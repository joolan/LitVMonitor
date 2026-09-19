package com.litv.monitor.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.litv.monitor.dto.ReminderSnoozeRequest;
import com.litv.monitor.dto.ReminderTaskDTO;
import com.litv.monitor.dto.Result;
import com.litv.monitor.entity.ReminderTask;
import com.litv.monitor.service.ReminderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/reminder")
@RequiredArgsConstructor
public class ReminderController {

    private final ReminderService reminderService;

    @GetMapping("/list")
    public Result<?> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String status) {
        String username = getCurrentUsername();
        Page<ReminderTask> result = reminderService.listPage(username, category, status, page, size);
        return Result.success(result);
    }

    @GetMapping("/{id}")
    public Result<ReminderTask> get(@PathVariable Long id) {
        ReminderTask task = reminderService.getById(id, getCurrentUsername());
        if (task == null) return Result.error("任务不存在");
        return Result.success(task);
    }

    @PostMapping
    public Result<ReminderTask> create(@RequestBody @Valid ReminderTaskDTO dto) {
        String username = getCurrentUsername();
        ReminderTask task = reminderService.create(dto, username);
        return Result.success(task);
    }

    @PutMapping("/{id}")
    public Result<ReminderTask> update(@PathVariable Long id, @RequestBody ReminderTaskDTO dto) {
        ReminderTask task = reminderService.update(id, dto, getCurrentUsername());
        if (task == null) return Result.error("任务不存在");
        return Result.success(task);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        boolean ok = reminderService.delete(id, getCurrentUsername());
        if (!ok) return Result.error("任务不存在");
        return Result.success();
    }

    @PutMapping("/{id}/complete")
    public Result<ReminderTask> complete(@PathVariable Long id) {
        ReminderTask task = reminderService.complete(id, getCurrentUsername());
        if (task == null) return Result.error("任务不存在");
        return Result.success(task);
    }

    @PutMapping("/{id}/snooze")
    public Result<ReminderTask> snooze(@PathVariable Long id, @RequestBody ReminderSnoozeRequest req) {
        ReminderTask task = reminderService.snooze(id, req.getMinutes(), getCurrentUsername());
        if (task == null) return Result.error("任务不存在");
        return Result.success(task);
    }

    @PutMapping("/{id}/enable")
    public Result<ReminderTask> enable(@PathVariable Long id) {
        ReminderTask task = reminderService.toggleEnabled(id, true, getCurrentUsername());
        if (task == null) return Result.error("任务不存在");
        return Result.success(task);
    }

    @PutMapping("/{id}/disable")
    public Result<ReminderTask> disable(@PathVariable Long id) {
        ReminderTask task = reminderService.toggleEnabled(id, false, getCurrentUsername());
        if (task == null) return Result.error("任务不存在");
        return Result.success(task);
    }

    @GetMapping("/dashboard")
    public Result<Map<String, Object>> dashboard() {
        String username = getCurrentUsername();
        Map<String, Object> summary = reminderService.getDashboardSummary(username);
        return Result.success(summary);
    }

    @PostMapping("/preview")
    public Result<?> preview(@RequestBody Map<String, Object> body) {
        String dueDate = (String) body.get("dueDate");
        String recurrenceType = (String) body.get("recurrenceType");
        String recurrenceConfig = (String) body.get("recurrenceConfig");
        int count = body.get("count") instanceof Number n ? n.intValue() : 5;
        if (count > 10) count = 10;
        java.util.List<String> previews = reminderService.previewReminders(dueDate, recurrenceType, recurrenceConfig, count);
        return Result.success(previews);
    }

    private String getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "anonymous";
    }
}
