package com.litv.monitor.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.litv.monitor.entity.*;
import com.litv.monitor.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
@RequiredArgsConstructor
public class InspectionService {

    private static final AtomicBoolean globalRunning = new AtomicBoolean(false);

    private final InspectionConfigMapper configMapper;
    private final InspectionHistoryMapper historyMapper;
    private final InspectionDetailMapper detailMapper;
    private final MonitorMapper monitorMapper;
    private final ExecutionService executionService;

    public boolean tryStartInspection() {
        return globalRunning.compareAndSet(false, true);
    }

    public void finishInspection() {
        globalRunning.set(false);
    }

    public List<InspectionConfig> listConfigs() {
        return configMapper.selectList(
            new LambdaQueryWrapper<InspectionConfig>()
                .orderByDesc(InspectionConfig::getCreatedAt)
        );
    }

    public List<InspectionConfig> listEnabledWithCron() {
        return configMapper.selectList(
            new LambdaQueryWrapper<InspectionConfig>()
                .eq(InspectionConfig::getEnabled, true)
                .isNotNull(InspectionConfig::getScheduleCron)
                .ne(InspectionConfig::getScheduleCron, "")
        );
    }

    public InspectionConfig getConfig(Long id) {
        return configMapper.selectById(id);
    }

    public InspectionConfig createConfig(InspectionConfig config) {
        config.setCreatedAt(LocalDateTime.now());
        config.setUpdatedAt(LocalDateTime.now());
        configMapper.insert(config);
        return config;
    }

    public InspectionConfig updateConfig(InspectionConfig config) {
        config.setUpdatedAt(LocalDateTime.now());
        configMapper.updateById(config);
        return config;
    }

    public void deleteConfig(Long id) {
        configMapper.deleteById(id);
        List<InspectionHistory> histories = historyMapper.selectList(
            new LambdaQueryWrapper<InspectionHistory>()
                .eq(InspectionHistory::getConfigId, id)
        );
        for (InspectionHistory h : histories) {
            detailMapper.delete(
                new LambdaQueryWrapper<InspectionDetail>()
                    .eq(InspectionDetail::getHistoryId, h.getId())
            );
        }
        historyMapper.delete(
            new LambdaQueryWrapper<InspectionHistory>()
                .eq(InspectionHistory::getConfigId, id)
        );
    }

    public List<InspectionHistory> listHistory(Long configId) {
        return historyMapper.selectList(
            new LambdaQueryWrapper<InspectionHistory>()
                .eq(InspectionHistory::getConfigId, configId)
                .orderByDesc(InspectionHistory::getStartedAt)
        );
    }

    public InspectionHistory getHistoryById(Long id) {
        return historyMapper.selectById(id);
    }

    public List<InspectionDetail> getHistoryDetails(Long historyId) {
        return detailMapper.selectList(
            new LambdaQueryWrapper<InspectionDetail>()
                .eq(InspectionDetail::getHistoryId, historyId)
                .orderByAsc(InspectionDetail::getMonitorName)
        );
    }

    @Async
    public void runInspection(Long configId) {
        InspectionConfig config = configMapper.selectById(configId);
        if (config == null) {
            log.error("Inspection config not found: {}", configId);
            finishInspection();
            return;
        }

        InspectionHistory history = new InspectionHistory();
        history.setConfigId(configId);
        history.setStatus("RUNNING");
        history.setStartedAt(LocalDateTime.now());
        try {
            historyMapper.insert(history);
        } catch (Exception e) {
            log.error("Failed to create inspection history", e);
            finishInspection();
            return;
        }

        long startTime = System.currentTimeMillis();

        try {
            List<Monitor> monitors = resolveMonitors(config);

            if (monitors.isEmpty()) {
                history.setStatus("COMPLETED");
                history.setTotalMonitors(0);
                history.setSuccessCount(0);
                history.setFailCount(0);
                history.setDurationMs(0);
                history.setCompletedAt(LocalDateTime.now());
                history.setReportContent("## 巡检报告\n\n未配置监控项，跳过巡检。");
                historyMapper.updateById(history);
                config.setLastRunAt(LocalDateTime.now());
                configMapper.updateById(config);
                return;
            }

            history.setTotalMonitors(monitors.size());
            int successCount = 0;
            int failCount = 0;
            List<InspectionDetail> insertedDetails = new ArrayList<>();

            for (Monitor monitor : monitors) {
                InspectionDetail detail = new InspectionDetail();
                detail.setHistoryId(history.getId());
                detail.setMonitorId(monitor.getId());
                detail.setMonitorName(monitor.getName());
                detail.setMonitorUrl(monitor.getUrl());
                detail.setCreatedAt(LocalDateTime.now());

                try {
                    long monitorStart = System.currentTimeMillis();
                    ExecutionLog result = executionService.executeMonitor(monitor, null);
                    long responseTime = System.currentTimeMillis() - monitorStart;

                    detail.setResponseTime((int) responseTime);
                    detail.setStatusCode(result.getStatusCode());

                    if (result.getSchemaCheckStatus() != null) {
                        detail.setSchemaChangeInfo(result.getSchemaCheckStatus());
                    }

                    if ("SUCCESS".equals(result.getStatus())) {
                        detail.setStatus("SUCCESS");
                        successCount++;
                    } else {
                        detail.setStatus("FAIL");
                        detail.setErrorMessage(result.getErrorMessage());
                        failCount++;
                    }
                } catch (Exception e) {
                    detail.setStatus("FAIL");
                    detail.setErrorMessage(e.getMessage());
                    failCount++;
                }

                detailMapper.insert(detail);
                insertedDetails.add(detail);
            }

            long duration = System.currentTimeMillis() - startTime;

            // Count schema changes from details
            int schemaChangeCount = 0;
            for (InspectionDetail d : insertedDetails) {
                if (d.getSchemaChangeInfo() != null
                    && !d.getSchemaChangeInfo().equals("无变更")
                    && !d.getSchemaChangeInfo().equals("未启用")
                    && !d.getSchemaChangeInfo().equals("未配置Schema")
                    && !d.getSchemaChangeInfo().equals("非JSON响应")
                    && !d.getSchemaChangeInfo().equals("无响应体")) {
                    schemaChangeCount++;
                }
            }

            history.setSuccessCount(successCount);
            history.setFailCount(failCount);
            history.setSchemaChangeCount(schemaChangeCount);
            history.setDurationMs((int) duration);
            history.setCompletedAt(LocalDateTime.now());
            history.setStatus("COMPLETED");
            history.setReportContent(buildReportContent(history, monitors.size(), successCount, failCount, duration));
            historyMapper.updateById(history);

            config.setLastRunAt(LocalDateTime.now());
            config.setUpdatedAt(LocalDateTime.now());
            configMapper.updateById(config);

            log.info("Inspection completed: config={}, total={}, success={}, fail={}, duration={}ms",
                configId, monitors.size(), successCount, failCount, duration);
        } catch (Exception e) {
            history.setStatus("FAILED");
            history.setCompletedAt(LocalDateTime.now());
            history.setReportContent("Inspection failed: " + e.getMessage());
            historyMapper.updateById(history);
            log.error("Inspection failed", e);
        } finally {
            finishInspection();
        }
    }

