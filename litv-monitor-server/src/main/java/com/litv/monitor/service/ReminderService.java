package com.litv.monitor.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.litv.monitor.dto.ReminderTaskDTO;
import com.litv.monitor.entity.ReminderTask;
import com.litv.monitor.enums.ReminderCategory;
import com.litv.monitor.mapper.ReminderTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReminderService {

    private final ReminderTaskMapper reminderTaskMapper;
    private final ObjectMapper objectMapper;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public Page<ReminderTask> listPage(String username, String category, String status,
                                        int pageNum, int pageSize) {
        LambdaQueryWrapper<ReminderTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ReminderTask::getUsername, username);
        if (category != null && !category.isEmpty()) {
            wrapper.eq(ReminderTask::getCategory, category);
        }
        wrapper.orderByDesc(ReminderTask::getCreatedAt);
        Page<ReminderTask> page = reminderTaskMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);

        // status filtering is done in-memory since nextDueAt comparison needs LocalDateTime
        if (status != null && !status.isEmpty() && !"all".equals(status)) {
            List<ReminderTask> filtered = new ArrayList<>();
            LocalDateTime now = LocalDateTime.now();
            for (ReminderTask task : page.getRecords()) {
                if (Boolean.TRUE.equals(task.getCompleted())) {
                    if ("completed".equals(status)) filtered.add(task);
                    continue;
                }
                String nextDueStr = task.getNextDueAt();
                if (nextDueStr == null || nextDueStr.isEmpty()) {
                    if ("normal".equals(status)) filtered.add(task);
                    continue;
                }
                LocalDateTime nextDue = LocalDateTime.parse(nextDueStr, FMT);
                switch (status) {
                    case "overdue" -> {
                        if (nextDue.isBefore(now)) filtered.add(task);
                    }
                    case "today" -> {
                        if (!nextDue.isBefore(now) && nextDue.toLocalDate().equals(now.toLocalDate())) filtered.add(task);
                    }
                    case "upcoming" -> {
                        if (nextDue.toLocalDate().equals(now.toLocalDate().plusDays(7))
                            && nextDue.isAfter(now)) filtered.add(task);
                    }
                    case "normal" -> {
                        if (nextDue.isAfter(now.plusDays(7))) filtered.add(task);
                    }
                }
            }
            page.setRecords(filtered);
            page.setTotal(filtered.size());
        }
        return page;
    }

    public ReminderTask getById(Long id, String username) {
        return reminderTaskMapper.selectOne(
                new LambdaQueryWrapper<ReminderTask>()
                        .eq(ReminderTask::getId, id)
                        .eq(ReminderTask::getUsername, username)
        );
    }

    public ReminderTask create(ReminderTaskDTO dto, String username) {
        ReminderTask task = new ReminderTask();
        task.setUsername(username);
        task.setTitle(dto.getTitle());
        task.setDescription(dto.getDescription());
        task.setCategory(dto.getCategory() != null ? dto.getCategory() : "other");
        task.setDueDate(dto.getDueDate());
        task.setRecurrenceType(dto.getRecurrenceType() != null ? dto.getRecurrenceType() : "once");
        task.setRecurrenceConfig(dto.getRecurrenceConfig());
        task.setAdvanceEnabled(dto.getAdvanceEnabled() != null ? dto.getAdvanceEnabled() : true);
        task.setAdvanceMinutes(dto.getAdvanceMinutes() != null ? dto.getAdvanceMinutes() : 60);
        task.setAdvanceDays(dto.getAdvanceDays() != null ? dto.getAdvanceDays() : 0);
        task.setAlertChannelIds(dto.getAlertChannelIds());
        task.setEnabled(dto.getEnabled() != null ? dto.getEnabled() : true);
        task.setCompleted(false);

        // Calculate initial nextDueAt
        task.setNextDueAt(calculateInitialNextDue(task));

        LocalDateTime now = LocalDateTime.now();
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        reminderTaskMapper.insert(task);
        return task;
    }

    public ReminderTask update(Long id, ReminderTaskDTO dto, String username) {
        ReminderTask task = getById(id, username);
        if (task == null) return null;

        if (dto.getTitle() != null) task.setTitle(dto.getTitle());
        if (dto.getDescription() != null) task.setDescription(dto.getDescription());
        if (dto.getCategory() != null) task.setCategory(dto.getCategory());
        if (dto.getDueDate() != null) task.setDueDate(dto.getDueDate());
        if (dto.getRecurrenceType() != null) task.setRecurrenceType(dto.getRecurrenceType());
        if (dto.getRecurrenceConfig() != null) task.setRecurrenceConfig(dto.getRecurrenceConfig());
        if (dto.getAdvanceEnabled() != null) task.setAdvanceEnabled(dto.getAdvanceEnabled());
        if (dto.getAdvanceMinutes() != null) task.setAdvanceMinutes(dto.getAdvanceMinutes());
        if (dto.getAdvanceDays() != null) task.setAdvanceDays(dto.getAdvanceDays());
        if (dto.getAlertChannelIds() != null) task.setAlertChannelIds(dto.getAlertChannelIds());
        if (dto.getEnabled() != null) task.setEnabled(dto.getEnabled());

        // Recalculate nextDueAt when time config changes
        if (dto.getDueDate() != null || dto.getRecurrenceType() != null || dto.getRecurrenceConfig() != null) {
            task.setCompleted(false);
            task.setNextDueAt(calculateInitialNextDue(task));
        }

        task.setUpdatedAt(LocalDateTime.now());
        reminderTaskMapper.updateById(task);
        return task;
    }

    public boolean delete(Long id, String username) {
        return reminderTaskMapper.delete(
                new LambdaQueryWrapper<ReminderTask>()
                        .eq(ReminderTask::getId, id)
                        .eq(ReminderTask::getUsername, username)
        ) > 0;
    }

    public ReminderTask complete(Long id, String username) {
        ReminderTask task = getById(id, username);
        if (task == null) return null;

        if ("once".equals(task.getRecurrenceType())) {
            task.setCompleted(true);
            task.setNextDueAt(null);
        } else {
            // Periodic task: advance to next occurrence
            LocalDateTime nextDue = calculateNextOccurrence(task);
            if (nextDue != null) {
                task.setNextDueAt(nextDue.format(FMT));
            } else {
                task.setCompleted(true);
                task.setNextDueAt(null);
            }
        }

        task.setUpdatedAt(LocalDateTime.now());
        reminderTaskMapper.updateById(task);
        return task;
    }

    public ReminderTask snooze(Long id, int minutes, String username) {
        ReminderTask task = getById(id, username);
        if (task == null) return null;

        LocalDateTime now = LocalDateTime.now();
        task.setLastSnoozedAt(now.format(FMT));

        LocalDateTime currentDue = task.getNextDueAt() != null
                ? LocalDateTime.parse(task.getNextDueAt(), FMT) : now;
        task.setNextDueAt(currentDue.plusMinutes(minutes).format(FMT));

        task.setUpdatedAt(now);
        reminderTaskMapper.updateById(task);
        return task;
    }

    public ReminderTask toggleEnabled(Long id, boolean enabled, String username) {
        ReminderTask task = getById(id, username);
        if (task == null) return null;
        task.setEnabled(enabled);
        task.setUpdatedAt(LocalDateTime.now());
        reminderTaskMapper.updateById(task);
        return task;
    }

    public List<String> previewReminders(String dueDate, String recurrenceType, String recurrenceConfig, int count) {
        if (dueDate == null || dueDate.isEmpty() || count <= 0) return Collections.emptyList();
        if ("once".equals(recurrenceType)) {
            return List.of(dueDate);
        }

        List<String> results = new ArrayList<>();
        LocalDateTime base = LocalDateTime.parse(dueDate, FMT);
        LocalDateTime current = base;
        LocalDateTime now = LocalDateTime.now();

        // Build a temporary ReminderTask for calculation
        ReminderTask tempTask = new ReminderTask();
        tempTask.setDueDate(dueDate);
        tempTask.setRecurrenceType(recurrenceType);
        tempTask.setRecurrenceConfig(recurrenceConfig);

        for (int i = 0; i < count; i++) {
            LocalDateTime next = calculateNextOccurrenceFrom(tempTask, current);
            if (next == null) break;
            results.add(next.format(FMT));
            current = next;
        }
        return results;
    }

    private LocalDateTime calculateNextOccurrenceFrom(ReminderTask task, LocalDateTime after) {
        LocalDateTime base = LocalDateTime.parse(task.getDueDate(), FMT);
        String configJson = task.getRecurrenceConfig();
        Map<String, Object> config = parseConfig(configJson);

        switch (task.getRecurrenceType()) {
            case "daily" -> {
                LocalDateTime next = base.plusDays(1);
                while (next.isBefore(after) || next.isEqual(after)) next = next.plusDays(1);
                return next;
            }
            case "weekly" -> {
                List<Integer> daysOfWeek = getConfigIntList(config, "daysOfWeek");
                if (daysOfWeek.isEmpty()) {
                    LocalDateTime next = base.plusWeeks(1);
                    while (next.isBefore(after) || next.isEqual(after)) next = next.plusWeeks(1);
                    return next;
                }
                LocalDateTime candidate = after;
                for (int i = 0; i < 365; i++) {
                    candidate = candidate.plusDays(1);
                    int dayNum = candidate.getDayOfWeek().getValue();
                    if (daysOfWeek.contains(dayNum) && candidate.isAfter(after)) {
                        return candidate.withHour(base.getHour()).withMinute(base.getMinute()).withSecond(base.getSecond());
                    }
                }
                return null;
            }
            case "monthly" -> {
                Integer dayOfMonth = getConfigInteger(config, "dayOfMonth");
                if (dayOfMonth == null) dayOfMonth = base.getDayOfMonth();
                java.time.YearMonth nextMonth = java.time.YearMonth.from(after);
                LocalDateTime next = nextMonth.atDay(Math.min(dayOfMonth, nextMonth.lengthOfMonth())).atTime(base.toLocalTime());
                if (!next.isAfter(after)) {
                    nextMonth = java.time.YearMonth.from(next.plusMonths(1));
                    next = nextMonth.atDay(Math.min(dayOfMonth, nextMonth.lengthOfMonth())).atTime(base.toLocalTime());
                }
                return next;
            }
            case "yearly" -> {
                Integer month = getConfigInteger(config, "month");
                Integer day = getConfigInteger(config, "dayOfMonth");
                if (month == null) month = base.getMonthValue();
                if (day == null) day = base.getDayOfMonth();
                java.time.YearMonth ym = java.time.YearMonth.of(after.getYear(), month);
                LocalDateTime next = ym.atDay(Math.min(day, ym.lengthOfMonth())).atTime(base.toLocalTime());
                if (!next.isAfter(after)) {
                    ym = java.time.YearMonth.of(after.getYear() + 1, month);
                    next = ym.atDay(Math.min(day, ym.lengthOfMonth())).atTime(base.toLocalTime());
                }
                return next;
            }
            default -> {
                return null;
            }
        }
    }

    public Map<String, Object> getDashboardSummary(String username) {
        Map<String, Object> summary = new HashMap<>();
        LocalDateTime now = LocalDateTime.now();
        String nowStr = now.format(FMT);
        String todayEnd = now.toLocalDate().atTime(23, 59, 59).format(FMT);

        // Overdue count
        long overdueCount = reminderTaskMapper.selectCount(
                new LambdaQueryWrapper<ReminderTask>()
                        .eq(ReminderTask::getUsername, username)
                        .eq(ReminderTask::getEnabled, true)
                        .eq(ReminderTask::getCompleted, false)
                        .isNotNull(ReminderTask::getNextDueAt)
                        .ne(ReminderTask::getNextDueAt, "")
                        .lt(ReminderTask::getNextDueAt, nowStr));
        summary.put("overdueCount", overdueCount);

        // Today count
        long todayCount = reminderTaskMapper.selectCount(
                new LambdaQueryWrapper<ReminderTask>()
                        .eq(ReminderTask::getUsername, username)
                        .eq(ReminderTask::getEnabled, true)
                        .eq(ReminderTask::getCompleted, false)
                        .ge(ReminderTask::getNextDueAt, nowStr)
                        .le(ReminderTask::getNextDueAt, todayEnd));
        summary.put("todayCount", todayCount);

        // Upcoming within 7 days (excluding today)
        String weekLater = now.plusDays(7).format(FMT);
        long upcomingCount = reminderTaskMapper.selectCount(
                new LambdaQueryWrapper<ReminderTask>()
                        .eq(ReminderTask::getUsername, username)
                        .eq(ReminderTask::getEnabled, true)
                        .eq(ReminderTask::getCompleted, false)
                        .gt(ReminderTask::getNextDueAt, todayEnd)
                        .le(ReminderTask::getNextDueAt, weekLater));
        summary.put("upcomingCount", upcomingCount);

        return summary;
    }

    private String calculateInitialNextDue(ReminderTask task) {
        String dueDate = task.getDueDate();
        if (dueDate == null || dueDate.isEmpty()) return dueDate;
        LocalDateTime due = LocalDateTime.parse(dueDate, FMT);
        LocalDateTime now = LocalDateTime.now();

        if ("once".equals(task.getRecurrenceType())) {
            return dueDate;
        }

        // For recurring tasks, if due date is in the past, advance to next occurrence
        if (due.isBefore(now)) {
            LocalDateTime next = calculateNextOccurrence(task);
            return next != null ? next.format(FMT) : dueDate;
        }
        return dueDate;
    }

    private LocalDateTime calculateNextOccurrence(ReminderTask task) {
        LocalDateTime now = LocalDateTime.now();
        // 以"当前到期点"为基准推进（而非最初的 dueDate），避免提前完成时又落回同一次
        String currentDue = task.getNextDueAt();
        LocalDateTime base = (currentDue != null && !currentDue.isEmpty())
                ? LocalDateTime.parse(currentDue, FMT)
                : LocalDateTime.parse(task.getDueDate(), FMT);
        String configJson = task.getRecurrenceConfig();

        Map<String, Object> config = parseConfig(configJson);

        switch (task.getRecurrenceType()) {
            case "daily" -> {
                LocalDateTime next = base.plusDays(1);
                while (!next.isAfter(now)) next = next.plusDays(1);
                return next;
            }
            case "weekly" -> {
                List<Integer> daysOfWeek = getConfigIntList(config, "daysOfWeek");
                if (daysOfWeek.isEmpty()) {
                    // Default: same day next week
                    LocalDateTime next = base.plusWeeks(1);
                    while (!next.isAfter(now)) next = next.plusWeeks(1);
                    return next;
                }
                // Find next matching day of week
                LocalDateTime candidate = base;
                for (int i = 0; i < 365; i++) {
                    candidate = candidate.plusDays(1);
                    int dayNum = candidate.getDayOfWeek().getValue(); // 1=Monday..7=Sunday
                    if (daysOfWeek.contains(dayNum) && candidate.isAfter(now)) {
                        return candidate.withHour(base.getHour()).withMinute(base.getMinute()).withSecond(base.getSecond());
                    }
                }
                return null;
            }
            case "monthly" -> {
                Integer dayOfMonth = getConfigInteger(config, "dayOfMonth");
                if (dayOfMonth == null) dayOfMonth = base.getDayOfMonth();
                java.time.YearMonth nextMonth = java.time.YearMonth.from(base.plusMonths(1));
                LocalDateTime next = nextMonth.atDay(Math.min(dayOfMonth, nextMonth.lengthOfMonth())).atTime(base.toLocalTime());
                while (!next.isAfter(now)) {
                    nextMonth = java.time.YearMonth.from(next.plusMonths(1));
                    next = nextMonth.atDay(Math.min(dayOfMonth, nextMonth.lengthOfMonth())).atTime(base.toLocalTime());
                }
                return next;
            }
            case "yearly" -> {
                Integer month = getConfigInteger(config, "month");
                Integer day = getConfigInteger(config, "dayOfMonth");
                if (month == null) month = base.getMonthValue();
                if (day == null) day = base.getDayOfMonth();
                java.time.YearMonth ym = java.time.YearMonth.of(base.getYear() + 1, month);
                LocalDateTime next = ym.atDay(Math.min(day, ym.lengthOfMonth())).atTime(base.toLocalTime());
                while (!next.isAfter(now)) {
                    ym = java.time.YearMonth.of(next.getYear() + 1, month);
                    next = ym.atDay(Math.min(day, ym.lengthOfMonth())).atTime(base.toLocalTime());
                }
                return next;
            }
            default -> {
                // "once" - no next occurrence
                return null;
            }
        }
    }

    private Map<String, Object> parseConfig(String json) {
        if (json == null || json.isEmpty()) return Collections.emptyMap();
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    private List<Integer> getConfigIntList(Map<String, Object> config, String key) {
        Object val = config.get(key);
        if (val instanceof List<?> list) {
            return list.stream().map(v -> {
                if (v instanceof Number n) return n.intValue();
                return Integer.parseInt(v.toString());
            }).toList();
        }
        return Collections.emptyList();
    }

    private Integer getConfigInteger(Map<String, Object> config, String key) {
        Object val = config.get(key);
        if (val instanceof Number n) return n.intValue();
        if (val instanceof String s) return Integer.parseInt(s);
        return null;
    }
}
