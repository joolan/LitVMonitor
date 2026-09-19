package com.litv.monitor.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.litv.monitor.dto.Result;
import com.litv.monitor.entity.AuditLog;
import com.litv.monitor.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/audit")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogService auditLogService;

    @GetMapping("/log")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public Result<Page<AuditLog>> listLogs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String action) {
        return Result.success(auditLogService.listLogs(new Page<>(page, size), username, action));
    }
}