    private List<Monitor> resolveMonitors(InspectionConfig config) {
        if (config.getMonitorIds() != null && !config.getMonitorIds().isEmpty()) {
            String[] ids = config.getMonitorIds().split(",");
            List<Long> monitorIds = new ArrayList<>();
            for (String id : ids) {
                try {
                    monitorIds.add(Long.parseLong(id.trim()));
                } catch (NumberFormatException e) {
                    // skip invalid
                }
            }
            if (!monitorIds.isEmpty()) {
                return monitorMapper.selectBatchIds(monitorIds);
            }
        }
        return monitorMapper.selectList(
            new LambdaQueryWrapper<Monitor>()
                .eq(Monitor::getEnabled, true)
        );
    }

    private String buildReportContent(InspectionHistory history, int total, int success, int fail, long duration) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 巡检报告\n\n");
        sb.append("- 巡检时间: ").append(history.getStartedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("\n");
        sb.append("- 总监控项: ").append(total).append("\n");
        sb.append("- 成功: ").append(success).append("\n");
        sb.append("- 失败: ").append(fail).append("\n");
        sb.append("- 耗时: ").append(duration).append("ms\n\n");

        double successRate = total > 0 ? (double) success / total * 100 : 0;
        sb.append("## 总结\n\n");
        sb.append("- 成功率: ").append(String.format("%.1f%%", successRate)).append("\n");

        if (fail > 0) {
            sb.append("\n## 失败项\n\n");
            List<InspectionDetail> details = detailMapper.selectList(
                new LambdaQueryWrapper<InspectionDetail>()
                    .eq(InspectionDetail::getHistoryId, history.getId())
                    .eq(InspectionDetail::getStatus, "FAIL")
            );
            for (InspectionDetail d : details) {
                sb.append("- ").append(d.getMonitorName()).append(": ").append(d.getErrorMessage()).append("\n");
            }
        }

        // Schema change summary
        List<InspectionDetail> allDetails = detailMapper.selectList(
            new LambdaQueryWrapper<InspectionDetail>()
                .eq(InspectionDetail::getHistoryId, history.getId())
        );
        long schemaChangeCount = allDetails.stream()
            .filter(d -> d.getSchemaChangeInfo() != null
                && !d.getSchemaChangeInfo().equals("无变更")
                && !d.getSchemaChangeInfo().equals("未启用")
                && !d.getSchemaChangeInfo().equals("未配置Schema"))
            .count();
        if (schemaChangeCount > 0) {
            sb.append("\n## Schema 变更\n\n");
            sb.append("- 检测到变更的监控项: ").append(schemaChangeCount).append("\n\n");
            for (InspectionDetail d : allDetails) {
                if (d.getSchemaChangeInfo() != null
                    && !d.getSchemaChangeInfo().equals("无变更")
                    && !d.getSchemaChangeInfo().equals("未启用")
                    && !d.getSchemaChangeInfo().equals("未配置Schema")) {
                    sb.append("- ").append(d.getMonitorName()).append(": ").append(d.getSchemaChangeInfo()).append("\n");
                }
            }
        }

        return sb.toString();
    }
}
