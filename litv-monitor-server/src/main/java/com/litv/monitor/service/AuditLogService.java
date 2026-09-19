package com.litv.monitor.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.litv.monitor.entity.AuditLog;
import com.litv.monitor.mapper.AuditLogMapper;
import com.litv.monitor.mapper.SecuritySettingMapper;
import com.litv.monitor.util.IpUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogMapper auditLogMapper;
    private final SecuritySettingMapper securitySettingMapper;

    public void record(Long userId, String username, String action,
                       String targetType, String targetId, String targetName, String detail) {
        AuditLog logEntry = new AuditLog();
        logEntry.setUserId(userId);
        logEntry.setUsername(username);
        logEntry.setAction(action);
        logEntry.setTargetType(targetType);
        logEntry.setTargetId(targetId);
        logEntry.setTargetName(targetName);
        logEntry.setDetail(detail);
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                logEntry.setIpAddress(IpUtils.getClientIp(attrs.getRequest(), securitySettingMapper));
                logEntry.setRequestUrl(buildFullUrl(attrs.getRequest()));
            }
        } catch (Exception ignored) {}
        logEntry.setCreatedAt(LocalDateTime.now());
        auditLogMapper.insert(logEntry);
    }

    public void record(Long userId, String username, String action,
                       String targetType, String targetId, String targetName, 
                       String detail, String requestBody) {
        AuditLog logEntry = new AuditLog();
        logEntry.setUserId(userId);
        logEntry.setUsername(username);
        logEntry.setAction(action);
        logEntry.setTargetType(targetType);
        logEntry.setTargetId(targetId);
        logEntry.setTargetName(targetName);
        logEntry.setDetail(detail);
        logEntry.setRequestBody(requestBody);
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                logEntry.setIpAddress(IpUtils.getClientIp(attrs.getRequest(), securitySettingMapper));
                logEntry.setRequestUrl(buildFullUrl(attrs.getRequest()));
            }
        } catch (Exception ignored) {}
        logEntry.setCreatedAt(LocalDateTime.now());
        auditLogMapper.insert(logEntry);
    }

    public static String maskSensitiveFields(String json, String... fields) {
        if (json == null || json.isEmpty()) return json;
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode node = mapper.readTree(json);
            for (String field : fields) {
                if (node.has(field) && !node.get(field).isNull()) {
                    // 完全脱敏，不保留任何字符
                    ((com.fasterxml.jackson.databind.node.ObjectNode) node).put(field, "***");
                }
            }
            return mapper.writeValueAsString(node);
        } catch (Exception e) {
            return json;
        }
    }

    public Page<AuditLog> listLogs(Page<AuditLog> page, String username, String action) {
        LambdaQueryWrapper<AuditLog> wrapper = new LambdaQueryWrapper<>();
        if (username != null && !username.isEmpty()) {
            wrapper.eq(AuditLog::getUsername, username);
        }
        if (action != null && !action.isEmpty()) {
            wrapper.eq(AuditLog::getAction, action);
        }
        wrapper.orderByDesc(AuditLog::getCreatedAt);
        return auditLogMapper.selectPage(page, wrapper);
    }

    private String buildFullUrl(jakarta.servlet.http.HttpServletRequest request) {
        String url = request.getRequestURL().toString();
        String query = request.getQueryString();
        if (query != null && !query.isEmpty()) {
            url = url + "?" + query;
        }
        return url;
    }
}
