package com.litv.monitor.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.litv.monitor.dto.MonitorDTO;
import com.litv.monitor.dto.MonitorVO;
import com.litv.monitor.dto.Result;
import com.litv.monitor.entity.ExecutionLog;
import com.litv.monitor.entity.Monitor;
import com.litv.monitor.service.AuditLogService;
import com.litv.monitor.service.ExecutionService;
import com.litv.monitor.service.MonitorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/monitor")
@RequiredArgsConstructor
public class MonitorController {

    private final MonitorService monitorService;
    private final ExecutionService executionService;
    private final AuditLogService auditLogService;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @GetMapping("/list")
    public Result<Page<MonitorVO>> listMonitors(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) Long id) {
        Page<MonitorVO> result = monitorService.listMonitors(new Page<>(page, size), keyword, enabled, id);
        if (!canSeeSecrets()) {
            result.getRecords().forEach(this::maskSecrets);
        }
        return Result.success(result);
    }

    @GetMapping("/{id}")
    public Result<Monitor> getMonitor(@PathVariable Long id) {
        Monitor monitor = monitorService.getMonitorById(id);
        if (monitor == null) return Result.error(404, "监控项不存在");
        if (!canSeeSecrets()) maskSecrets(monitor);
        return Result.success(monitor);
    }

    /** ADMIN/OPERATOR 可查看请求头、请求体、签名私钥等敏感配置；VIEWER 仅看基础信息。 */
    private boolean canSeeSecrets() {
        org.springframework.security.core.Authentication auth =
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        return auth.getAuthorities().stream().anyMatch(a ->
                "ROLE_ADMIN".equals(a.getAuthority()) || "ROLE_OPERATOR".equals(a.getAuthority()));
    }

    private void maskSecrets(Monitor m) {
        if (m == null) return;
        m.setHeaders(null);
        m.setBody(null);
        m.setSignConfig(null);
        m.setPreRequestScript(null);
        m.setVariableExtractConfig(null);
        m.setExpectedSchemaJson(null);
        m.setConfig(null);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public Result<Monitor> createMonitor(@Valid @RequestBody MonitorDTO dto) {
        Monitor monitor = monitorService.createMonitor(dto);
        String username = getCurrentUsername();
        Long userId = getCurrentUserId();
        String requestBody = "";
        try { requestBody = objectMapper.writeValueAsString(dto); } catch (Exception ignored) {}
        auditLogService.record(userId, username, "CREATE", "MONITOR",
                String.valueOf(monitor.getId()), monitor.getName(), "创建监控项", requestBody);
        return Result.success(monitor);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public Result<Monitor> updateMonitor(@PathVariable Long id, @Valid @RequestBody MonitorDTO dto) {
        Monitor monitor = monitorService.updateMonitor(id, dto);
        if (monitor != null) {
            String username = getCurrentUsername();
            Long userId = getCurrentUserId();
            String requestBody = "";
            try { requestBody = objectMapper.writeValueAsString(dto); } catch (Exception ignored) {}
            auditLogService.record(userId, username, "UPDATE", "MONITOR",
                    String.valueOf(id), monitor.getName(), "更新监控项", requestBody);
        }
        return monitor != null ? Result.success(monitor) : Result.error(404, "监控项不存在");
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public Result<Void> deleteMonitor(@PathVariable Long id) {
        Monitor monitor = monitorService.getMonitorById(id);
        String name = monitor != null ? monitor.getName() : "";
        monitorService.deleteMonitor(id);
        String username = getCurrentUsername();
        Long userId = getCurrentUserId();
        auditLogService.record(userId, username, "DELETE", "MONITOR",
                String.valueOf(id), name, "删除监控项", null);
        return Result.success();
    }

    @PostMapping("/{id}/test")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public Result<ExecutionLog> testMonitor(@PathVariable Long id) {
        Monitor monitor = monitorService.getMonitorById(id);
        if (monitor == null) {
            return Result.error(404, "监控项不存在");
        }
        ExecutionLog log = executionService.executeMonitor(monitor, null);
        String username = getCurrentUsername();
        auditLogService.record(null, username, "TEST", "MONITOR",
                String.valueOf(id), monitor.getName(), "手动测试监控项", null);
        return Result.success(log);
    }

    @PostMapping("/{id}/copy")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public Result<Monitor> copyMonitor(@PathVariable Long id) {
        Monitor copy = monitorService.copyMonitor(id);
        if (copy != null) {
            String username = getCurrentUsername();
            Long userId = getCurrentUserId();
            auditLogService.record(userId, username, "CREATE", "MONITOR",
                    String.valueOf(copy.getId()), copy.getName(), "复制监控项", null);
        }
        return copy != null ? Result.success(copy) : Result.error(404, "监控项不存在");
    }

    @GetMapping("/enabled")
    public Result<List<Monitor>> getEnabledMonitors() {
        return Result.success(monitorService.getEnabledMonitors());
    }

    @GetMapping("/{id}/groups")
    public Result<List<Map<String, Object>>> getGroupsForMonitor(@PathVariable Long id) {
        return Result.success(monitorService.getGroupsForMonitor(id));
    }

    @PostMapping("/{id}/groups")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public Result<Void> addMonitorToGroup(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Long groupId = Long.valueOf(body.get("groupId").toString());
        Integer sortOrder = body.get("sortOrder") != null ? Integer.valueOf(body.get("sortOrder").toString()) : 0;
        Boolean continueOnFail = body.get("continueOnFail") != null ? (Boolean) body.get("continueOnFail") : true;
        monitorService.addMonitorToGroup(id, groupId, sortOrder, continueOnFail);
        String username = getCurrentUsername();
        Long userId = getCurrentUserId();
        String requestBody = "";
        try { requestBody = objectMapper.writeValueAsString(body); } catch (Exception ignored) {}
        auditLogService.record(userId, username, "ADD_TO_GROUP", "MONITOR",
                String.valueOf(id), "", "将监控项添加到分组", requestBody);
        return Result.success();
    }

    @PutMapping("/{id}/groups/{groupId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public Result<Void> updateMonitorInGroup(@PathVariable Long id, @PathVariable Long groupId, @RequestBody Map<String, Object> body) {
        Integer sortOrder = body.get("sortOrder") != null ? Integer.valueOf(body.get("sortOrder").toString()) : null;
        Boolean continueOnFail = body.get("continueOnFail") != null ? (Boolean) body.get("continueOnFail") : null;
        monitorService.updateMonitorInGroup(id, groupId, sortOrder, continueOnFail);
        String username = getCurrentUsername();
        Long userId = getCurrentUserId();
        String requestBody = "";
        try { requestBody = objectMapper.writeValueAsString(body); } catch (Exception ignored) {}
        auditLogService.record(userId, username, "UPDATE_GROUP_CONFIG", "MONITOR",
                String.valueOf(id), "", "更新监控项分组配置", requestBody);
        return Result.success();
    }

    @DeleteMapping("/{id}/groups/{groupId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public Result<Void> removeMonitorFromGroup(@PathVariable Long id, @PathVariable Long groupId) {
        monitorService.removeMonitorFromGroup(id, groupId);
        String username = getCurrentUsername();
        Long userId = getCurrentUserId();
        auditLogService.record(userId, username, "REMOVE_FROM_GROUP", "MONITOR",
                String.valueOf(id), "", "从分组中移除监控项", null);
        return Result.success();
    }

    @PutMapping("/batch/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public Result<Void> batchUpdateStatus(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Long> ids = ((List<Number>) body.get("ids")).stream().map(Number::longValue).toList();
        Boolean enabled = (Boolean) body.get("enabled");
        monitorService.batchUpdateStatus(ids, enabled);
        String username = getCurrentUsername();
        Long userId = getCurrentUserId();
        String requestBody = "";
        try { requestBody = objectMapper.writeValueAsString(body); } catch (Exception ignored) {}
        auditLogService.record(userId, username, "BATCH_UPDATE_STATUS", "MONITOR",
                ids.toString(), "", "批量更新监控项状态(enabled=" + enabled + ")", requestBody);
        return Result.success();
    }

    private String getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "system";
    }

    private Long getCurrentUserId() {
        return null;
    }
}
