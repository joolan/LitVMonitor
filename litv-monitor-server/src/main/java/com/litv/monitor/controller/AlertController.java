package com.litv.monitor.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.litv.monitor.dto.AlertConfigDTO;
import com.litv.monitor.dto.Result;
import com.litv.monitor.entity.AlertConfig;
import com.litv.monitor.entity.AlertLog;
import com.litv.monitor.entity.ExecutionLog;
import com.litv.monitor.service.AlertService;
import com.litv.monitor.service.AuditLogService;
import com.litv.monitor.mapper.AlertConfigMapper;
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
@RequestMapping("/alert")
@RequiredArgsConstructor
public class AlertController {

    private final AlertConfigMapper alertConfigMapper;
    private final AlertService alertService;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    @GetMapping("/config/list")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public Result<List<AlertConfig>> listConfigs() {
        List<AlertConfig> configs = alertConfigMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AlertConfig>().last("LIMIT 1000"));
        configs.forEach(c -> c.setConfig(com.litv.monitor.util.SecretMasker.mask(c.getConfig())));
        return Result.success(configs);
    }

    @PostMapping("/config")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public Result<AlertConfig> createConfig(@Valid @RequestBody AlertConfigDTO dto) {
        AlertConfig config = new AlertConfig();
        config.setName(dto.getName());
        config.setType(dto.getType());
        config.setConfig(dto.getConfig());
        config.setAlertTemplate(dto.getAlertTemplate());
        config.setEnabled(dto.getEnabled() != null ? dto.getEnabled() : true);
        config.setCooldownMinutes(dto.getCooldownMinutes() != null ? dto.getCooldownMinutes() : 30);
        alertConfigMapper.insert(config);
        String username = getCurrentUsername();
        String requestBody = "";
        try { requestBody = objectMapper.writeValueAsString(dto); } catch (Exception ignored) {}
        auditLogService.record(null, username, "CREATE", "ALERT_CONFIG",
                String.valueOf(config.getId()), config.getName(), "创建告警配置", requestBody);
        return Result.success(config);
    }

    @PutMapping("/config/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public Result<AlertConfig> updateConfig(@PathVariable Long id, @Valid @RequestBody AlertConfigDTO dto) {
        AlertConfig config = alertConfigMapper.selectById(id);
        if (config == null) {
            return Result.error(404, "告警配置不存在");
        }
        config.setName(dto.getName());
        config.setType(dto.getType());
        // 前端回传的哨兵值（******）表示未修改，用库中旧值回填
        config.setConfig(com.litv.monitor.util.SecretMasker.restore(dto.getConfig(), config.getConfig()));
        config.setAlertTemplate(dto.getAlertTemplate());
        config.setEnabled(dto.getEnabled());
        config.setCooldownMinutes(dto.getCooldownMinutes());
        alertConfigMapper.updateById(config);
        String username = getCurrentUsername();
        String requestBody = "";
        try { requestBody = objectMapper.writeValueAsString(dto); } catch (Exception ignored) {}
        auditLogService.record(null, username, "UPDATE", "ALERT_CONFIG",
                String.valueOf(id), config.getName(), "更新告警配置", requestBody);
        return Result.success(config);
    }

    @DeleteMapping("/config/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public Result<Void> deleteConfig(@PathVariable Long id) {
        alertConfigMapper.deleteById(id);
        String username = getCurrentUsername();
        auditLogService.record(null, username, "DELETE", "ALERT_CONFIG",
                String.valueOf(id), "", "删除告警配置", null);
        return Result.success();
    }

    @PostMapping("/config/{id}/test")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public Result<String> testConfig(@PathVariable Long id) {
        AlertConfig config = alertConfigMapper.selectById(id);
        if (config == null) {
            return Result.error(404, "告警配置不存在");
        }

        ExecutionLog testLog = new ExecutionLog();
        testLog.setMonitorId(0L);
        testLog.setMonitorName("测试监控");
        testLog.setStatus("FAIL");
        testLog.setStatusCode(500);
        testLog.setErrorMessage("这是一条测试告警消息 [LitVMonitor]");
        testLog.setExecutedAt(LocalDateTime.now());

        try {
            alertService.sendAlertSync(config, testLog);
            String username = getCurrentUsername();
            auditLogService.record(null, username, "TEST", "ALERT_CONFIG",
                    String.valueOf(id), config.getName(), "测试告警配置", null);
            return Result.success("测试告警发送成功");
        } catch (Exception e) {
            return Result.error("测试告警发送失败: " + e.getMessage());
        }
    }

    @GetMapping("/log/list")
    public Result<Page<AlertLog>> listLogs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long monitorId,
            @RequestParam(required = false) Long groupId,
            @RequestParam(required = false) String triggerType,
            @RequestParam(required = false) Long alertConfigId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String executionId,
            @RequestParam(required = false) String alertCategory,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {
        return Result.success(alertService.listAlertLogs(new Page<>(page, size), monitorId, groupId, triggerType, alertConfigId, status, executionId, alertCategory, startTime, endTime));
    }

    @GetMapping("/log/{id}")
    public Result<AlertLog> getLog(@PathVariable Long id) {
        AlertLog log = alertService.getAlertLogById(id);
        return log != null ? Result.success(log) : Result.error(404, "告警记录不存在");
    }

    private String getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "system";
    }
}
