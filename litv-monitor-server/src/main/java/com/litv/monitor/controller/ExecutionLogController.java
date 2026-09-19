package com.litv.monitor.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.litv.monitor.dto.Result;
import com.litv.monitor.entity.ExecutionLog;
import com.litv.monitor.service.ExecutionLogService;
import com.litv.monitor.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/log")
@RequiredArgsConstructor
public class ExecutionLogController {

    private final ExecutionLogService executionLogService;
    private final AuditLogService auditLogService;

    @GetMapping("/list")
    public Result<Page<ExecutionLog>> listLogs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long monitorId,
            @RequestParam(required = false) Long groupId,
            @RequestParam(required = false) String domain,
            @RequestParam(required = false) String ipAddress,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String schemaCheckStatus,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {
        return Result.success(executionLogService.listLogs(new Page<>(page, size), monitorId, groupId, domain, ipAddress, status, schemaCheckStatus, startTime, endTime));
    }

    @GetMapping("/{id}")
    public Result<ExecutionLog> getLog(@PathVariable Long id) {
        ExecutionLog log = executionLogService.getLogById(id);
        return log != null ? Result.success(log) : Result.error(404, "日志不存在");
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public Result<Void> deleteLog(@PathVariable Long id) {
        executionLogService.deleteLog(id);
        String username = getCurrentUsername();
        auditLogService.record(null, username, "DELETE", "EXECUTION_LOG",
                String.valueOf(id), "", "删除执行日志", null);
        return Result.success();
    }

    @DeleteMapping("/cleanup")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public Result<Integer> cleanupOldLogs(@RequestParam(defaultValue = "30") int days) {
        int deleted = executionLogService.cleanupOldLogs(days);
        String username = getCurrentUsername();
        auditLogService.record(null, username, "CLEANUP", "EXECUTION_LOG",
                null, "", "清理旧日志(" + days + "天前), 共" + deleted + "条", null);
        return Result.success(deleted);
    }

    private String getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "system";
    }
}
