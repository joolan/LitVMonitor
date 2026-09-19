package com.litv.monitor.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.litv.monitor.entity.ReminderTask;
import com.litv.monitor.mapper.ReminderTaskMapper;
import com.litv.monitor.service.ReminderNotifyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReminderScheduler {

    private final ReminderTaskMapper reminderTaskMapper;
    private final ReminderNotifyService notifyService;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // 同一"到期时间点(occurrence)"只通知一次，避免重复；24h 后仍未处理会再次提醒
    private final Cache<String, LocalDateTime> remindCache = Caffeine.newBuilder()
            .expireAfterWrite(24, TimeUnit.HOURS)
            .maximumSize(2000)
            .build();

    @Scheduled(fixedRate = 60000)
    public void checkReminders() {
        LocalDateTime now = LocalDateTime.now();
        String nowStr = now.format(FMT);

        List<ReminderTask> tasks = reminderTaskMapper.selectList(
                new LambdaQueryWrapper<ReminderTask>()
                        .eq(ReminderTask::getEnabled, true)
                        .eq(ReminderTask::getCompleted, false));

        for (ReminderTask task : tasks) {
            try {
                processTask(task, now, nowStr);
            } catch (Exception e) {
                log.error("Failed to process reminder: {}", task.getTitle(), e);
            }
        }
    }

    private void processTask(ReminderTask task, LocalDateTime now, String nowStr) {
        String nextDueStr = task.getNextDueAt();
        if (nextDueStr == null || nextDueStr.isEmpty()) return;

        LocalDateTime nextDue;
        try {
            nextDue = LocalDateTime.parse(nextDueStr, FMT);
        } catch (Exception e) {
            log.warn("Reminder {} has invalid nextDueAt: {}", task.getId(), nextDueStr);
            return;
        }

        // 提前提醒（同一到期点只发一次）
        if (Boolean.TRUE.equals(task.getAdvanceEnabled()) && shouldAdvanceRemind(task, now, nextDue)) {
            String cacheKey = task.getId() + ":advance:" + nextDueStr;
            if (remindCache.getIfPresent(cacheKey) == null) {
                log.info("Sending advance reminder for task: {}", task.getTitle());
                notifyService.sendReminder(task, "advance");
                task.setLastRemindedAt(nowStr);
                reminderTaskMapper.updateById(task);
                remindCache.put(cacheKey, now);
            }
        }

        // 到期提醒（同一到期点只发一次）。
        // 注意：这里只负责"通知"，不再自动完成或推进到下一次；
        // 是否完成由用户手动操作（"完成"）决定，未处理的任务会保持"已过期"状态。
        if (!now.isBefore(nextDue)) {
            String cacheKey = task.getId() + ":due:" + nextDueStr;
            if (remindCache.getIfPresent(cacheKey) == null) {
                log.info("Sending due reminder for task: {}", task.getTitle());
                notifyService.sendReminder(task, "due");
                task.setLastRemindedAt(nowStr);
                reminderTaskMapper.updateById(task);
                remindCache.put(cacheKey, now);
            }
        }
    }

    private boolean shouldAdvanceRemind(ReminderTask task, LocalDateTime now, LocalDateTime nextDue) {
        if (task.getAdvanceDays() != null && task.getAdvanceDays() > 0) {
            LocalDateTime advancePoint = nextDue.minusDays(task.getAdvanceDays());
            return !now.isBefore(advancePoint);
        }
        if (task.getAdvanceMinutes() != null && task.getAdvanceMinutes() > 0) {
            LocalDateTime advancePoint = nextDue.minusMinutes(task.getAdvanceMinutes());
            return !now.isBefore(advancePoint);
        }
        return false;
    }
}
