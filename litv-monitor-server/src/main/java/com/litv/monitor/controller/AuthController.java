package com.litv.monitor.controller;

import com.litv.monitor.dto.LoginRequest;
import com.litv.monitor.dto.LoginResponse;
import com.litv.monitor.dto.Result;
import com.litv.monitor.entity.SysUser;
import com.litv.monitor.security.JwtTokenProvider;
import com.litv.monitor.service.AuditLogService;
import com.litv.monitor.service.SecuritySettingsService;
import com.litv.monitor.service.UserSessionService;
import com.litv.monitor.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserService userService;
    private final AuditLogService auditLogService;
    private final UserSessionService userSessionService;
    private final SecuritySettingsService securitySettingsService;

    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                       HttpServletRequest httpRequest) {
        String clientIp = getClientIp(httpRequest);
        // IP 维度限速（缓解撞库/暴力破解）
        if (userSessionService.isIpThrottled(clientIp)) {
            return Result.error(429, "登录尝试过于频繁，请稍后再试");
        }
        // Check account lockout
        if (securitySettingsService.isLockoutEnabled() && userSessionService.isLockedOut(request.getUsername())) {
            return Result.error(423, "账号已被锁定，请稍后再试");
        }

        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
            );

            SecurityContextHolder.getContext().setAuthentication(authentication);

            SysUser user = userService.findByUsername(request.getUsername());

            // Generate token with jti
            String userAgent = httpRequest.getHeader("User-Agent");
            boolean mustChange = Boolean.TRUE.equals(user.getMustChangePassword());
            String jti = userSessionService.createSession(user.getId(), user.getUsername(), clientIp, userAgent);
            String token = jwtTokenProvider.generateToken(
                    user.getUsername(), "ROLE_" + user.getRole(), mustChange, jti);

            // Clear failed attempts on successful login
            userSessionService.clearFailedAttempts(request.getUsername());
            userSessionService.clearIpFailedAttempts(clientIp);

            try {
                String maskedBody = AuditLogService.maskSensitiveFields(
                    "{\"username\":\"" + request.getUsername() + "\",\"password\":\"" + request.getPassword() + "\"}",
                    "password");
                auditLogService.record(user.getId(), request.getUsername(), "LOGIN", "USER",
                        String.valueOf(user.getId()), user.getUsername(), "用户登录成功", maskedBody);
            } catch (Exception ignored) {}

            LoginResponse response = LoginResponse.builder()
                    .token(token)
                    .username(user.getUsername())
                    .nickname(user.getNickname())
                    .email(user.getEmail())
                    .role(user.getRole())
                    .mustChangePassword(mustChange)
                    .build();

            return Result.success(response);
        } catch (org.springframework.security.authentication.BadCredentialsException e) {
            userSessionService.recordFailedAttempt(request.getUsername());
            userSessionService.recordFailedAttemptByIp(clientIp);
            try {
                String maskedBody = AuditLogService.maskSensitiveFields(
                    "{\"username\":\"" + request.getUsername() + "\",\"password\":\"" + request.getPassword() + "\"}",
                    "password");
                auditLogService.record(null, request.getUsername(), "LOGIN", "USER",
                        null, request.getUsername(), "登录失败: 用户名或密码错误", maskedBody);
            } catch (Exception ignored) {}
            return Result.error(401, "用户名或密码错误");
        } catch (org.springframework.security.authentication.DisabledException e) {
            userSessionService.recordFailedAttempt(request.getUsername());
            return Result.error(401, "账号已被禁用");
        } catch (Exception e) {
            // Record failed attempt
            userSessionService.recordFailedAttempt(request.getUsername());
            userSessionService.recordFailedAttemptByIp(clientIp);

            try {
                String maskedBody = AuditLogService.maskSensitiveFields(
                    "{\"username\":\"" + request.getUsername() + "\",\"password\":\"" + request.getPassword() + "\"}",
                    "password");
                auditLogService.record(null, request.getUsername(), "LOGIN", "USER",
                        null, request.getUsername(), "登录失败: " + e.getMessage(), maskedBody);
            } catch (Exception ignored) {}
            return Result.error(401, "登录失败: " + e.getMessage());
        }
    }

    @GetMapping("/me")
    public Result<Map<String, Object>> getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        SysUser user = userService.findByUsername(authentication.getName());

        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("id", user.getId());
        userInfo.put("username", user.getUsername());
        userInfo.put("nickname", user.getNickname());
        userInfo.put("email", user.getEmail());
        userInfo.put("role", user.getRole());
        userInfo.put("mustChangePassword", Boolean.TRUE.equals(user.getMustChangePassword()));

        return Result.success(userInfo);
    }

    @PostMapping("/logout")
    public Result<Void> logout(@RequestBody(required = false) Map<String, String> body) {
        if (body != null && body.containsKey("jti")) {
            String jti = body.get("jti");
            userSessionService.removeSession(jti);
        }
        SecurityContextHolder.clearContext();
        return Result.success();
    }

    private String getClientIp(HttpServletRequest request) {
        return request.getRemoteAddr();
    }
}
