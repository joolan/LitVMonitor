package com.litv.monitor.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.litv.monitor.dto.AlertTemplateDTO;
import com.litv.monitor.dto.Result;
import com.litv.monitor.entity.AlertTemplate;
import com.litv.monitor.mapper.AlertTemplateMapper;
import com.litv.monitor.service.AuditLogService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/alert/template")
@RequiredArgsConstructor
public class AlertTemplateController {

    private final AlertTemplateMapper alertTemplateMapper;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    @GetMapping("/list")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public Result<List<AlertTemplate>> list() {
        return Result.success(alertTemplateMapper.selectList(
                new LambdaQueryWrapper<AlertTemplate>().last("LIMIT 1000")));
    }

    @GetMapping("/{id}")
    public Result<AlertTemplate> get(@PathVariable Long id) {
        AlertTemplate t = alertTemplateMapper.selectById(id);
        return t != null ? Result.success(t) : Result.error(404, "模板不存在");
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public Result<AlertTemplate> create(@Valid @RequestBody AlertTemplateDTO dto) {
        AlertTemplate t = new AlertTemplate();
        t.setName(dto.getName());
        t.setTriggerType(dto.getTriggerType());
        t.setContent(dto.getContent());
        t.setCooldownMinutes(dto.getCooldownMinutes() != null ? dto.getCooldownMinutes() : 30);
        t.setEnabled(dto.getEnabled() != null ? dto.getEnabled() : false);
        t.setRateLimitEnabled(dto.getRateLimitEnabled());
        t.setRateLimitCount(dto.getRateLimitCount());
        t.setRecoveryNotify(dto.getRecoveryNotify());
        t.setRecoveryConsecutiveCount(dto.getRecoveryConsecutiveCount());
        t.setFallbackChannelIds(dto.getFallbackChannelIds());
        t.setTemplateType("CUSTOM");
        t.setCreatedAt(LocalDateTime.now());
        alertTemplateMapper.insert(t);
        String username = getCurrentUsername();
        String requestBody = "";
        try { requestBody = objectMapper.writeValueAsString(dto); } catch (Exception ignored) {}
        auditLogService.record(null, username, "CREATE", "ALERT_TEMPLATE",
                String.valueOf(t.getId()), t.getName(), "创建告警模板", requestBody);
        return Result.success(t);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public Result<AlertTemplate> update(@PathVariable Long id, @Valid @RequestBody AlertTemplateDTO dto) {
        AlertTemplate t = alertTemplateMapper.selectById(id);
        if (t == null) return Result.error(404, "模板不存在");
        t.setName(dto.getName());
        t.setTriggerType(dto.getTriggerType());
        t.setContent(dto.getContent());
        t.setCooldownMinutes(dto.getCooldownMinutes());
        t.setEnabled(dto.getEnabled());
        t.setRateLimitEnabled(dto.getRateLimitEnabled());
        t.setRateLimitCount(dto.getRateLimitCount());
        t.setRecoveryNotify(dto.getRecoveryNotify());
        t.setRecoveryConsecutiveCount(dto.getRecoveryConsecutiveCount());
        t.setFallbackChannelIds(dto.getFallbackChannelIds());
        alertTemplateMapper.updateById(t);
        String username = getCurrentUsername();
        String requestBody = "";
        try { requestBody = objectMapper.writeValueAsString(dto); } catch (Exception ignored) {}
        auditLogService.record(null, username, "UPDATE", "ALERT_TEMPLATE",
                String.valueOf(id), t.getName(), "更新告警模板", requestBody);
        return Result.success(t);
    }

    @PutMapping("/{id}/enable")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public Result<AlertTemplate> enable(@PathVariable Long id) {
        AlertTemplate t = alertTemplateMapper.selectById(id);
        if (t == null) return Result.error(404, "模板不存在");

        // Disable all other templates with the same trigger type
        alertTemplateMapper.update(null,
                new LambdaUpdateWrapper<AlertTemplate>()
                        .eq(AlertTemplate::getTriggerType, t.getTriggerType())
                        .ne(AlertTemplate::getId, id)
                        .set(AlertTemplate::getEnabled, false));

        t.setEnabled(true);
        alertTemplateMapper.updateById(t);
        String username = getCurrentUsername();
        auditLogService.record(null, username, "ENABLE", "ALERT_TEMPLATE",
                String.valueOf(id), t.getName(), "启用告警模板", null);
        return Result.success(t);
    }

    @PutMapping("/{id}/disable")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public Result<AlertTemplate> disable(@PathVariable Long id) {
        AlertTemplate t = alertTemplateMapper.selectById(id);
        if (t == null) return Result.error(404, "模板不存在");
        t.setEnabled(false);
        alertTemplateMapper.updateById(t);
        String username = getCurrentUsername();
        auditLogService.record(null, username, "DISABLE", "ALERT_TEMPLATE",
                String.valueOf(id), t.getName(), "禁用告警模板", null);
        return Result.success(t);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public Result<Void> delete(@PathVariable Long id) {
        AlertTemplate t = alertTemplateMapper.selectById(id);
        if (t == null) return Result.error(404, "模板不存在");
        if ("SYSTEM".equals(t.getTemplateType())) {
            return Result.error(400, "系统预置模板不能删除");
        }
        alertTemplateMapper.deleteById(id);
        String username = getCurrentUsername();
        auditLogService.record(null, username, "DELETE", "ALERT_TEMPLATE",
                String.valueOf(id), "", "删除告警模板", null);
        return Result.success();
    }

    private String getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "system";
    }
}
