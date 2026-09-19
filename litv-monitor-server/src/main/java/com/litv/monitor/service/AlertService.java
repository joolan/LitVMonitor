package com.litv.monitor.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.litv.monitor.entity.AlertChannel;
import com.litv.monitor.entity.AlertConfig;
import com.litv.monitor.entity.AlertLog;
import com.litv.monitor.entity.AlertTemplate;
import com.litv.monitor.entity.ExecutionLog;
import com.litv.monitor.entity.Monitor;
import com.litv.monitor.enums.AlertSendStatus;
import com.litv.monitor.enums.ChannelType;
import com.litv.monitor.mapper.AlertChannelMapper;
import com.litv.monitor.mapper.AlertConfigMapper;
import com.litv.monitor.mapper.AlertLogMapper;
import com.litv.monitor.mapper.AlertTemplateMapper;
import com.litv.monitor.mapper.MonitorMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlertService {

    private final AlertConfigMapper alertConfigMapper;
    private final AlertChannelMapper alertChannelMapper;
    private final AlertTemplateMapper alertTemplateMapper;
    private final AlertLogMapper alertLogMapper;
    private final MonitorMapper monitorMapper;
    private final ObjectMapper objectMapper;
    private final AlertSilenceService alertSilenceService;
    private final AlertRateLimitService alertRateLimitService;
    private final SsrfProtectionService ssrfGuard;

    private final com.github.benmanes.caffeine.cache.Cache<String, java.time.LocalDateTime> lastSendTimes =
            com.github.benmanes.caffeine.cache.Caffeine.newBuilder()
                    .expireAfterWrite(2, java.util.concurrent.TimeUnit.HOURS)
                    .maximumSize(1000)
                    .build();

    private static final int DEFAULT_COOLDOWN_MINUTES = 30;

    private static final String DEFAULT_TEMPLATE = "监控告警\n监控名称: {{monitorName}}\n监控ID: {{monitorId}}\n状态: {{status}}\n状态码: {{statusCode}}\n响应时间: {{responseTime}}\n错误信息: {{errorMessage}}\n执行时间: {{executedAt}}";

    // ========== New System: AlertTemplate + AlertChannel ==========

    public void doSendAlert(AlertChannel channel, ExecutionLog executionLog, String triggerType) {
        String cooldownKey = "ch:" + channel.getId() + ":" + triggerType;

        AlertTemplate template = resolveTemplate(triggerType);
        int cooldownMinutes = DEFAULT_COOLDOWN_MINUTES;
        if (template != null && template.getCooldownMinutes() != null) {
            cooldownMinutes = template.getCooldownMinutes();
        }

        LocalDateTime lastSend = lastSendTimes.getIfPresent(cooldownKey);
        if (lastSend != null && cooldownMinutes > 0) {
            long minutesSinceLastSend = java.time.temporal.ChronoUnit.MINUTES.between(lastSend, LocalDateTime.now());
            if (minutesSinceLastSend < cooldownMinutes) {
                log.info("Alert suppressed for channel {} ({}), cooldown {}/{} min",
                        channel.getName(), triggerType, minutesSinceLastSend, cooldownMinutes);
                String alertContent = buildAlertMessage(template, executionLog);
                recordAlertLog(String.valueOf(channel.getId()), channel.getName(), channel.getType(),
                        executionLog, alertContent, AlertSendStatus.SUPPRESSED.name(), triggerType);
                return;
            }
        }

        try {
            String alertContent = buildAlertMessage(template, executionLog);
            sendByChannelType(channel, executionLog, alertContent);
            lastSendTimes.put(cooldownKey, LocalDateTime.now());
            recordAlertLog(String.valueOf(channel.getId()), channel.getName(), channel.getType(),
                    executionLog, alertContent, AlertSendStatus.SENT.name(), triggerType);
        } catch (Exception e) {
            log.error("Failed to send alert via channel: {}", channel.getName(), e);
            recordAlertLog(String.valueOf(channel.getId()), channel.getName(), channel.getType(),
                    executionLog, "发送失败: " + e.getMessage(), AlertSendStatus.FAILED.name(), triggerType);
        }
    }

    private void sendByChannelType(AlertChannel channel, ExecutionLog executionLog, String alertContent) throws Exception {
        if (ChannelType.EMAIL.name().equals(channel.getType())) {
            Map<String, Object> config = objectMapper.readValue(channel.getConfig(), Map.class);
            sendEmail(config, alertContent);
        } else if (ChannelType.WEBHOOK.name().equals(channel.getType())) {
            Map<String, Object> config = objectMapper.readValue(channel.getConfig(), Map.class);
            sendWebhook(config, executionLog, alertContent);
        } else if (ChannelType.DINGTALK.name().equals(channel.getType())) {
            Map<String, Object> config = objectMapper.readValue(channel.getConfig(), Map.class);
            sendDingTalk(config, executionLog, alertContent);
        } else if (ChannelType.WECHAT.name().equals(channel.getType())) {
            Map<String, Object> config = objectMapper.readValue(channel.getConfig(), Map.class);
            sendWeChat(config, executionLog, alertContent);
        } else if (ChannelType.FEISHU.name().equals(channel.getType())) {
            Map<String, Object> config = objectMapper.readValue(channel.getConfig(), Map.class);
            sendFeishu(config, executionLog, alertContent);
        }
    }

    public void sendAlertSync(AlertChannel channel, ExecutionLog executionLog) throws Exception {
        AlertTemplate template = resolveTemplate("TEST");
        String alertContent = buildAlertMessage(template, executionLog);

        sendByChannelType(channel, executionLog, alertContent);

        String cooldownKey = "ch:" + channel.getId() + ":TEST";
        lastSendTimes.put(cooldownKey, LocalDateTime.now());
        recordAlertLog(String.valueOf(channel.getId()), channel.getName(), channel.getType(),
                executionLog, alertContent, AlertSendStatus.SENT.name(), "TEST");
    }

    /**
     * 统一按渠道ID列表发送告警，优先使用新系统（AlertChannel + AlertTemplate），
     * 找不到时回退到旧系统 AlertConfig。
     */
    public void sendAlertByIds(String configIds, ExecutionLog executionLog, String triggerType) {
        if (configIds == null || configIds.isEmpty()) {
            recordAlertLog(null, null, "NONE", executionLog, executionLog.getErrorMessage(), AlertSendStatus.NO_CHANNEL.name(), triggerType);
            return;
        }

        Long monitorId = executionLog.getMonitorId();
        Long groupId = executionLog.getGroupId();
        boolean isSuccess = "SUCCESS".equals(executionLog.getStatus());

        String fingerprint = alertRateLimitService.buildFingerprint(
                triggerType, monitorId, groupId, executionLog.getDomain(), executionLog.getUrl());

        // 1. 恢复通知：优先级最高（仅检测，不计数）
        boolean isRecovery = alertRateLimitService.checkRecovery(fingerprint, isSuccess, triggerType);
        if (isRecovery && alertRateLimitService.isRecoveryNotifyEnabled(triggerType)) {
            log.info("Alert recovered for fingerprint {}, sending recovery notification", fingerprint);
            ExecutionLog recoveryLog = buildRecoveryLog(executionLog, triggerType);
            sendRecoveryNotification(configIds, recoveryLog, triggerType);
            alertRateLimitService.resetCount(fingerprint);
            return;
        }

        // 成功时重置计数（非恢复场景，如手动恢复）
        if (isSuccess) {
            alertRateLimitService.incrementCount(fingerprint, true);
            return;
        }

        // 2. 静默检查：不发送 + 不计入限频计数
        if (alertSilenceService.isSilenced(monitorId, groupId)) {
            log.info("Alert silenced for monitor {} group {} ({})", monitorId, groupId, triggerType);
            recordAlertLog(null, null, "NONE", executionLog, "告警已静默", AlertSendStatus.SILENCED.name(), triggerType);
            return;
        }

        // 3. 冷却检查：不发送 + 不计入限频计数
        AlertTemplate template = resolveTemplate(triggerType);
        int cooldownMinutes = DEFAULT_COOLDOWN_MINUTES;
        if (template != null && template.getCooldownMinutes() != null) {
            cooldownMinutes = template.getCooldownMinutes();
        }
        String cooldownKey = fingerprint + ":" + triggerType;
        LocalDateTime lastSend = lastSendTimes.getIfPresent(cooldownKey);
        if (lastSend != null && cooldownMinutes > 0) {
            long minutesSinceLastSend = java.time.temporal.ChronoUnit.MINUTES.between(lastSend, LocalDateTime.now());
            if (minutesSinceLastSend < cooldownMinutes) {
                log.info("Alert suppressed for fingerprint {} ({}), cooldown {}/{} min",
                        fingerprint, triggerType, minutesSinceLastSend, cooldownMinutes);
                String alertContent = buildAlertMessage(template, executionLog);
                recordAlertLog(null, null, "NONE", executionLog, alertContent, AlertSendStatus.SUPPRESSED.name(), triggerType);
                return;
            }
        }

        // 4. 限频检查：计数后判断
        alertRateLimitService.incrementCount(fingerprint, false);
        if (alertRateLimitService.isRateLimited(fingerprint, triggerType)) {
            log.info("Alert rate-limited for fingerprint {} ({})", fingerprint, triggerType);
            recordAlertLog(null, null, "NONE", executionLog, "告警已达限频上限", AlertSendStatus.SUPPRESSED.name(), triggerType);
            return;
        }

        // 5. 正常发送
        boolean anySent = false;
        for (String idStr : configIds.split(",")) {
            try {
                Long configId = Long.parseLong(idStr.trim());
                // AlertChannel 与 AlertConfig 共用 alert_config 表，这里只按 AlertChannel 处理
                AlertChannel channel = alertChannelMapper.selectById(configId);
                if (channel != null && Boolean.TRUE.equals(channel.getEnabled())) {
                    doSendAlert(channel, executionLog, triggerType);
                    anySent = true;
                } else {
                    recordAlertLog(idStr, channel != null ? channel.getName() : null,
                            channel != null ? channel.getType() : null,
                            executionLog, executionLog.getErrorMessage(), AlertSendStatus.CONFIG_UNAVAILABLE.name(), triggerType);
                }
            } catch (Exception e) {
                log.error("Failed to send {} alert for config: {}", triggerType, idStr, e);
                recordAlertLog(null, null, "NONE", executionLog, "发送异常: " + e.getMessage(), AlertSendStatus.FAILED.name(), triggerType);
            }
        }
        if (!anySent) {
            recordAlertLog(null, null, "NONE", executionLog, executionLog.getErrorMessage(), AlertSendStatus.NO_CHANNEL.name(), triggerType);
        }
    }

    // ========== Old System: AlertConfig (backward compatible) ==========

    @Async
    public void sendAlert(ExecutionLog executionLog, String alertType) {
        String triggerType = alertType != null ? alertType : "FAIL";

        Long monitorId = executionLog.getMonitorId();
        Long groupId = executionLog.getGroupId();
        boolean isSuccess = "SUCCESS".equals(executionLog.getStatus());

        String fingerprint = alertRateLimitService.buildFingerprint(
                triggerType, monitorId, groupId, executionLog.getDomain(), executionLog.getUrl());

        // 1. 恢复通知：优先级最高（仅检测，不计数）
        boolean isRecovery = alertRateLimitService.checkRecovery(fingerprint, isSuccess, triggerType);
        if (isRecovery && alertRateLimitService.isRecoveryNotifyEnabled(triggerType)) {
            log.info("Alert recovered for fingerprint {}, sending recovery notification", fingerprint);
            ExecutionLog recoveryLog = buildRecoveryLog(executionLog, triggerType);
            List<AlertChannel> channels = alertChannelMapper.selectList(
                    new LambdaQueryWrapper<AlertChannel>().eq(AlertChannel::getEnabled, true));
            for (AlertChannel channel : channels) {
                try {
                    sendByChannelType(channel, recoveryLog, "[恢复通知] 服务已恢复正常");
                    recordAlertLog(String.valueOf(channel.getId()), channel.getName(), channel.getType(),
                            recoveryLog, "[恢复通知] 服务已恢复正常", AlertSendStatus.SENT.name(), triggerType);
                } catch (Exception e) {
                    log.error("Failed to send recovery notification via channel: {}", channel.getName(), e);
                }
            }
            alertRateLimitService.resetCount(fingerprint);
            return;
        }

        // 成功时重置计数（非恢复场景）
        if (isSuccess) {
            alertRateLimitService.incrementCount(fingerprint, true);
            return;
        }

        // 2. 静默检查：不发送 + 不计入限频计数
        if (alertSilenceService.isSilenced(monitorId, groupId)) {
            log.info("Alert silenced for monitor {} group {} ({})", monitorId, groupId, triggerType);
            recordAlertLog(null, null, "NONE", executionLog, "告警已静默", AlertSendStatus.SILENCED.name(), triggerType);
            return;
        }

        // 3. 冷却检查：不发送 + 不计入限频计数
        AlertTemplate template = resolveTemplate(triggerType);
        int cooldownMinutes = DEFAULT_COOLDOWN_MINUTES;
        if (template != null && template.getCooldownMinutes() != null) {
            cooldownMinutes = template.getCooldownMinutes();
        }
        String cooldownKey = fingerprint + ":" + triggerType;
        LocalDateTime lastSend = lastSendTimes.getIfPresent(cooldownKey);
        if (lastSend != null && cooldownMinutes > 0) {
            long minutesSinceLastSend = java.time.temporal.ChronoUnit.MINUTES.between(lastSend, LocalDateTime.now());
            if (minutesSinceLastSend < cooldownMinutes) {
                log.info("Alert suppressed for fingerprint {} ({}), cooldown {}/{} min",
                        fingerprint, triggerType, minutesSinceLastSend, cooldownMinutes);
                String alertContent = buildAlertMessage(template, executionLog);
                recordAlertLog(null, null, "NONE", executionLog, alertContent, AlertSendStatus.SUPPRESSED.name(), triggerType);
                return;
            }
        }

        // 4. 限频检查：计数后判断
        alertRateLimitService.incrementCount(fingerprint, false);
        if (alertRateLimitService.isRateLimited(fingerprint, triggerType)) {
            log.info("Alert rate-limited for fingerprint {} ({})", fingerprint, triggerType);
            recordAlertLog(null, null, "NONE", executionLog, "告警已达限频上限", AlertSendStatus.SUPPRESSED.name(), triggerType);
            return;
        }

        // 5. 正常发送
        List<AlertChannel> channels = alertChannelMapper.selectList(
                new LambdaQueryWrapper<AlertChannel>()
                        .eq(AlertChannel::getEnabled, true)
        );
        if (channels.isEmpty()) {
            recordAlertLog(null, null, "NONE", executionLog, executionLog.getErrorMessage(), AlertSendStatus.NO_CHANNEL.name(), triggerType);
            return;
        }
        for (AlertChannel channel : channels) {
            doSendAlert(channel, executionLog, triggerType);
        }
    }

    public void sendAlert(AlertConfig config, ExecutionLog executionLog, String triggerType) {
        String cooldownKey = config.getId() + ":" + triggerType;
        int cooldownMinutes = config.getCooldownMinutes() != null ? config.getCooldownMinutes() : DEFAULT_COOLDOWN_MINUTES;

        LocalDateTime lastSend = lastSendTimes.getIfPresent(cooldownKey);
        if (lastSend != null && cooldownMinutes > 0) {
            long minutesSinceLastSend = java.time.temporal.ChronoUnit.MINUTES.between(lastSend, LocalDateTime.now());
            if (minutesSinceLastSend < cooldownMinutes) {
                log.info("Alert suppressed for config {} ({}), cooldown {}/{} min",
                        config.getName(), triggerType, minutesSinceLastSend, cooldownMinutes);
                String alertContent = buildAlertMessageFromConfig(config, executionLog);
                recordAlertLog(String.valueOf(config.getId()), config.getName(), config.getType(),
                        executionLog, alertContent, AlertSendStatus.SUPPRESSED.name(), triggerType);
                return;
            }
        }

        try {
            String alertContent = buildAlertMessageFromConfig(config, executionLog);

            if (ChannelType.EMAIL.name().equals(config.getType())) {
                sendEmailAlertFromConfig(config, alertContent);
            } else if (ChannelType.WEBHOOK.name().equals(config.getType())) {
                sendWebhookAlertFromConfig(config, executionLog, alertContent);
            }

            lastSendTimes.put(cooldownKey, LocalDateTime.now());
            recordAlertLog(String.valueOf(config.getId()), config.getName(), config.getType(),
                    executionLog, alertContent, AlertSendStatus.SENT.name(), triggerType);

        } catch (Exception e) {
            log.error("Failed to send alert: {}", config.getName(), e);
            recordAlertLog(String.valueOf(config.getId()), config.getName(), config.getType(),
                    executionLog, "发送失败: " + e.getMessage(), AlertSendStatus.FAILED.name(), triggerType);
        }
    }

    public void sendAlertSync(AlertConfig config, ExecutionLog executionLog) throws Exception {
        String alertContent = buildAlertMessageFromConfig(config, executionLog);

        if (ChannelType.EMAIL.name().equals(config.getType())) {
            sendEmailAlertFromConfig(config, alertContent);
        } else if (ChannelType.WEBHOOK.name().equals(config.getType())) {
            sendWebhookAlertFromConfig(config, executionLog, alertContent);
        }

        String cooldownKey = config.getId() + ":TEST";
        lastSendTimes.put(cooldownKey, LocalDateTime.now());
        recordAlertLog(String.valueOf(config.getId()), config.getName(), config.getType(),
                executionLog, alertContent, AlertSendStatus.SENT.name(), "TEST");
    }

    public void sendResponseTimeAlert(ExecutionLog executionLog, Monitor monitor, int actualTime, int threshold) {
        ExecutionLog alertLog = new ExecutionLog();
        alertLog.setMonitorId(monitor.getId());
        alertLog.setMonitorName(monitor.getName());
        alertLog.setGroupId(executionLog.getGroupId());
        alertLog.setExecutionId(executionLog.getExecutionId());
        alertLog.setStatusCode(executionLog.getStatusCode());
        alertLog.setResponseTime(actualTime);
        alertLog.setErrorMessage(String.format("响应时间 %dms 超过阈值 %dms（连续触发）", actualTime, threshold));
        alertLog.setExecutedAt(executionLog.getExecutedAt());
        alertLog.setStatus("SLOW");
        alertLog.setUrl(executionLog.getUrl());
        alertLog.setDomain(executionLog.getDomain());

        sendAlertByIds(monitor.getResponseTimeAlertConfigIds(), alertLog, "RESPONSE_TIME");
    }

    @Async
    public void sendGroupAlert(Long groupId, String groupName, int failCount, String alertConfigIds, String executionId) {
        ExecutionLog groupLog = new ExecutionLog();
        groupLog.setGroupId(groupId);
        groupLog.setMonitorName(groupName);
        groupLog.setExecutionId(executionId);
        groupLog.setStatus("FAIL");
        groupLog.setErrorMessage(String.format("任务 [%s] 连续失败 %d 次", groupName, failCount));
        groupLog.setExecutedAt(java.time.LocalDateTime.now());

        sendAlertByIds(alertConfigIds, groupLog, "GROUP_FAIL");
    }

    // ========== Template Resolution ==========

    private AlertTemplate resolveTemplate(String triggerType) {
        if (triggerType == null || triggerType.isEmpty()) {
            triggerType = "ALL";
        }
        AlertTemplate template = alertTemplateMapper.selectOne(
                new LambdaQueryWrapper<AlertTemplate>()
                        .eq(AlertTemplate::getTriggerType, triggerType)
                        .eq(AlertTemplate::getEnabled, true)
                        .orderByAsc(AlertTemplate::getId)
                        .last("LIMIT 1")
        );
        if (template != null) return template;

        if (!"ALL".equals(triggerType)) {
            template = alertTemplateMapper.selectOne(
                    new LambdaQueryWrapper<AlertTemplate>()
                            .eq(AlertTemplate::getTriggerType, "ALL")
                            .eq(AlertTemplate::getEnabled, true)
                            .orderByAsc(AlertTemplate::getId)
                            .last("LIMIT 1")
            );
        }
        return template;
    }

    // ========== Message Building ==========

    private String buildAlertMessage(AlertTemplate template, ExecutionLog executionLog) {
        String content = (template != null && template.getContent() != null && !template.getContent().isEmpty())
                ? template.getContent() : DEFAULT_TEMPLATE;
        return renderTemplate(content, executionLog);
    }

    private String buildAlertMessageFromConfig(AlertConfig config, ExecutionLog executionLog) {
        String template = config.getAlertTemplate();
        if (template == null || template.isEmpty()) {
            template = DEFAULT_TEMPLATE;
        }
        return renderTemplate(template, executionLog);
    }

    private String renderTemplate(String template, ExecutionLog executionLog) {
        String monitorName = executionLog.getMonitorName();
        if (monitorName == null && executionLog.getMonitorId() != null && executionLog.getMonitorId() > 0) {
            Monitor m = monitorMapper.selectById(executionLog.getMonitorId());
            monitorName = m != null ? m.getName() : "-";
        }
        if (monitorName == null) monitorName = "-";

        String executedAtStr = "-";
        if (executionLog.getExecutedAt() != null) {
            java.time.ZoneId beijing = java.time.ZoneId.of("Asia/Shanghai");
            java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            executedAtStr = executionLog.getExecutedAt().atZone(beijing).format(formatter);
        }

        return template
                .replace("{{monitorId}}", executionLog.getMonitorId() != null ? String.valueOf(executionLog.getMonitorId()) : "-")
                .replace("{{monitorName}}", monitorName)
                .replace("{{groupId}}", executionLog.getGroupId() != null ? String.valueOf(executionLog.getGroupId()) : "-")
                .replace("{{status}}", executionLog.getStatus() != null ? executionLog.getStatus() : "-")
                .replace("{{statusCode}}", executionLog.getStatusCode() != null ? String.valueOf(executionLog.getStatusCode()) : "-")
                .replace("{{errorMessage}}", executionLog.getErrorMessage() != null ? executionLog.getErrorMessage() : "无")
                .replace("{{executedAt}}", executedAtStr)
                .replace("{{responseTime}}", executionLog.getResponseTime() != null ? executionLog.getResponseTime() + "ms" : "-")
                .replace("{{domain}}", executionLog.getDomain() != null ? executionLog.getDomain() : "-")
                .replace("{{url}}", executionLog.getUrl() != null ? executionLog.getUrl() : "-");
    }

    private String formatBeijingTime(LocalDateTime dt) {
        if (dt == null) return "-";
        java.time.ZoneId beijing = java.time.ZoneId.of("Asia/Shanghai");
        java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        return dt.atZone(beijing).format(fmt);
    }

    // ========== Alert Log Recording ==========

    private void recordAlertLog(String configId, String configName, String alertType,
                                 ExecutionLog executionLog, String alertContent,
                                 String status, String triggerType) {
        AlertLog alertLog = new AlertLog();
        Long monitorId = executionLog.getMonitorId();
        if (monitorId != null && monitorMapper.selectById(monitorId) == null) {
            monitorId = null;
        }
        alertLog.setMonitorId(monitorId);
        alertLog.setMonitorName(executionLog.getMonitorName());
        alertLog.setGroupId(executionLog.getGroupId());
        alertLog.setGroupName(buildSourceName(executionLog, triggerType));
        alertLog.setExecutionId(executionLog.getExecutionId());
        if (configId != null) {
            try { alertLog.setAlertConfigId(Long.parseLong(configId)); } catch (NumberFormatException ignored) {}
        }
        alertLog.setAlertConfigName(configName);
        alertLog.setAlertType(alertType != null ? alertType : "NONE");
        alertLog.setTriggerType(triggerType);
        alertLog.setStatusCode(executionLog.getStatusCode());
        alertLog.setResponseTime(executionLog.getResponseTime());
        alertLog.setErrorMessage(executionLog.getErrorMessage());
        alertLog.setAlertContent(alertContent);
        alertLog.setStatus(status);
        alertLog.setSentAt(java.time.LocalDateTime.now());
        alertLogMapper.insert(alertLog);
    }

    // ========== Email Sending ==========

    private void sendEmailAlertFromConfig(AlertConfig config, String alertContent) throws Exception {
        Map<String, Object> emailConfig = objectMapper.readValue(config.getConfig(), Map.class);
        sendEmail(emailConfig, alertContent);
    }

    private void sendEmail(Map<String, Object> emailConfig, String alertContent) throws Exception {
        String smtpHost = (String) emailConfig.get("smtpHost");
        Object smtpPortObj = emailConfig.get("smtpPort");
        int smtpPort = smtpPortObj instanceof Number ? ((Number) smtpPortObj).intValue() : 587;
        String smtpUsername = (String) emailConfig.get("smtpUsername");
        String smtpPassword = (String) emailConfig.get("smtpPassword");
        Boolean smtpSsl = emailConfig.get("smtpSsl") instanceof Boolean ? (Boolean) emailConfig.get("smtpSsl") : true;
        String from = (String) emailConfig.get("from");
        String to = (String) emailConfig.get("to");

        log.info("Sending email alert via {}:{} from={} to={}", smtpHost, smtpPort, from, to);

        org.springframework.mail.javamail.JavaMailSenderImpl mailSenderImpl =
                new org.springframework.mail.javamail.JavaMailSenderImpl();
        mailSenderImpl.setHost(smtpHost);
        mailSenderImpl.setPort(smtpPort);
        mailSenderImpl.setUsername(smtpUsername);
        mailSenderImpl.setPassword(smtpPassword);

        java.util.Properties props = mailSenderImpl.getJavaMailProperties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.connectiontimeout", "30000");
        props.put("mail.smtp.timeout", "30000");
        props.put("mail.smtp.writetimeout", "30000");
        if (smtpPort == 465) {
            props.put("mail.smtp.ssl.enable", "true");
        } else if (Boolean.TRUE.equals(smtpSsl)) {
            props.put("mail.smtp.starttls.enable", "true");
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to.split(","));
        message.setSubject("[LitVMonitor] 监控告警");
        message.setText(alertContent);

        mailSenderImpl.send(message);
        log.info("Email alert sent successfully to {}", to);
    }

    // ========== Webhook Sending ==========

    private void sendWebhookAlertFromConfig(AlertConfig config, ExecutionLog executionLog, String alertContent) throws Exception {
        Map<String, Object> webhookConfig = objectMapper.readValue(config.getConfig(), Map.class);
        sendWebhook(webhookConfig, executionLog, alertContent);
    }

    private void sendWebhook(Map<String, Object> webhookConfig, ExecutionLog executionLog, String alertContent) throws Exception {
        String webhookUrl = (String) webhookConfig.get("url");
        String method = (String) webhookConfig.getOrDefault("method", "POST");
        String contentType = (String) webhookConfig.getOrDefault("contentType", "application/json");
        String headersJson = (String) webhookConfig.get("headers");
        String template = (String) webhookConfig.get("template");

        validateOutboundHost(webhookUrl);
        log.info("Sending webhook alert (method={})", method);

        String jsonBody;
        if (template != null && !template.isEmpty()) {
            jsonBody = template
                    .replace("{{alertContent}}", alertContent)
                    .replace("{{monitorId}}", executionLog.getMonitorId() != null ? String.valueOf(executionLog.getMonitorId()) : "-")
                    .replace("{{monitorName}}", executionLog.getMonitorName() != null ? executionLog.getMonitorName() : "-")
                    .replace("{{groupId}}", executionLog.getGroupId() != null ? String.valueOf(executionLog.getGroupId()) : "-")
                    .replace("{{status}}", executionLog.getStatus())
                    .replace("{{statusCode}}", executionLog.getStatusCode() != null ? String.valueOf(executionLog.getStatusCode()) : "-")
                    .replace("{{errorMessage}}", executionLog.getErrorMessage() != null ? executionLog.getErrorMessage() : "无")
                    .replace("{{executedAt}}", formatBeijingTime(executionLog.getExecutedAt()))
                    .replace("{{responseTime}}", executionLog.getResponseTime() != null ? executionLog.getResponseTime() + "ms" : "-");
        } else {
            jsonBody = objectMapper.writeValueAsString(Map.of(
                    "monitorId", executionLog.getMonitorId() != null ? executionLog.getMonitorId() : 0,
                    "monitorName", executionLog.getMonitorName() != null ? executionLog.getMonitorName() : "",
                    "status", executionLog.getStatus(),
                    "statusCode", executionLog.getStatusCode() != null ? executionLog.getStatusCode() : 0,
                    "errorMessage", executionLog.getErrorMessage() != null ? executionLog.getErrorMessage() : "",
                    "executedAt", formatBeijingTime(executionLog.getExecutedAt()),
                    "alertContent", alertContent
            ));
        }

        log.debug("Webhook body length: {}", jsonBody.length());

        okhttp3.OkHttpClient client = buildSafeClient();
        okhttp3.RequestBody body = okhttp3.RequestBody.create(
                jsonBody,
                okhttp3.MediaType.parse(contentType)
        );

        okhttp3.Request.Builder requestBuilder = new okhttp3.Request.Builder()
                .url(webhookUrl);

        if (headersJson != null && !headersJson.isEmpty()) {
            try {
                Map<String, String> headers = objectMapper.readValue(headersJson, Map.class);
                headers.forEach(requestBuilder::addHeader);
            } catch (Exception e) {
                log.warn("Failed to parse webhook headers", e);
            }
        }

        if ("PUT".equals(method)) {
            requestBuilder.put(body);
        } else {
            requestBuilder.post(body);
        }

        try (okhttp3.Response response = client.newCall(requestBuilder.build()).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            log.info("Webhook response: code={}", response.code());
            if (!response.isSuccessful()) {
                throw new RuntimeException("Webhook failed with code: " + response.code() + ", body: " + responseBody);
            }
        }
    }

    private void sendDingTalk(Map<String, Object> config, ExecutionLog executionLog, String alertContent) throws Exception {
        String webhookUrl = (String) config.get("url");
        String title = "LitVMonitor 告警通知";

        String markdownContent = "### " + title + "\n\n" +
                "- **状态**: " + (executionLog.getStatus() != null ? executionLog.getStatus() : "-") + "\n" +
                "- **监控**: " + (executionLog.getMonitorName() != null ? executionLog.getMonitorName() : "-") + "\n" +
                "- **URL**: " + (executionLog.getUrl() != null ? executionLog.getUrl() : "-") + "\n" +
                "- **状态码**: " + (executionLog.getStatusCode() != null ? executionLog.getStatusCode() : "-") + "\n" +
                "- **响应时间**: " + (executionLog.getResponseTime() != null ? executionLog.getResponseTime() + "ms" : "-") + "\n" +
                "- **时间**: " + formatBeijingTime(executionLog.getExecutedAt()) + "\n" +
                "- **详情**: " + alertContent;

        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("msgtype", "markdown");
        Map<String, String> markdown = new java.util.LinkedHashMap<>();
        markdown.put("title", title);
        markdown.put("text", markdownContent);
        body.put("markdown", markdown);

        String jsonBody = objectMapper.writeValueAsString(body);
        sendHttpPost(webhookUrl, jsonBody, "application/json");
        log.info("DingTalk alert sent");
    }

    private void sendWeChat(Map<String, Object> config, ExecutionLog executionLog, String alertContent) throws Exception {
        String webhookUrl = (String) config.get("url");

        String content = "## LitVMonitor 告警通知\n" +
                "> 状态: <font color=\"" + ("SUCCESS".equals(executionLog.getStatus()) ? "info" : "warning") + "\">" +
                (executionLog.getStatus() != null ? executionLog.getStatus() : "-") + "</font>\n" +
                "> 监控: " + (executionLog.getMonitorName() != null ? executionLog.getMonitorName() : "-") + "\n" +
                "> URL: " + (executionLog.getUrl() != null ? executionLog.getUrl() : "-") + "\n" +
                "> 状态码: " + (executionLog.getStatusCode() != null ? executionLog.getStatusCode() : "-") + "\n" +
                "> 响应时间: " + (executionLog.getResponseTime() != null ? executionLog.getResponseTime() + "ms" : "-") + "\n" +
                "> 时间: " + formatBeijingTime(executionLog.getExecutedAt()) + "\n" +
                "> 详情: " + alertContent;

        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("msgtype", "markdown");
        Map<String, String> markdown = new java.util.LinkedHashMap<>();
        markdown.put("content", content);
        body.put("markdown", markdown);

        String jsonBody = objectMapper.writeValueAsString(body);
        sendHttpPost(webhookUrl, jsonBody, "application/json");
        log.info("WeChat alert sent");
    }

    private void sendFeishu(Map<String, Object> config, ExecutionLog executionLog, String alertContent) throws Exception {
        String webhookUrl = (String) config.get("url");

        String text = "LitVMonitor 告警通知\n\n" +
                "状态: " + (executionLog.getStatus() != null ? executionLog.getStatus() : "-") + "\n" +
                "监控: " + (executionLog.getMonitorName() != null ? executionLog.getMonitorName() : "-") + "\n" +
                "URL: " + (executionLog.getUrl() != null ? executionLog.getUrl() : "-") + "\n" +
                "状态码: " + (executionLog.getStatusCode() != null ? executionLog.getStatusCode() : "-") + "\n" +
                "响应时间: " + (executionLog.getResponseTime() != null ? executionLog.getResponseTime() + "ms" : "-") + "\n" +
                "时间: " + formatBeijingTime(executionLog.getExecutedAt()) + "\n" +
                "详情: " + alertContent;

        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("msg_type", "text");
        Map<String, String> content = new java.util.LinkedHashMap<>();
        content.put("text", text);
        body.put("content", content);

        String jsonBody = objectMapper.writeValueAsString(body);
        sendHttpPost(webhookUrl, jsonBody, "application/json");
        log.info("Feishu alert sent");
    }

    private void sendHttpPost(String url, String jsonBody, String contentType) throws Exception {
        validateOutboundHost(url);
        okhttp3.OkHttpClient client = buildSafeClient();
        okhttp3.RequestBody body = okhttp3.RequestBody.create(
                jsonBody,
                okhttp3.MediaType.parse(contentType)
        );
        okhttp3.Request request = new okhttp3.Request.Builder()
                .url(url)
                .post(body)
                .build();
        try (okhttp3.Response response = client.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            log.info("HTTP POST response: code={}", response.code());
            if (!response.isSuccessful()) {
                throw new RuntimeException("HTTP POST failed with code: " + response.code() + ", body: " + responseBody);
            }
        }
    }

    /** 构造带 SSRF 防护的出站 HTTP 客户端（校验解析后的 IP，覆盖重定向）。缓存复用。 */
    private volatile okhttp3.OkHttpClient safeClient;

    private okhttp3.OkHttpClient buildSafeClient() {
        if (safeClient != null) return safeClient;
        synchronized (this) {
            if (safeClient != null) return safeClient;
            okhttp3.OkHttpClient.Builder builder = new okhttp3.OkHttpClient.Builder()
                    .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS);
            if (ssrfGuard != null && ssrfGuard.isEnabled()) {
                builder.dns(hostname -> {
                    java.util.List<java.net.InetAddress> addrs = okhttp3.Dns.SYSTEM.lookup(hostname);
                    for (java.net.InetAddress a : addrs) {
                        ssrfGuard.assertAllowed(a);
                    }
                    return addrs;
                });
            }
            safeClient = builder.build();
            return safeClient;
        }
    }

    /** 显式校验出站 URL 的主机（OkHttp Dns 对 IP 字面量可能跳过）。 */
    private void validateOutboundHost(String url) {
        if (ssrfGuard == null || !ssrfGuard.isEnabled()) return;
        try {
            ssrfGuard.assertHostAllowed(new java.net.URL(url).getHost());
        } catch (java.net.MalformedURLException e) {
            throw new RuntimeException("非法的地址: " + url);
        }
    }

    // ========== Query Methods ==========

    public Page<AlertLog> listAlertLogs(Page<AlertLog> page, Long monitorId, Long groupId,
                                         String triggerType, Long alertConfigId,
                                         String status, String executionId,
                                         String alertCategory,
                                         String startTime, String endTime) {
        LambdaQueryWrapper<AlertLog> wrapper = new LambdaQueryWrapper<>();
        if (monitorId != null) {
            wrapper.eq(AlertLog::getMonitorId, monitorId);
        }
        if (groupId != null) {
            wrapper.eq(AlertLog::getGroupId, groupId);
        }
        if (triggerType != null && !triggerType.isEmpty()) {
            wrapper.eq(AlertLog::getTriggerType, triggerType);
        }
        if (alertConfigId != null) {
            wrapper.eq(AlertLog::getAlertConfigId, alertConfigId);
        }
        if (status != null && !status.isEmpty()) {
            wrapper.eq(AlertLog::getStatus, status);
        }
        if (executionId != null && !executionId.isEmpty()) {
            wrapper.eq(AlertLog::getExecutionId, executionId);
        }
        if ("RECOVERY".equals(alertCategory)) {
            wrapper.like(AlertLog::getAlertContent, "[恢复通知]");
        } else if ("ALERT".equals(alertCategory)) {
            wrapper.notLike(AlertLog::getAlertContent, "[恢复通知]");
        }
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        if (startTime != null && !startTime.isEmpty()) {
            wrapper.ge(AlertLog::getSentAt, LocalDateTime.parse(startTime, fmt));
        }
        if (endTime != null && !endTime.isEmpty()) {
            wrapper.le(AlertLog::getSentAt, LocalDateTime.parse(endTime, fmt));
        }
        wrapper.orderByDesc(AlertLog::getSentAt);
        return alertLogMapper.selectPage(page, wrapper);
    }

    public void recordAlertLogForSsl(ExecutionLog executionLog, String reason) {
        recordAlertLog(null, null, "NONE", executionLog, reason, AlertSendStatus.NO_CHANNEL.name(), "SSL_CERT");
    }

    private String buildSourceName(ExecutionLog executionLog, String triggerType) {
        if ("GROUP_FAIL".equals(triggerType) && executionLog.getGroupId() != null) {
            String groupName = executionLog.getMonitorName() != null ? executionLog.getMonitorName() : "";
            return "监控任务#" + executionLog.getGroupId() + " " + groupName;
        }
        if ("SSL_CERT".equals(triggerType)) {
            if (executionLog.getDomain() != null) {
                return "证书告警 " + executionLog.getDomain();
            }
            return null;
        }
        if (executionLog.getMonitorId() != null && executionLog.getMonitorId() > 0) {
            String monitorName = executionLog.getMonitorName() != null ? executionLog.getMonitorName() : "";
            return "监控项#" + executionLog.getMonitorId() + " " + monitorName;
        }
        return null;
    }

    public AlertLog getAlertLogById(Long id) {
        return alertLogMapper.selectById(id);
    }

    private ExecutionLog buildRecoveryLog(ExecutionLog original, String triggerType) {
        ExecutionLog recoveryLog = new ExecutionLog();
        recoveryLog.setMonitorId(original.getMonitorId());
        recoveryLog.setMonitorName(original.getMonitorName());
        recoveryLog.setGroupId(original.getGroupId());
        recoveryLog.setExecutionId(original.getExecutionId());
        recoveryLog.setDomain(original.getDomain());
        recoveryLog.setUrl(original.getUrl());
        recoveryLog.setStatus("SUCCESS");
        recoveryLog.setExecutedAt(LocalDateTime.now());
        recoveryLog.setErrorMessage("[恢复] 服务已恢复正常");
        return recoveryLog;
    }

    private void sendRecoveryNotification(String configIds, ExecutionLog recoveryLog, String triggerType) {
        String recoveryContent = "[恢复通知] " + (recoveryLog.getMonitorName() != null ? recoveryLog.getMonitorName() : "监控")
                + " 已恢复正常\n时间: " + recoveryLog.getExecutedAt();

        for (String idStr : configIds.split(",")) {
            try {
                Long configId = Long.parseLong(idStr.trim());
                AlertChannel channel = alertChannelMapper.selectById(configId);
                if (channel != null && Boolean.TRUE.equals(channel.getEnabled())) {
                    sendByChannelType(channel, recoveryLog, recoveryContent);
                    recordAlertLog(String.valueOf(channel.getId()), channel.getName(), channel.getType(),
                            recoveryLog, recoveryContent, AlertSendStatus.SENT.name(), triggerType);
                }
            } catch (Exception e) {
                log.error("Failed to send recovery notification for config: {}", idStr, e);
            }
        }
    }
}
