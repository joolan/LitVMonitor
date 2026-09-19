package com.litv.monitor.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.litv.monitor.entity.ExecutionLog;
import com.litv.monitor.entity.GroupMonitor;
import com.litv.monitor.entity.Monitor;
import com.litv.monitor.entity.MonitorGroup;
import com.litv.monitor.mapper.GroupMonitorMapper;
import com.litv.monitor.mapper.MonitorGroupMapper;
import com.litv.monitor.mapper.MonitorMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Slf4j
@Service
@RequiredArgsConstructor
public class GroupExecutionService {

    private final MonitorGroupMapper monitorGroupMapper;
    private final GroupMonitorMapper groupMonitorMapper;
    private final MonitorMapper monitorMapper;
    private final ExecutionService executionService;
    private final VariableEngine variableEngine;
    private final AlertService alertService;
    @Qualifier("taskExecutor")
    private final Executor taskExecutor;

    private final com.github.benmanes.caffeine.cache.Cache<Long, Integer> consecutiveFailCounts =
            com.github.benmanes.caffeine.cache.Caffeine.newBuilder()
                    .expireAfterWrite(1, java.util.concurrent.TimeUnit.HOURS)
                    .maximumSize(500)
                    .build();

    @Async
    public CompletableFuture<List<ExecutionLog>> executeGroup(Long groupId) {
        MonitorGroup group = monitorGroupMapper.selectById(groupId);
        if (group == null || !group.getEnabled()) {
            return CompletableFuture.completedFuture(List.of());
        }

        if (Boolean.TRUE.equals(group.getRunning())) {
            log.warn("Group {} is already running, skipping", group.getName());
            return CompletableFuture.completedFuture(List.of());
        }
        group.setRunning(true);
        monitorGroupMapper.updateById(group);

        try {
            String executionId = UUID.randomUUID().toString();
            log.info("Starting group execution: {} (ID: {})", group.getName(), executionId);

            LambdaQueryWrapper<GroupMonitor> wrapper = new LambdaQueryWrapper<GroupMonitor>()
                    .eq(GroupMonitor::getGroupId, groupId)
                    .orderByAsc(GroupMonitor::getSortOrder);
            List<GroupMonitor> groupMonitors = groupMonitorMapper.selectList(wrapper);

            List<GroupMonitor> parallel = new ArrayList<>();
            List<GroupMonitor> sequential = new ArrayList<>();
            for (GroupMonitor gm : groupMonitors) {
                if (gm.getSortOrder() == null || gm.getSortOrder() == 0) {
                    parallel.add(gm);
                } else {
                    sequential.add(gm);
                }
            }
            sequential.sort(Comparator.comparing(GroupMonitor::getSortOrder));

            List<ExecutionLog> results = new ArrayList<>();
            boolean hasFailure = false;

            if (!parallel.isEmpty()) {
                log.info("Executing {} monitors in parallel (sortOrder=0) in group: {}",
                        parallel.size(), group.getName());

                List<CompletableFuture<ExecutionLog>> futures = parallel.stream()
                        .map(gm -> {
                            Monitor monitor = monitorMapper.selectById(gm.getMonitorId());
                            if (monitor == null || !monitor.getEnabled()) {
                                return null;
                            }
                            log.info("Submitting parallel monitor: {} in group: {}",
                                    monitor.getName(), group.getName());
                            return CompletableFuture.supplyAsync(
                                    () -> executionService.executeMonitor(monitor, groupId, executionId),
                                    taskExecutor);
                        })
                        .filter(Objects::nonNull)
                        .toList();

                if (!futures.isEmpty()) {
                    CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();

                    for (CompletableFuture<ExecutionLog> f : futures) {
                        ExecutionLog result = f.join();
                        results.add(result);
                        if ("FAIL".equals(result.getStatus()) || "ERROR".equals(result.getStatus())) {
                            hasFailure = true;
                        }
                    }
                }
            }

            if (!sequential.isEmpty()) {
                log.info("Executing {} monitors sequentially (sortOrder>0) in group: {}",
                        sequential.size(), group.getName());

                for (GroupMonitor gm : sequential) {
                    Monitor monitor = monitorMapper.selectById(gm.getMonitorId());
                    if (monitor == null || !monitor.getEnabled()) {
                        continue;
                    }

                    log.info("Executing sequential monitor: {} (sortOrder={}) in group: {}",
                            monitor.getName(), gm.getSortOrder(), group.getName());
                    ExecutionLog result = executionService.executeMonitor(monitor, groupId, executionId);
                    results.add(result);

                    if ("FAIL".equals(result.getStatus()) || "ERROR".equals(result.getStatus())) {
                        hasFailure = true;
                        if (!Boolean.TRUE.equals(gm.getContinueOnFail())) {
                            log.info("Monitor {} failed, stopping sequential execution in group {}",
                                    monitor.getName(), group.getName());
                            break;
                        }
                    }
                }
            }

            if (Boolean.TRUE.equals(group.getAlertOnFail())) {
                // Count actual failures
                int failCount = 0;
                for (ExecutionLog r : results) {
                    if ("FAIL".equals(r.getStatus()) || "ERROR".equals(r.getStatus())) {
                        failCount++;
                    }
                }
                int totalExecuted = results.size();

                // Determine if group is considered failed based on criteria
                boolean groupFailed = false;
                String criteriaType = group.getFailCriteriaType() != null ? group.getFailCriteriaType() : "ANY";
                switch (criteriaType) {
                    case "COUNT":
                        int countThreshold = group.getFailCountThreshold() != null ? group.getFailCountThreshold() : 1;
                        groupFailed = failCount >= countThreshold;
                        break;
                    case "PERCENT":
                        int percentThreshold = group.getFailPercentThreshold() != null ? group.getFailPercentThreshold() : 50;
                        groupFailed = totalExecuted > 0 && (failCount * 100 / totalExecuted) >= percentThreshold;
                        break;
                    case "ANY":
                    default:
                        groupFailed = hasFailure;
                        break;
                }

                if (groupFailed) {
                    Integer current = consecutiveFailCounts.getIfPresent(groupId);
                    int consecutive = (current != null ? current : 0) + 1;
                    consecutiveFailCounts.put(groupId, consecutive);
                    log.debug("Group {} consecutive failure count: {}/{} (criteria={}, failCount={}/{})",
                            group.getName(), consecutive, group.getFailThreshold(), criteriaType, failCount, totalExecuted);

                    int threshold = group.getFailThreshold() != null ? group.getFailThreshold() : 1;
                    if (consecutive >= threshold) {
                        log.info("Group {} failed {} times consecutively (threshold: {}, criteria: {}, fails: {}/{}), sending alert",
                                group.getName(), consecutive, threshold, criteriaType, failCount, totalExecuted);
                        consecutiveFailCounts.put(groupId, 0);
                        alertService.sendGroupAlert(groupId, group.getName(), consecutive, group.getAlertConfigIds(), executionId);
                    }
                } else {
                    consecutiveFailCounts.invalidate(groupId);
                }
            }

            log.info("Group execution completed: {} with {} results (failure={}, parallel={}, sequential={})",
                    group.getName(), results.size(), hasFailure, parallel.size(), sequential.size());
            return CompletableFuture.completedFuture(results);
        } finally {
            group.setRunning(false);
            monitorGroupMapper.updateById(group);
        }
    }
}
