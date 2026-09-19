package com.litv.monitor.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.litv.monitor.entity.AlertChannel;
import com.litv.monitor.entity.ReminderTask;
import com.litv.monitor.enums.ReminderCategory;
import com.litv.monitor.mapper.AlertChannelMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReminderNotifyService {

    private final AlertChannelMapper alertChannelMapper;
    private final ObjectMapper objectMapper;
    private final SsrfProtectionService ssrfGuard;

    public void sendReminder(ReminderTask task, String remindType) {
        if (task.getAlertChannelIds() == null || task.getAlertChannelIds().trim().isEmpty()) {
            log.info("No alert channels for reminder: {}, skip", task.getTitle());
            return;
        }

        String title = task.getTitle();
        String description = task.getDescription() != null ? task.getDescription() : "无";
        String categoryLabel = getCategoryLabel(task.getCategory());
        String typeLabel = "advance".equals(remindType) ? "提前提醒" : "到期提醒";
        String advanceInfo = "";
        if ("advance".equals(remindType) && task.getAdvanceEnabled()) {
            if (task.getAdvanceDays() != null && task.getAdvanceDays() > 0) {
                advanceInfo = "提前" + task.getAdvanceDays() + "天";
            } else if (task.getAdvanceMinutes() != null && task.getAdvanceMinutes() > 0) {
                advanceInfo = "提前" + task.getAdvanceMinutes() + "分钟";
            }
        }

        String content = "任务通知 | " + title + "\n"
                + "分类: " + categoryLabel + "\n"
                + "描述: " + description + "\n"
                + "到期时间: " + task.getDueDate() + "\n"
                + "提醒类型: " + advanceInfo + typeLabel;

        String[] channelIds = task.getAlertChannelIds().split(",");
        for (String channelId : channelIds) {
            try {
                AlertChannel channel = alertChannelMapper.selectById(Long.parseLong(channelId.trim()));
                if (channel == null || !Boolean.TRUE.equals(channel.getEnabled())) {
                    continue;
                }
                sendByChannelType(channel, task, content);
                log.info("Reminder notification sent via channel {} for task: {}", channel.getName(), task.getTitle());
            } catch (Exception e) {
                log.error("Failed to send reminder notification via channel {}", channelId, e);
            }
        }
    }

    private void sendByChannelType(AlertChannel channel, ReminderTask task, String content) throws Exception {
        String type = channel.getType();
        Map<String, Object> config = objectMapper.readValue(channel.getConfig(), Map.class);

        if ("EMAIL".equals(type)) {
            sendReminderEmail(config, task, content);
        } else if ("WEBHOOK".equals(type)) {
            sendReminderWebhook(config, task, content);
        } else if ("DINGTALK".equals(type)) {
            sendReminderDingTalk(config, task, content);
        } else if ("WECHAT".equals(type)) {
            sendReminderWeChat(config, task, content);
        } else if ("FEISHU".equals(type)) {
            sendReminderFeishu(config, task, content);
        }
    }

    private void sendReminderEmail(Map<String, Object> emailConfig, ReminderTask task, String content) throws Exception {
        String smtpHost = (String) emailConfig.get("smtpHost");
        Object smtpPortObj = emailConfig.get("smtpPort");
        int smtpPort = smtpPortObj instanceof Number ? ((Number) smtpPortObj).intValue() : 587;
        String smtpUsername = (String) emailConfig.get("smtpUsername");
        String smtpPassword = (String) emailConfig.get("smtpPassword");
        Boolean smtpSsl = emailConfig.get("smtpSsl") instanceof Boolean ? (Boolean) emailConfig.get("smtpSsl") : true;
        String from = (String) emailConfig.get("from");
        String to = (String) emailConfig.get("to");

        org.springframework.mail.javamail.JavaMailSenderImpl mailSender =
                new org.springframework.mail.javamail.JavaMailSenderImpl();
        mailSender.setHost(smtpHost);
        mailSender.setPort(smtpPort);
        mailSender.setUsername(smtpUsername);
        mailSender.setPassword(smtpPassword);

        java.util.Properties props = mailSender.getJavaMailProperties();
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
        message.setSubject("[LitVMonitor] 任务通知 - " + task.getTitle());
        message.setText(content);

        mailSender.send(message);
    }

    private void sendReminderWebhook(Map<String, Object> webhookConfig, ReminderTask task, String content) throws Exception {
        String webhookUrl = (String) webhookConfig.get("url");
        validateOutboundHost(webhookUrl);
        String method = (String) webhookConfig.getOrDefault("method", "POST");
        String contentType = (String) webhookConfig.getOrDefault("contentType", "application/json");
        String headersJson = (String) webhookConfig.get("headers");
        String template = (String) webhookConfig.get("template");

        String jsonBody;
        if (template != null && !template.isEmpty()) {
            Map<String, String> vars = new LinkedHashMap<>();
            vars.put("alertContent", content);
            vars.put("monitorId", "0");
            vars.put("monitorName", task.getTitle());
            vars.put("status", "REMINDER");
            vars.put("errorMessage", task.getDescription() != null ? task.getDescription() : "");
            vars.put("executedAt", task.getDueDate());
            jsonBody = template;
            for (Map.Entry<String, String> entry : vars.entrySet()) {
                jsonBody = jsonBody.replace("{{" + entry.getKey() + "}}", entry.getValue());
            }
        } else {
            Map<String, Object> bodyMap = new LinkedHashMap<>();
            bodyMap.put("monitorId", 0);
            bodyMap.put("monitorName", task.getTitle());
            bodyMap.put("status", "REMINDER");
            bodyMap.put("statusCode", 0);
            bodyMap.put("errorMessage", task.getDescription() != null ? task.getDescription() : "");
            bodyMap.put("executedAt", task.getDueDate());
            bodyMap.put("alertContent", content);
            jsonBody = objectMapper.writeValueAsString(bodyMap);
        }

        okhttp3.OkHttpClient client = buildSafeClient();
        okhttp3.RequestBody body = okhttp3.RequestBody.create(
                jsonBody, okhttp3.MediaType.parse(contentType));

        okhttp3.Request.Builder requestBuilder = new okhttp3.Request.Builder().url(webhookUrl);

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
            if (!response.isSuccessful()) {
                throw new RuntimeException("Webhook failed with code: " + response.code());
            }
        }
    }

    private void sendReminderDingTalk(Map<String, Object> config, ReminderTask task, String content) throws Exception {
        String webhookUrl = (String) config.get("url");

        String markdownContent = "### LitVMonitor 任务通知\n\n" +
                "- **任务**: " + task.getTitle() + "\n" +
                "- **分类**: " + getCategoryLabel(task.getCategory()) + "\n" +
                "- **描述**: " + (task.getDescription() != null ? task.getDescription() : "-") + "\n" +
                "- **到期时间**: " + task.getDueDate() + "\n";

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("msgtype", "markdown");
        Map<String, String> markdown = new LinkedHashMap<>();
        markdown.put("title", "LitVMonitor 任务通知");
        markdown.put("text", markdownContent);
        body.put("markdown", markdown);

        String jsonBody = objectMapper.writeValueAsString(body);
        sendHttpPost(webhookUrl, jsonBody, "application/json");
    }

    private void sendReminderWeChat(Map<String, Object> config, ReminderTask task, String content) throws Exception {
        String webhookUrl = (String) config.get("url");

        String mdContent = "## LitVMonitor 任务通知\n" +
                "> 任务: " + task.getTitle() + "\n" +
                "> 分类: " + getCategoryLabel(task.getCategory()) + "\n" +
                "> 描述: " + (task.getDescription() != null ? task.getDescription() : "-") + "\n" +
                "> 到期时间: " + task.getDueDate() + "\n";

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("msgtype", "markdown");
        Map<String, String> markdown = new LinkedHashMap<>();
        markdown.put("content", mdContent);
        body.put("markdown", markdown);

        String jsonBody = objectMapper.writeValueAsString(body);
        sendHttpPost(webhookUrl, jsonBody, "application/json");
    }

    private void sendReminderFeishu(Map<String, Object> config, ReminderTask task, String content) throws Exception {
        String webhookUrl = (String) config.get("url");

        String text = "LitVMonitor 任务通知\n\n"
                + "任务: " + task.getTitle() + "\n"
                + "分类: " + getCategoryLabel(task.getCategory()) + "\n"
                + "描述: " + (task.getDescription() != null ? task.getDescription() : "-") + "\n"
                + "到期时间: " + task.getDueDate() + "\n";

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("msg_type", "text");
        Map<String, String> contentMap = new LinkedHashMap<>();
        contentMap.put("text", text);
        body.put("content", contentMap);

        String jsonBody = objectMapper.writeValueAsString(body);
        sendHttpPost(webhookUrl, jsonBody, "application/json");
    }

    private void sendHttpPost(String url, String jsonBody, String contentType) throws Exception {
        validateOutboundHost(url);
        okhttp3.OkHttpClient client = buildSafeClient();
        okhttp3.RequestBody body = okhttp3.RequestBody.create(
                jsonBody, okhttp3.MediaType.parse(contentType));
        okhttp3.Request request = new okhttp3.Request.Builder().url(url).post(body).build();
        try (okhttp3.Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new RuntimeException("HTTP POST failed with code: " + response.code());
            }
        }
    }

    /** 构造带 SSRF 防护的出站 HTTP 客户端。缓存复用。 */
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
            throw new IllegalArgumentException("非法的地址: " + url);
        }
    }

    private String getCategoryLabel(String category) {
        if (category == null) return "其他";
        try {
            return ReminderCategory.valueOf(category.toUpperCase()).getLabel();
        } catch (IllegalArgumentException e) {
            return category;
        }
    }
}
