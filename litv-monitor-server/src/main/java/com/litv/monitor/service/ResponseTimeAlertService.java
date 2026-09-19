package com.litv.monitor.service;

import com.litv.monitor.entity.ExecutionLog;
import com.litv.monitor.entity.Monitor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResponseTimeAlertService {

    private final AlertService alertService;

    private final com.github.benmanes.caffeine.cache.Cache<Long, Integer> consecutiveSlowCounts =
            com.github.benmanes.caffeine.cache.Caffeine.newBuilder()
                    .expireAfterWrite(1, TimeUnit.HOURS)
                    .maximumSize(500)
                    .build();

    public void checkAndAlert(ExecutionLog logEntry, Monitor monitor) {
        if (monitor.getResponseTimeAlertEnabled() == null || !monitor.getResponseTimeAlertEnabled()) {
            return;
        }

        Integer threshold = monitor.getResponseTimeThreshold();
        if (threshold == null || threshold <= 0) {
            return;
        }

        Integer requiredCount = monitor.getResponseTimeConsecutiveCount();
        if (requiredCount == null || requiredCount <= 0) {
            requiredCount = 1;
        }

        Integer responseTime = logEntry.getResponseTime();
        if (responseTime == null) {
            resetCount(monitor.getId());
            return;
        }

        if (responseTime > threshold) {
            Integer current = consecutiveSlowCounts.getIfPresent(monitor.getId());
            int currentCount = (current != null ? current : 0) + 1;
            consecutiveSlowCounts.put(monitor.getId(), currentCount);
            log.debug("Monitor {} response time {}ms > {}ms threshold, consecutive: {}/{}",
                    monitor.getName(), responseTime, threshold, currentCount, requiredCount);

            if (currentCount >= requiredCount) {
                log.info("Monitor {} triggered response time alert ({}ms > {}ms, {} consecutive)",
                        monitor.getName(), responseTime, threshold, currentCount);
                consecutiveSlowCounts.put(monitor.getId(), 0);
                alertService.sendResponseTimeAlert(logEntry, monitor, responseTime, threshold);
            }
        } else {
            resetCount(monitor.getId());
        }
    }

    private void resetCount(Long monitorId) {
        consecutiveSlowCounts.invalidate(monitorId);
    }
}
