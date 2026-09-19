package com.litv.monitor.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.litv.monitor.dto.ProfileUpdateDTO;
import com.litv.monitor.dto.Result;
import com.litv.monitor.dto.UserDTO;
import com.litv.monitor.entity.SysUser;
import com.litv.monitor.service.AuditLogService;
import com.litv.monitor.service.UserService;
import com.litv.monitor.service.UserSessionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final AuditLogService auditLogService;
    private final UserSessionService userSessionService;
    private final ObjectMapper objectMapper;

    @GetMapping("/list")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Page<SysUser>> listUsers(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.success(userService.listUsers(new Page<>(page, size)));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Result<SysUser> createUser(@Valid @RequestBody UserDTO dto) {
        SysUser user = userService.createUser(dto);
        String username = getCurrentUsername();
        String requestBody = "";
        try {
            requestBody = AuditLogService.maskSensitiveFields(
                objectMapper.writeValueAsString(dto), "password");
        } catch (Exception ignored) {}
        auditLogService.record(null, username, "CREATE", "USER",
                String.valueOf(user.getId()), user.getUsername(), "创建用户", requestBody);
        return Result.success(user);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<SysUser> updateUser(@PathVariable Long id, @Valid @RequestBody UserDTO dto) {
        try {
            SysUser user = userService.updateUser(id, dto);
            if (user != null) {
                String username = getCurrentUsername();
                String requestBody = "";
                try {
                    requestBody = AuditLogService.maskSensitiveFields(
                        objectMapper.writeValueAsString(dto), "password");
                } catch (Exception ignored) {}
                auditLogService.record(null, username, "UPDATE", "USER",
                        String.valueOf(id), user.getUsername(), "更新用户", requestBody);
            }
            return user != null ? Result.success(user) : Result.error(404, "用户不存在");
        } catch (RuntimeException e) {
            return Result.error(400, e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> deleteUser(@PathVariable Long id) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        SysUser currentUser = userService.findByUsername(auth.getName());
        if (currentUser != null && currentUser.getId().equals(id)) {
            return Result.error(400, "不能删除自己的账户");
        }
        if (userService.isAdmin(id) && userService.countAdmins() <= 1) {
            return Result.error(400, "不能删除最后一个管理员账户");
        }
        try {
            userService.deleteUser(id);
            String username = getCurrentUsername();
            auditLogService.record(null, username, "DELETE", "USER",
                    String.valueOf(id), "", "删除用户", null);
            return Result.success();
        } catch (RuntimeException e) {
            return Result.error(400, e.getMessage());
        }
    }

    @PutMapping("/profile")
    public Result<SysUser> updateProfile(@Valid @RequestBody ProfileUpdateDTO dto) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();
        try {
            SysUser user = userService.updateProfile(username,
                    dto.getOldPassword(), dto.getNewPassword(),
                    dto.getNewPasswordConfirm(),
                    dto.getNickname(), dto.getEmail());
            if (user != null) {
                // Password change invalidates existing sessions so the token's
                // must-change-password flag is refreshed on next login.
                if (dto.getNewPassword() != null && !dto.getNewPassword().isEmpty()) {
                    userSessionService.removeAllSessionsForUser(user.getId());
                }
                String requestBody = "";
                try {
                    // Mask passwords in audit log
                    java.util.Map<String, Object> auditBody = new java.util.HashMap<>();
                    if (dto.getOldPassword() != null) auditBody.put("oldPassword", "***");
                    if (dto.getNewPassword() != null) auditBody.put("newPassword", "***");
                    auditBody.put("nickname", dto.getNickname());
                    auditBody.put("email", dto.getEmail());
                    requestBody = objectMapper.writeValueAsString(auditBody);
                } catch (Exception ignored) {}
                auditLogService.record(user.getId(), username, "UPDATE_PROFILE", "USER",
                        String.valueOf(user.getId()), user.getUsername(), "修改个人资料", requestBody);
                return Result.success(user);
            }
            return Result.error(404, "用户不存在");
        } catch (RuntimeException e) {
            return Result.error(400, e.getMessage());
        }
    }

    private String getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "system";
    }
}
