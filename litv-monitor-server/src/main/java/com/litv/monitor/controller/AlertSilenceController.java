package com.litv.monitor.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.litv.monitor.dto.Result;
import com.litv.monitor.entity.AlertSilence;
import com.litv.monitor.service.AlertSilenceService;
import com.litv.monitor.service.AuditLogService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/alert/silence")
@RequiredArgsConstructor
public class AlertSilenceController {

    private final AlertSilenceService alertSilenceService;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    @GetMapping("/list")
    public Result<Page<AlertSilence>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.success(alertSilenceService.listSilences(new Page<>(page, size)));
    }

    @GetMapping("/{id}")
    public Result<AlertSilence> get(@PathVariable Long id) {
        AlertSilence silence = alertSilenceService.getSilenceById(id);
        return silence != null ? Result.success(silence) : Result.error(404, "静默规则不存在");
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public Result<AlertSilence> create(@RequestBody AlertSilence dto) {
        AlertSilence silence = alertSilenceService.createSilence(dto);
        String username = getCurrentUsername();
        String requestBody = "";
        try { requestBody = objectMapper.writeValueAsString(dto); } catch (Exception ignored) {}
        auditLogService.record(null, username, "CREATE", "ALERT_SILENCE",
                String.valueOf(silence.getId()), silence.getName(), "创建静默规则", requestBody);
        return Result.success(silence);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public Result<AlertSilence> update(@PathVariable Long id, @RequestBody AlertSilence dto) {
        AlertSilence silence = alertSilenceService.updateSilence(id, dto);
        if (silence != null) {
            String username = getCurrentUsername();
            String requestBody = "";
            try { requestBody = objectMapper.writeValueAsString(dto); } catch (Exception ignored) {}
            auditLogService.record(null, username, "UPDATE", "ALERT_SILENCE",
                    String.valueOf(id), silence.getName(), "更新静默规则", requestBody);
        }
        return silence != null ? Result.success(silence) : Result.error(404, "静默规则不存在");
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public Result<Void> delete(@PathVariable Long id) {
        alertSilenceService.deleteSilence(id);
        String username = getCurrentUsername();
        auditLogService.record(null, username, "DELETE", "ALERT_SILENCE",
                String.valueOf(id), "", "删除静默规则", null);
        return Result.success();
    }

    @PutMapping("/{id}/enable")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public Result<Void> enable(@PathVariable Long id) {
        alertSilenceService.toggleSilence(id, true);
        String username = getCurrentUsername();
        auditLogService.record(null, username, "ENABLE", "ALERT_SILENCE",
                String.valueOf(id), "", "启用静默规则", null);
        return Result.success();
    }

    @PutMapping("/{id}/disable")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public Result<Void> disable(@PathVariable Long id) {
        alertSilenceService.toggleSilence(id, false);
        String username = getCurrentUsername();
        auditLogService.record(null, username, "DISABLE", "ALERT_SILENCE",
                String.valueOf(id), "", "禁用静默规则", null);
        return Result.success();
    }

    private String getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "system";
    }
}
