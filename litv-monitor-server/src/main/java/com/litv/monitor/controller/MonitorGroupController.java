package com.litv.monitor.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.litv.monitor.dto.MonitorGroupDTO;
import com.litv.monitor.dto.Result;
import com.litv.monitor.entity.GroupMonitor;
import com.litv.monitor.entity.MonitorGroup;
import com.litv.monitor.service.AuditLogService;
import com.litv.monitor.service.GroupExecutionService;
import com.litv.monitor.service.MonitorGroupService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/group")
@RequiredArgsConstructor
public class MonitorGroupController {

    private final MonitorGroupService monitorGroupService;
    private final GroupExecutionService groupExecutionService;
    private final AuditLogService auditLogService;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @GetMapping("/list")
    public Result<Page<MonitorGroup>> listGroups(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) Long id) {
        return Result.success(monitorGroupService.listGroups(new Page<>(page, size), keyword, enabled, id));
    }

    @GetMapping("/{id}")
    public Result<MonitorGroup> getGroup(@PathVariable Long id) {
        MonitorGroup group = monitorGroupService.getGroupById(id);
        return group != null ? Result.success(group) : Result.error(404, "监控任务不存在");
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public Result<MonitorGroup> createGroup(@Valid @RequestBody MonitorGroupDTO dto) {
        MonitorGroup group = monitorGroupService.createGroup(dto);
        String username = getCurrentUsername();
        String requestBody = "";
        try { requestBody = objectMapper.writeValueAsString(dto); } catch (Exception ignored) {}
        auditLogService.record(null, username, "CREATE", "GROUP",
                String.valueOf(group.getId()), group.getName(), "创建监控任务", requestBody);
        return Result.success(group);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public Result<MonitorGroup> updateGroup(@PathVariable Long id, @Valid @RequestBody MonitorGroupDTO dto) {
        MonitorGroup group = monitorGroupService.updateGroup(id, dto);
        if (group != null) {
            String username = getCurrentUsername();
            String requestBody = "";
            try { requestBody = objectMapper.writeValueAsString(dto); } catch (Exception ignored) {}
            auditLogService.record(null, username, "UPDATE", "GROUP",
                    String.valueOf(id), group.getName(), "更新监控任务", requestBody);
        }
        return group != null ? Result.success(group) : Result.error(404, "监控任务不存在");
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public Result<Void> deleteGroup(@PathVariable Long id) {
        MonitorGroup group = monitorGroupService.getGroupById(id);
        String name = group != null ? group.getName() : "";
        monitorGroupService.deleteGroup(id);
        String username = getCurrentUsername();
        auditLogService.record(null, username, "DELETE", "GROUP",
                String.valueOf(id), name, "删除监控任务", null);
        return Result.success();
    }

    @GetMapping("/{id}/monitors")
    public Result<List<GroupMonitor>> getGroupMonitors(@PathVariable Long id) {
        return Result.success(monitorGroupService.getGroupMonitors(id));
    }

    @PostMapping("/{id}/run")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public Result<String> runGroup(@PathVariable Long id) {
        MonitorGroup group = monitorGroupService.getGroupById(id);
        groupExecutionService.executeGroup(id);
        String username = getCurrentUsername();
        auditLogService.record(null, username, "RUN", "GROUP",
                String.valueOf(id), group != null ? group.getName() : "", "手动执行监控任务", null);
        return Result.success("任务执行已启动");
    }

    private String getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "system";
    }
}
