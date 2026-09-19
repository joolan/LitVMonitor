package com.litv.monitor.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.litv.monitor.dto.GlobalVariableDTO;
import com.litv.monitor.dto.Result;
import com.litv.monitor.entity.GlobalVariable;
import com.litv.monitor.entity.GroupVariable;
import com.litv.monitor.mapper.GroupVariableMapper;
import com.litv.monitor.service.AuditLogService;
import com.litv.monitor.service.GlobalVariableService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/variable")
@RequiredArgsConstructor
public class GlobalVariableController {

    private final GlobalVariableService globalVariableService;
    private final GroupVariableMapper groupVariableMapper;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    @GetMapping("/global/list")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public Result<Page<GlobalVariable>> listVariables(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword) {
        return Result.success(globalVariableService.listVariables(new Page<>(page, size), keyword));
    }

    @GetMapping("/global/all")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public Result<List<GlobalVariable>> listAllGlobalVariables() {
        return Result.success(globalVariableService.listAllVariables());
    }

    @GetMapping("/global/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public Result<GlobalVariable> getVariable(@PathVariable Long id) {
        GlobalVariable variable = globalVariableService.getVariableById(id);
        return variable != null ? Result.success(variable) : Result.error(404, "变量不存在");
    }

    @PostMapping("/global")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public Result<GlobalVariable> createVariable(@Valid @RequestBody GlobalVariableDTO dto) {
        GlobalVariable variable = globalVariableService.createVariable(dto);
        String username = getCurrentUsername();
        String requestBody = "";
        try { requestBody = objectMapper.writeValueAsString(dto); } catch (Exception ignored) {}
        auditLogService.record(null, username, "CREATE", "VARIABLE",
                String.valueOf(variable.getId()), variable.getName(), "创建全局变量", requestBody);
        return Result.success(variable);
    }

    @PutMapping("/global/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public Result<GlobalVariable> updateVariable(@PathVariable Long id, @Valid @RequestBody GlobalVariableDTO dto) {
        GlobalVariable variable = globalVariableService.updateVariable(id, dto);
        if (variable != null) {
            String username = getCurrentUsername();
            String requestBody = "";
            try { requestBody = objectMapper.writeValueAsString(dto); } catch (Exception ignored) {}
            auditLogService.record(null, username, "UPDATE", "VARIABLE",
                    String.valueOf(id), variable.getName(), "更新全局变量", requestBody);
        }
        return variable != null ? Result.success(variable) : Result.error(404, "变量不存在");
    }

    @DeleteMapping("/global/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public Result<Void> deleteVariable(@PathVariable Long id) {
        globalVariableService.deleteVariable(id);
        String username = getCurrentUsername();
        auditLogService.record(null, username, "DELETE", "VARIABLE",
                String.valueOf(id), "", "删除全局变量", null);
        return Result.success();
    }

    @GetMapping("/group/list")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public Result<Page<GroupVariable>> listGroupVariables(
            @RequestParam(required = false) Long groupId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        LambdaQueryWrapper<GroupVariable> wrapper = new LambdaQueryWrapper<>();
        if (groupId != null) {
            wrapper.eq(GroupVariable::getGroupId, groupId);
        }
        return Result.success(groupVariableMapper.selectPage(new Page<>(page, size), wrapper));
    }

    @GetMapping("/group/all")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public Result<List<GroupVariable>> listAllGroupVariables() {
        return Result.success(groupVariableMapper.selectList(null));
    }

    @PostMapping("/group")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public Result<GroupVariable> createGroupVariable(@RequestBody Map<String, Object> body) {
        GroupVariable var = new GroupVariable();
        if (body.get("groupId") != null) {
            var.setGroupId(Long.valueOf(body.get("groupId").toString()));
        }
        var.setName(body.get("name").toString());
        var.setValue(body.get("value") != null ? body.get("value").toString() : null);
        var.setScope("GROUP");
        groupVariableMapper.insert(var);
        String username = getCurrentUsername();
        String requestBody = "";
        try { requestBody = objectMapper.writeValueAsString(body); } catch (Exception ignored) {}
        auditLogService.record(null, username, "CREATE", "VARIABLE",
                String.valueOf(var.getId()), var.getName(), "创建分组变量", requestBody);
        return Result.success(var);
    }

    @PutMapping("/group/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public Result<GroupVariable> updateGroupVariable(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        GroupVariable var = groupVariableMapper.selectById(id);
        if (var == null) {
            return Result.error(404, "变量不存在");
        }
        var.setName(body.get("name").toString());
        var.setValue(body.get("value") != null ? body.get("value").toString() : null);
        groupVariableMapper.updateById(var);
        String username = getCurrentUsername();
        String requestBody = "";
        try { requestBody = objectMapper.writeValueAsString(body); } catch (Exception ignored) {}
        auditLogService.record(null, username, "UPDATE", "VARIABLE",
                String.valueOf(id), var.getName(), "更新分组变量", requestBody);
        return Result.success(var);
    }

    @DeleteMapping("/group/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public Result<Void> deleteGroupVariable(@PathVariable Long id) {
        groupVariableMapper.deleteById(id);
        String username = getCurrentUsername();
        auditLogService.record(null, username, "DELETE", "VARIABLE",
                String.valueOf(id), "", "删除分组变量", null);
        return Result.success();
    }

    private String getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "system";
    }
}
