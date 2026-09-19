package com.litv.monitor.controller;

import com.litv.monitor.dto.Result;
import com.litv.monitor.service.AuditLogService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Stream;

@Slf4j
@RestController
@RequestMapping("/backup")
@RequiredArgsConstructor
public class BackupController {

    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @Value("${spring.datasource.url:jdbc:sqlite:litv-monitor.db}")
    private String datasourceUrl;

    private static final java.util.concurrent.ConcurrentHashMap<String, java.util.Map<String, Object>> downloadTokens = new java.util.concurrent.ConcurrentHashMap<>();

    // Clean up expired tokens periodically (max 500 tokens, expire after 10 min)
    private static final java.util.concurrent.ScheduledExecutorService tokenCleaner = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "backup-token-cleaner");
        t.setDaemon(true);
        return t;
    });
    static {
        tokenCleaner.scheduleAtFixedRate(() -> {
            long now = System.currentTimeMillis();
            downloadTokens.entrySet().removeIf(e -> {
                Long expiresAt = (Long) e.getValue().get("expiresAt");
                return expiresAt != null && now > expiresAt;
            });
        }, 5, 5, java.util.concurrent.TimeUnit.MINUTES);
    }

    @PostMapping("/download-token")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Map<String, String>> createDownloadToken(@RequestBody Map<String, String> body) {
        String path = body.get("path");
        if (path == null || path.isEmpty()) return Result.error("请指定备份文件路径");
        try {
            Path backupFile = validateBackupPath(path);
            if (!Files.exists(backupFile)) return Result.error(404, "备份文件不存在");
            String token = UUID.randomUUID().toString();
            java.util.Map<String, Object> tokenInfo = new java.util.LinkedHashMap<>();
            tokenInfo.put("path", path);
            tokenInfo.put("expiresAt", System.currentTimeMillis() + 5 * 60 * 1000);
            downloadTokens.put(token, tokenInfo);
            return Result.success(Map.of("token", token));
        } catch (SecurityException e) {
            return Result.error(403, "非法备份路径");
        } catch (Exception e) {
            return Result.error("生成下载令牌失败: " + e.getMessage());
        }
    }

    @PostMapping("/create")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Map<String, String>> createBackup() {
        try {
            String dbPath = extractDbPath();
            Path dbFile = Paths.get(dbPath);
            if (!Files.exists(dbFile)) {
                return Result.error("数据库文件不存在: " + dbPath);
            }

            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String backupDir = dbFile.getParent().resolve("backups").toString();
            Files.createDirectories(Paths.get(backupDir));
            String backupPath = Paths.get(backupDir, "litv-monitor_" + timestamp + ".db").toString();

            // 将 WAL 内容合并回主库，确保备份文件完整
            try {
                jdbcTemplate.execute("PRAGMA wal_checkpoint(TRUNCATE)");
            } catch (Exception e) {
                log.warn("WAL checkpoint before backup failed: {}", e.getMessage());
            }

            Files.copy(dbFile, Paths.get(backupPath), StandardCopyOption.REPLACE_EXISTING);

            Map<String, String> result = new LinkedHashMap<>();
            result.put("path", backupPath);
            result.put("size", String.valueOf(Files.size(Paths.get(backupPath))));
            result.put("timestamp", timestamp);
            log.info("Database backup created: {}", backupPath);
            String username = getCurrentUsername();
            auditLogService.record(null, username, "CREATE", "BACKUP",
                    null, result.get("path"), "创建数据库备份", null);
            return Result.success(result);
        } catch (Exception e) {
            log.error("Failed to create backup", e);
            return Result.error("备份失败: " + e.getMessage());
        }
    }

    @GetMapping("/list")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<List<Map<String, Object>>> listBackups() {
        try {
            String dbPath = extractDbPath();
            Path backupDir = Paths.get(dbPath).getParent().resolve("backups");
            List<Map<String, Object>> backups = new ArrayList<>();
            if (Files.exists(backupDir)) {
                try (Stream<Path> files = Files.list(backupDir)) {
                    files.filter(p -> p.toString().endsWith(".db"))
                         .sorted((a, b) -> b.getFileName().toString().compareTo(a.getFileName().toString()))
                         .forEach(p -> {
                             try {
                                 Map<String, Object> info = new LinkedHashMap<>();
                                 info.put("name", p.getFileName().toString());
                                 info.put("path", p.toString());
                                 info.put("size", Files.size(p));
                                 info.put("lastModified", Files.getLastModifiedTime(p).toMillis());
                                 backups.add(info);
                             } catch (IOException ignored) {}
                         });
                }
            }
            return Result.success(backups);
        } catch (Exception e) {
            return Result.error("获取备份列表失败: " + e.getMessage());
        }
    }

    @PostMapping("/restore")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> restoreBackup(@RequestBody Map<String, String> body) {
        try {
            String backupPath = body.get("path");
            if (backupPath == null || backupPath.isEmpty()) {
                return Result.error("请指定备份文件路径");
            }
            Path backupFile = validateBackupPath(backupPath);
            if (!Files.exists(backupFile)) {
                return Result.error("备份文件不存在");
            }

            String dbPath = extractDbPath();
            Path dbFile = Paths.get(dbPath);

            Path tempFile = dbFile.getParent().resolve("litv-monitor_restore_tmp.db");
            Files.copy(backupFile, tempFile, StandardCopyOption.REPLACE_EXISTING);
            Files.move(tempFile, dbFile, StandardCopyOption.REPLACE_EXISTING);

            log.info("Database restored from: {}", backupPath);
            String username = getCurrentUsername();
            auditLogService.record(null, username, "RESTORE", "BACKUP",
                    null, backupPath, "从备份恢复数据库", null);
            return Result.success();
        } catch (SecurityException e) {
            log.warn("Backup path validation failed: {}", e.getMessage());
            return Result.error("非法备份路径");
        } catch (Exception e) {
            log.error("Failed to restore backup", e);
            return Result.error("恢复失败: " + e.getMessage() + "。请重启应用使备份生效。");
        }
    }

    @GetMapping("/download")
    @PreAuthorize("hasRole('ADMIN')")
    public void downloadBackup(@RequestParam String path,
                               @RequestParam(required = false) String token,
                               @RequestHeader(value = "X-Download-Token", required = false) String headerToken,
                               jakarta.servlet.http.HttpServletResponse response) {
        try {
            // 优先使用请求头中的令牌（避免令牌出现在 URL / 访问日志中）
            if ((token == null || token.isEmpty()) && headerToken != null && !headerToken.isEmpty()) {
                token = headerToken;
            }
            // Validate path - either via token or via auth
            if (token != null && !token.isEmpty()) {
                java.util.Map<String, Object> tokenInfo = downloadTokens.remove(token);
                if (tokenInfo == null) {
                    writeError(response, 403, "下载令牌无效或已过期");
                    return;
                }
                if (System.currentTimeMillis() > (Long) tokenInfo.get("expiresAt")) {
                    writeError(response, 403, "下载令牌已过期");
                    return;
                }
                path = (String) tokenInfo.get("path");
            } else {
                Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                if (auth == null || !auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) {
                    writeError(response, 403, "需要管理员权限");
                    return;
                }
            }

            Path backupFile = validateBackupPath(path);
            if (!Files.exists(backupFile)) {
                writeError(response, 404, "备份文件不存在");
                return;
            }
            response.setContentType("application/octet-stream");
            response.setHeader("Content-Disposition", "attachment; filename=\"" + backupFile.getFileName().toString() + "\"");
            response.setContentLengthLong(Files.size(backupFile));
            java.nio.file.Files.copy(backupFile, response.getOutputStream());
            response.getOutputStream().flush();
        } catch (SecurityException e) {
            log.warn("Backup path validation failed: {}", e.getMessage());
            writeError(response, 403, "非法备份路径");
        } catch (Exception e) {
            log.error("Failed to download backup", e);
            writeError(response, 500, "下载失败");
        }
    }

    /** 直接写 JSON 错误响应，避免 sendError 触发 /error 转发导致状态被改写。 */
    private void writeError(jakarta.servlet.http.HttpServletResponse response, int status, String message) {
        try {
            if (response.isCommitted()) return;
            response.setStatus(status);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":" + status + ",\"message\":\"" + message + "\",\"data\":null}");
        } catch (Exception ignored) {
        }
    }

    @PostMapping("/upload-restore")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> uploadAndRestore(@RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
        try {
            if (file.isEmpty()) {
                return Result.error("上传文件为空");
            }
            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null || !originalFilename.endsWith(".db")) {
                return Result.error("仅支持 .db 备份文件");
            }

            // Sanitize filename: remove path separators and directory traversal
            String safeFilename = originalFilename.replaceAll("[^a-zA-Z0-9._\\-]", "_");
            if (safeFilename.contains("..") || safeFilename.startsWith(".")) {
                safeFilename = "upload_" + safeFilename;
            }

            String dbPath = extractDbPath();
            Path dbFile = Paths.get(dbPath);

            // Save uploaded file as backup first
            String backupDir = dbFile.getParent().resolve("backups").toString();
            Files.createDirectories(Paths.get(backupDir));
            String backupPath = Paths.get(backupDir, safeFilename).toString();
            file.transferTo(Paths.get(backupPath).toFile());

            // Then restore: copy to current db
            Path tempFile = dbFile.getParent().resolve("litv-monitor_restore_tmp.db");
            Files.copy(Paths.get(backupPath), tempFile, StandardCopyOption.REPLACE_EXISTING);
            Files.move(tempFile, dbFile, StandardCopyOption.REPLACE_EXISTING);

            log.info("Database restored from uploaded file: {}", safeFilename);
            String username = getCurrentUsername();
            auditLogService.record(null, username, "RESTORE", "BACKUP",
                    null, safeFilename, "上传备份文件并恢复数据库", null);
            return Result.success();
        } catch (Exception e) {
            log.error("Failed to upload and restore", e);
            return Result.error("恢复失败: " + e.getMessage() + "。请重启应用使备份生效。");
        }
    }

    @DeleteMapping("/delete")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> deleteBackup(@RequestParam String path) {
        try {
            Path backupFile = validateBackupPath(path);
            if (Files.exists(backupFile)) {
                Files.delete(backupFile);
                String username = getCurrentUsername();
                auditLogService.record(null, username, "DELETE", "BACKUP",
                        null, path, "删除备份文件", null);
                return Result.success();
            }
            return Result.error("备份文件不存在");
        } catch (SecurityException e) {
            log.warn("Backup path validation failed: {}", e.getMessage());
            return Result.error("非法备份路径");
        } catch (Exception e) {
            return Result.error("删除失败: " + e.getMessage());
        }
    }

    private Path validateBackupPath(String path) throws IOException {
        Path backupDir = Paths.get(extractDbPath()).getParent().resolve("backups").toAbsolutePath().normalize();
        Path target = Paths.get(path).toAbsolutePath().normalize();
        // 1) 词法校验：normalize() 会消解 ".."，必须位于 backups 目录内
        if (!target.startsWith(backupDir)) {
            throw new SecurityException("非法备份路径: " + path);
        }
        // 2) 后缀校验：仅允许 .db 备份文件
        String fileName = target.getFileName() != null ? target.getFileName().toString().toLowerCase() : "";
        if (!fileName.endsWith(".db")) {
            throw new SecurityException("仅允许操作 .db 备份文件: " + path);
        }
        // 3) 拒绝符号链接（Files.copy 会跟随软链，可能读到 .jwt-secret 等目录外文件）
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(target)) {
            throw new SecurityException("非法备份路径（符号链接）: " + path);
        }
        // 4) 真实路径校验：解析软链后再确认仍在 backups 内（防止父目录为软链的逃逸）
        if (Files.exists(target)) {
            Path realTarget = target.toRealPath();
            Path realBackupDir = backupDir.toRealPath();
            if (!realTarget.startsWith(realBackupDir)) {
                throw new SecurityException("非法备份路径（软链逃逸）: " + path);
            }
        }
        return target;
    }

    private String extractDbPath() {
        String url = datasourceUrl.replace("jdbc:sqlite:", "");
        // 去掉连接参数（如 ?busy_timeout=5000&journal_mode=WAL），否则会被当作文件名的一部分
        int queryIdx = url.indexOf('?');
        if (queryIdx >= 0) {
            url = url.substring(0, queryIdx);
        }
        if (url.startsWith("file:")) {
            url = url.substring(5);
        }
        java.nio.file.Path path = java.nio.file.Paths.get(url);
        if (!path.isAbsolute()) {
            path = java.nio.file.Paths.get(System.getProperty("user.dir"), url);
        }
        return path.toAbsolutePath().toString();
    }

    private String getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "system";
    }
}
