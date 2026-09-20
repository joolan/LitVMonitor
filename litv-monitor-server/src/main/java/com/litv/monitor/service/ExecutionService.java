package com.litv.monitor.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.litv.monitor.dto.SchemaChange;
import com.litv.monitor.entity.AlertTemplate;
import com.litv.monitor.entity.ExecutionLog;
import com.litv.monitor.entity.Monitor;
import com.litv.monitor.executor.MonitorExecutor;
import com.litv.monitor.executor.MonitorExecutorRegistry;
import com.litv.monitor.mapper.AlertTemplateMapper;
import com.litv.monitor.mapper.ExecutionLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExecutionService {

    private final ExecutionLogMapper executionLogMapper;
    private final ObjectMapper objectMapper;
    private final VariableEngine variableEngine;
    private final ResponseTimeAlertService responseTimeAlertService;
    private final ApiSchemaService apiSchemaService;
    private final AlertService alertService;
    private final AlertTemplateMapper alertTemplateMapper;
    private final MonitorExecutorRegistry executorRegistry;

    @org.springframework.beans.factory.annotation.Value("${monitor.log-body-max-size:262144}")
    private int logBodyMaxSize;

    private final com.github.benmanes.caffeine.cache.Cache<Long, Integer> monitorConsecutiveFailCounts =
            com.github.benmanes.caffeine.cache.Caffeine.newBuilder()
                    .expireAfterWrite(1, java.util.concurrent.TimeUnit.HOURS)
                    .maximumSize(500)
                    .build();

    private String truncateBody(String body) {
        if (body == null || logBodyMaxSize <= 0) return body;
        if (body.length() <= logBodyMaxSize) return body;
        return body.substring(0, logBodyMaxSize) + "\n...[truncated " + (body.length() - logBodyMaxSize) + " chars]";
    }

    public ExecutionLog executeMonitor(Monitor monitor, Long groupId) {
        return executeMonitor(monitor, groupId, null);
    }

    public ExecutionLog executeMonitor(Monitor monitor, Long groupId, String executionId) {
        ExecutionLog logEntry = new ExecutionLog();
        logEntry.setMonitorId(monitor.getId());
        logEntry.setMonitorName(monitor.getName());
        logEntry.setGroupId(groupId);
        logEntry.setExecutionId(executionId);
        logEntry.setExecutedAt(LocalDateTime.now());

        String type = monitor.getMonitorType() != null ? monitor.getMonitorType() : "HTTP";

        try {
            Map<String, String> baseVars = variableEngine.loadVariables(groupId);

            MonitorExecutor executor = executorRegistry.getExecutor(type);
            if (executor != null) {
                // retryCount 表示最大尝试次数（默认 1 = 不重试）
                int maxAttempts = monitor.getRetryCount() != null ? Math.max(1, monitor.getRetryCount()) : 1;
                for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                    if (attempt > 1) {
                        // 清除上一次尝试的残留，避免误导
                        logEntry.setStatusCode(null);
                        logEntry.setResponseTime(null);
                        logEntry.setResponseBody(null);
                        logEntry.setErrorMessage(null);
                    }
                    executor.execute(monitor, logEntry, baseVars);
                    if ("SUCCESS".equals(logEntry.getStatus())) {
                        break;
                    }
                    if (attempt < maxAttempts) {
                        log.info("Monitor {} attempt {}/{} not successful ({}), retrying...",
                                monitor.getName(), attempt, maxAttempts, logEntry.getStatus());
                    }
                }
            } else {
                logEntry.setStatus("ERROR");
                logEntry.setErrorMessage("不支持的监控类型: " + type);
            }
        } catch (Exception e) {
            long responseTime = System.currentTimeMillis() - logEntry.getExecutedAt().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
            logEntry.setResponseTime((int) responseTime);
            logEntry.setStatus("ERROR");
            logEntry.setErrorMessage(e.getMessage());
            log.error("Monitor execution failed: {}", monitor.getName(), e);
        }

        // Check response time alert
        try {
            responseTimeAlertService.checkAndAlert(logEntry, monitor);
        } catch (Exception e) {
            log.error("Failed to check response time alert for monitor: {}", monitor.getName(), e);
        }

        // Check API Schema alert (HTTP only)
        if ("HTTP".equals(type)) {
            try {
                checkSchemaAlert(logEntry, monitor);
            } catch (Exception e) {
                log.error("Failed to check schema alert for monitor: {}", monitor.getName(), e);
            }
        }

        // Check monitor-level failure alert
        try {
            checkMonitorFailureAlert(logEntry, monitor);
        } catch (Exception e) {
            log.error("Failed to check monitor failure alert: {}", monitor.getName(), e);
        }

        // Truncate large bodies before persisting to keep the DB compact
        logEntry.setRequestBody(truncateBody(logEntry.getRequestBody()));
        logEntry.setResponseBody(truncateBody(logEntry.getResponseBody()));

        // Single write: schemaCheckStatus already resolved above
        executionLogMapper.insert(logEntry);

        return logEntry;
    }

    private void checkMonitorFailureAlert(ExecutionLog logEntry, Monitor monitor) {
        if (monitor.getAlertEnabled() == null || !monitor.getAlertEnabled()) {
            return;
        }

        String configIds = monitor.getAlertConfigIds();
        if (configIds == null || configIds.isEmpty()) {
            // Fall back to template's fallbackChannelIds
            AlertTemplate template = alertTemplateMapper.selectOne(
                    new LambdaQueryWrapper<AlertTemplate>()
                            .eq(AlertTemplate::getTriggerType, "FAIL")
                            .eq(AlertTemplate::getEnabled, true));
            if (template != null && template.getFallbackChannelIds() != null && !template.getFallbackChannelIds().isEmpty()) {
                configIds = template.getFallbackChannelIds();
                log.info("Monitor {} has no alertConfigIds, using fallback channels from template: {}", monitor.getName(), template.getName());
            } else {
                log.debug("Monitor {} has alertEnabled=true but no alertConfigIds and no template fallback, skipping", monitor.getName());
                return;
            }
        }

        boolean isFail = "FAIL".equals(logEntry.getStatus()) || "ERROR".equals(logEntry.getStatus());

        if (isFail) {
            Integer current = monitorConsecutiveFailCounts.getIfPresent(monitor.getId());
            int consecutive = (current != null ? current : 0) + 1;
            monitorConsecutiveFailCounts.put(monitor.getId(), consecutive);

            int threshold = monitor.getAlertConsecutiveCount() != null ? monitor.getAlertConsecutiveCount() : 3;
            if (consecutive >= threshold) {
                log.info("Monitor {} failed {} times consecutively (threshold: {}), sending alert",
                        monitor.getName(), consecutive, threshold);
                alertService.sendAlertByIds(configIds, logEntry, "FAIL");
            }
        } else {
            Integer prevFailCount = monitorConsecutiveFailCounts.getIfPresent(monitor.getId());
            monitorConsecutiveFailCounts.invalidate(monitor.getId());
            // If there were previous failures, send success status to trigger recovery notification
            if (prevFailCount != null && prevFailCount > 0) {
                log.info("Monitor {} recovered after {} failures, checking recovery notification",
                        monitor.getName(), prevFailCount);
                alertService.sendAlertByIds(configIds, logEntry, "FAIL");
            }
        }
    }

    private void checkSchemaAlert(ExecutionLog logEntry, Monitor monitor) {
        if (monitor.getSchemaAlertEnabled() == null || !monitor.getSchemaAlertEnabled()) {
            logEntry.setSchemaCheckStatus("未启用");
            return;
        }
        if (monitor.getExpectedSchemaJson() == null || monitor.getExpectedSchemaJson().isEmpty()) {
            logEntry.setSchemaCheckStatus("未配置Schema");
            return;
        }
        String responseBody = logEntry.getResponseBody();
        if (responseBody == null || responseBody.isEmpty()) {
            logEntry.setSchemaCheckStatus("无响应体");
            return;
        }

        // Only compare if response is valid JSON
        JsonNode responseNode;
        try {
            responseNode = objectMapper.readTree(responseBody);
        } catch (Exception e) {
            log.debug("Response is not valid JSON, skipping schema check for monitor: {}", monitor.getName());
            logEntry.setSchemaCheckStatus("非JSON响应");
            return;
        }
        if (!responseNode.isObject() && !responseNode.isArray()) {
            logEntry.setSchemaCheckStatus("非JSON响应");
            return;
        }

        List<SchemaChange> allChanges = apiSchemaService.detectChanges(monitor.getExpectedSchemaJson(), responseBody);
        log.info("checkSchemaAlert for monitor {}: allChanges.size={}, expectedSchema={}", monitor.getName(), allChanges.size(), monitor.getExpectedSchemaJson());
        if (allChanges.isEmpty()) {
            logEntry.setSchemaCheckStatus("无变更");
            return;
        }

        // Filter by user-selected change types
        Set<String> allowedTypes = new HashSet<>();
        if (monitor.getSchemaAlertChangeTypes() != null && !monitor.getSchemaAlertChangeTypes().isEmpty()) {
            for (String t : monitor.getSchemaAlertChangeTypes().split(",")) {
                allowedTypes.add(t.trim().toUpperCase());
            }
        } else {
            allowedTypes.addAll(Set.of("ADDED", "REMOVED", "MODIFIED", "BREAKING"));
        }

        List<SchemaChange> matchedChanges = new ArrayList<>();
        boolean hasBreaking = false;
        for (SchemaChange change : allChanges) {
            if ("REMOVED".equals(change.getChangeType())) {
                hasBreaking = true;
            }
            if (allowedTypes.contains(change.getChangeType())) {
                matchedChanges.add(change);
            }
        }

        if (hasBreaking && allowedTypes.contains("BREAKING")) {
            for (SchemaChange change : allChanges) {
                if ("REMOVED".equals(change.getChangeType()) && !matchedChanges.contains(change)) {
                    matchedChanges.add(change);
                }
            }
        }

        if (matchedChanges.isEmpty()) {
            logEntry.setSchemaCheckStatus("无匹配变更");
            return;
        }

        // Record the change types on the main log entry
        Set<String> changeTypes = new LinkedHashSet<>();
        for (SchemaChange c : matchedChanges) {
            changeTypes.add(c.getChangeType());
        }
        logEntry.setSchemaCheckStatus(String.join(",", changeTypes));

        String changeDetail = matchedChanges.stream()
            .map(c -> "[" + c.getChangeType() + "] " + c.getDescription())
            .reduce((a, b) -> a + "\n  " + b)
            .orElse("");

        ExecutionLog alertLog = new ExecutionLog();
        alertLog.setMonitorId(monitor.getId());
        alertLog.setMonitorName(monitor.getName());
        alertLog.setGroupId(logEntry.getGroupId());
        alertLog.setExecutionId(logEntry.getExecutionId());
        alertLog.setStatusCode(logEntry.getStatusCode());
        alertLog.setResponseTime(logEntry.getResponseTime());
        alertLog.setErrorMessage("API Schema 变更 (" + matchedChanges.size() + "项):\n  " + changeDetail);
        alertLog.setExecutedAt(logEntry.getExecutedAt());
        alertLog.setStatus("SCHEMA_CHANGE");
        alertLog.setUrl(logEntry.getUrl());
        alertLog.setDomain(logEntry.getDomain());

        String channelIds = monitor.getSchemaAlertChannelIds();
        if (channelIds == null || channelIds.isEmpty()) {
            channelIds = monitor.getResponseTimeAlertConfigIds();
        }
        alertService.sendAlertByIds(channelIds, alertLog, "SCHEMA_CHANGE");
    }
}
