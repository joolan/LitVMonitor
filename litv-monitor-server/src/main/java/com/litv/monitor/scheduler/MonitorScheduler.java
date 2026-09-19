package com.litv.monitor.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.litv.monitor.entity.InspectionConfig;
import com.litv.monitor.entity.MonitorGroup;
import com.litv.monitor.mapper.InspectionConfigMapper;
import com.litv.monitor.mapper.MonitorGroupMapper;
import com.litv.monitor.service.GroupExecutionService;
import com.litv.monitor.service.InspectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class MonitorScheduler {

    private final MonitorGroupMapper monitorGroupMapper;
    private final GroupExecutionService groupExecutionService;
    private final InspectionService inspectionService;

    private final com.github.benmanes.caffeine.cache.Cache<Long, LocalDateTime> lastExecutionTimes =
            com.github.benmanes.caffeine.cache.Caffeine.newBuilder()
                    .expireAfterWrite(24, TimeUnit.HOURS)
                    .maximumSize(200)
                    .build();

    private final com.github.benmanes.caffeine.cache.Cache<Long, LocalDateTime> lastInspectionTimes =
            com.github.benmanes.caffeine.cache.Caffeine.newBuilder()
                    .expireAfterWrite(24, TimeUnit.HOURS)
                    .maximumSize(100)
                    .build();

    @Scheduled(fixedRate = 60000)
    public void executeGroups() {
        log.debug("Checking for groups to execute...");
        LocalDateTime now = LocalDateTime.now();

        List<MonitorGroup> groups = monitorGroupMapper.selectList(
                new LambdaQueryWrapper<MonitorGroup>()
                        .eq(MonitorGroup::getEnabled, true)
                        .isNotNull(MonitorGroup::getCronExpression)
        );

        for (MonitorGroup group : groups) {
            try {
                if (shouldExecuteGroup(group, now)) {
                    log.info("Executing scheduled group: {} (cron: {})", group.getName(), group.getCronExpression());
                    groupExecutionService.executeGroup(group.getId());
                    lastExecutionTimes.put(group.getId(), now);
                }
            } catch (Exception e) {
                log.error("Failed to execute group: {}", group.getName(), e);
            }
        }

        // 检查巡检定时任务
        executeInspections(now);
    }

    private void executeInspections(LocalDateTime now) {
        List<InspectionConfig> configs = inspectionService.listEnabledWithCron();
        for (InspectionConfig config : configs) {
            try {
                if (shouldExecuteInspection(config, now)) {
                    if (!inspectionService.tryStartInspection()) {
                        log.warn("Skipping scheduled inspection: {} - another inspection is running", config.getName());
                        continue;
                    }
                    log.info("Executing scheduled inspection: {} (cron: {})", config.getName(), config.getScheduleCron());
                    inspectionService.runInspection(config.getId());
                    lastInspectionTimes.put(config.getId(), now);
                }
            } catch (Exception e) {
                log.error("Failed to execute inspection: {}", config.getName(), e);
            }
        }
    }

    private boolean shouldExecuteInspection(InspectionConfig config, LocalDateTime now) {
        String cron = config.getScheduleCron();
        if (cron == null || cron.isEmpty()) return false;

        LocalDateTime lastExec = lastInspectionTimes.getIfPresent(config.getId());
        if (lastExec != null && lastExec.toLocalDate().equals(now.toLocalDate()) &&
            lastExec.getHour() == now.getHour() && lastExec.getMinute() == now.getMinute()) {
            return false;
        }

        String[] parts = cron.split(" ");
        if (parts.length < 5) return false;

        String scheduleType = inferSimpleScheduleType(parts);
        switch (scheduleType) {
            case "interval":
                return checkInterval(parts, now, lastExec);
            case "daily":
                return checkDaily(parts, now);
            case "weekly":
                return checkWeekly(parts, now);
            default:
                return false;
        }
    }

    private String inferSimpleScheduleType(String[] parts) {
        if (parts[2].contains("*") && parts[3].contains("*")) {
            return "interval";
        } else if (parts[3].contains("*")) {
            return "daily";
        } else if (parts[4].contains("*")) {
            return "weekly";
        }
        return "interval";
    }

    private boolean shouldExecuteGroup(MonitorGroup group, LocalDateTime now) {
        String cron = group.getCronExpression();
        if (cron == null || cron.isEmpty()) return false;

        LocalDateTime lastExec = lastExecutionTimes.getIfPresent(group.getId());
        if (lastExec != null && lastExec.toLocalDate().equals(now.toLocalDate()) &&
            lastExec.getHour() == now.getHour() && lastExec.getMinute() == now.getMinute()) {
            return false;
        }

        String[] parts = cron.split(" ");
        if (parts.length < 6) return false;

        String scheduleType = group.getScheduleType();
        if (scheduleType == null) {
            scheduleType = inferScheduleType(parts);
        }

        switch (scheduleType) {
            case "interval":
                return checkInterval(parts, now, lastExec);
            case "daily":
                return checkDaily(parts, now);
            case "weekly":
                return checkWeekly(parts, now);
            default:
                return false;
        }
    }

    private String inferScheduleType(String[] parts) {
        if (parts[2].contains("*") && parts[3].contains("*") && parts[4].contains("*")) {
            return "interval";
        } else if (parts[3].contains("*") && parts[4].contains("*")) {
            return "daily";
        } else if (parts[3].contains("*")) {
            return "weekly";
        }
        return "interval";
    }

    private boolean checkInterval(String[] parts, LocalDateTime now, LocalDateTime lastExec) {
        int interval = 5;
        String minutePart = parts[1];
        if (minutePart.startsWith("*/")) {
            try {
                interval = Integer.parseInt(minutePart.substring(2));
            } catch (NumberFormatException e) {
                return false;
            }
        }

        if (lastExec == null) return true;
        long minutesSinceLast = ChronoUnit.MINUTES.between(
                lastExec.truncatedTo(ChronoUnit.MINUTES),
                now.truncatedTo(ChronoUnit.MINUTES)
        );
        return minutesSinceLast >= interval;
    }

    private boolean checkDaily(String[] parts, LocalDateTime now) {
        try {
            int hour = Integer.parseInt(parts[2]);
            int minute = Integer.parseInt(parts[1]);
            return now.getHour() == hour && now.getMinute() == minute;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private boolean checkWeekly(String[] parts, LocalDateTime now) {
        try {
            int hour = Integer.parseInt(parts[2]);
            int minute = Integer.parseInt(parts[1]);
            int dayOfWeek = Integer.parseInt(parts[5]);
            DayOfWeek currentDay = now.getDayOfWeek();
            int javaDay = dayOfWeek == 0 ? 7 : dayOfWeek;
            return now.getHour() == hour && now.getMinute() == minute &&
                   currentDay.getValue() == javaDay;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
