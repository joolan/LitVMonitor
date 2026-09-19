package com.litv.monitor.controller;

import com.litv.monitor.dto.Result;
import com.litv.monitor.dto.UnlockUserDTO;
import com.litv.monitor.entity.UserSession;
import com.litv.monitor.service.AuditLogService;
import com.litv.monitor.service.SecuritySettingsService;
import com.litv.monitor.service.UserSessionService;
import com.litv.monitor.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/security")
@RequiredArgsConstructor
public class SecuritySettingsController {

    private final SecuritySettingsService securitySettingsService;
    private final UserSessionService userSessionService;
    private final UserService userService;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    @GetMapping("/settings")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Map<String, String>> getSettings() {
        return Result.success(securitySettingsService.getAllSettings());
    }

    @PutMapping("/settings")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> updateSettings(@RequestBody Map<String, String> updates) {
        securitySettingsService.updateSettings(updates);
        String username = getCurrentUsername();
        String requestBody = "";
        try { requestBody = objectMapper.writeValueAsString(updates); } catch (Exception ignored) {}
        auditLogService.record(null, username, "UPDATE", "SECURITY_SETTINGS",
                "", "", "更新安全设置", requestBody);
        return Result.success();
    }

    @GetMapping("/sessions")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<List<UserSession>> getAllActiveSessions() {
        // Return all active sessions grouped by user
        // Frontend will display per-user
        return Result.success(List.of());
    }

    @GetMapping("/sessions/user/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<List<UserSession>> getUserSessions(@PathVariable Long userId) {
        return Result.success(userSessionService.getActiveSessions(userId));
    }

    @GetMapping("/sessions/online-count")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Map<Long, Long>> getOnlineCounts() {
        // Returns map of userId -> active session count
        // We'll fetch all users and count sessions for each
        Map<Long, Long> counts = new HashMap<>();
        try {
            var users = userService.listUsers(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 1000));
            for (var user : users.getRecords()) {
                long count = userSessionService.countActiveSessions(user.getId());
                if (count > 0) {
                    counts.put(user.getId(), count);
                }
            }
        } catch (Exception e) {
            // ignore
        }
        return Result.success(counts);
    }

    @PostMapping("/sessions/kick")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> kickSession(@RequestBody Map<String, String> body) {
        String jti = body.get("jti");
        if (jti == null || jti.isEmpty()) {
            return Result.error(400, "缺少 jti 参数");
        }
        userSessionService.kickSession(jti);
        String username = getCurrentUsername();
        auditLogService.record(null, username, "KICK", "SESSION",
                jti, "", "踢出会话");
        return Result.success();
    }

    @PostMapping("/unlock")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> unlockUser(@RequestBody UnlockUserDTO dto) {
        if (dto.getUserId() == null) {
            return Result.error(400, "缺少用户ID");
        }
        var user = userService.findById(dto.getUserId());
        if (user == null) {
            return Result.error(404, "用户不存在");
        }
        if ("admin".equals(user.getUsername())) {
            // For admin, only reset lockouts (cannot be unlocked via UI, only restart)
            userSessionService.resetAllLockouts();
        } else {
            userSessionService.unlockUser(user.getUsername());
        }
        String username = getCurrentUsername();
        auditLogService.record(null, username, "UNLOCK", "USER",
                String.valueOf(dto.getUserId()), user.getUsername(), "解锁用户");
        return Result.success();
    }

    @GetMapping("/lockout/status")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Map<String, Object>> getLockoutStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("lockoutEnabled", securitySettingsService.isLockoutEnabled());
        status.put("maxAttempts", securitySettingsService.getLockoutMaxAttempts());
        status.put("durationMinutes", securitySettingsService.getLockoutDurationMinutes());
        return Result.success(status);
    }

    @GetMapping("/lockout/users")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<List<String>> getLockedUsers() {
        // Return list of currently locked usernames
        List<String> locked = new java.util.ArrayList<>();
        try {
            var users = userService.listUsers(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 1000));
            for (var user : users.getRecords()) {
                if (userSessionService.isLockedOut(user.getUsername())) {
                    locked.add(user.getUsername());
                }
            }
        } catch (Exception e) {
            // ignore
        }
        return Result.success(locked);
    }

    private String getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "system";
    }
}
