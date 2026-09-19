package com.litv.monitor.controller;

import com.litv.monitor.dto.AlertChannelDTO;
import com.litv.monitor.dto.Result;
import com.litv.monitor.entity.AlertChannel;
import com.litv.monitor.entity.ExecutionLog;
import com.litv.monitor.mapper.AlertChannelMapper;
import com.litv.monitor.service.AlertService;
import com.litv.monitor.service.AuditLogService;
import com.litv.monitor.util.SecretMasker;
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
@RequestMapping("/alert/channel")
@RequiredArgsConstructor
public class AlertChannelController {

    private final AlertChannelMapper alertChannelMapper;
    private final AlertService alertService;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    @GetMapping("/list")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public Result<List<AlertChannel>> list() {
        List<AlertChannel> channels = alertChannelMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AlertChannel>().last("LIMIT 1000"));
        channels.forEach(c -> c.setConfig(SecretMasker.mask(c.getConfig())));
        return Result.success(channels);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public Result<AlertChannel> get(@PathVariable Long id) {
        AlertChannel c = alertChannelMapper.selectById(id);
        if (c == null) return Result.error(404, "渠道不存在");
        c.setConfig(SecretMasker.mask(c.getConfig()));
        return Result.success(c);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public Result<AlertChannel> create(@Valid @RequestBody AlertChannelDTO dto) {
        AlertChannel c = new AlertChannel();
        c.setName(dto.getName());
        c.setType(dto.getType());
        c.setConfig(dto.getConfig());
        c.setEnabled(dto.getEnabled() != null ? dto.getEnabled() : true);
        c.setCreatedAt(LocalDateTime.now());
        alertChannelMapper.insert(c);
        String username = getCurrentUsername();
        String requestBody = "";
        try { requestBody = objectMapper.writeValueAsString(dto); } catch (Exception ignored) {}
        auditLogService.record(null, username, "CREATE", "ALERT_CHANNEL",
                String.valueOf(c.getId()), c.getName(), "创建告警渠道", requestBody);
        return Result.success(c);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public Result<AlertChannel> update(@PathVariable Long id, @Valid @RequestBody AlertChannelDTO dto) {
        AlertChannel c = alertChannelMapper.selectById(id);
        if (c == null) return Result.error(404, "渠道不存在");
        c.setName(dto.getName());
        c.setType(dto.getType());
        // 前端回传的哨兵值（******）表示未修改，用库中旧值回填
        c.setConfig(SecretMasker.restore(dto.getConfig(), c.getConfig()));
        c.setEnabled(dto.getEnabled());
        alertChannelMapper.updateById(c);
        String username = getCurrentUsername();
        String requestBody = "";
        try { requestBody = objectMapper.writeValueAsString(dto); } catch (Exception ignored) {}
        auditLogService.record(null, username, "UPDATE", "ALERT_CHANNEL",
                String.valueOf(id), c.getName(), "更新告警渠道", requestBody);
        return Result.success(c);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public Result<Void> delete(@PathVariable Long id) {
        alertChannelMapper.deleteById(id);
        String username = getCurrentUsername();
        auditLogService.record(null, username, "DELETE", "ALERT_CHANNEL",
                String.valueOf(id), "", "删除告警渠道", null);
        return Result.success();
    }

    @PostMapping("/{id}/test")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public Result<String> test(@PathVariable Long id) {
        AlertChannel channel = alertChannelMapper.selectById(id);
        if (channel == null) return Result.error(404, "渠道不存在");

        ExecutionLog testLog = new ExecutionLog();
        testLog.setMonitorId(0L);
        testLog.setMonitorName("测试监控");
        testLog.setStatus("FAIL");
        testLog.setStatusCode(500);
        testLog.setErrorMessage("这是一条测试告警消息 [LitVMonitor]");
        testLog.setExecutedAt(LocalDateTime.now());

        try {
            alertService.sendAlertSync(channel, testLog);
            String username = getCurrentUsername();
            auditLogService.record(null, username, "TEST", "ALERT_CHANNEL",
                    String.valueOf(id), channel.getName(), "测试告警渠道", null);
            return Result.success("测试告警发送成功");
        } catch (Exception e) {
            return Result.error("测试告警发送失败: " + e.getMessage());
        }
    }

    private String getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "system";
    }
}
