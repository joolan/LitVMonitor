package com.litv.monitor.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.litv.monitor.entity.SecuritySetting;
import com.litv.monitor.mapper.SecuritySettingMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SecuritySettingsService {

    private final SecuritySettingMapper securitySettingMapper;

    /** SSRF 防护的初始默认值，来自环境变量/yml（仅用于首次初始化，之后以数据库设置为准）。 */
    @Value("${monitor.ssrf-protection:true}")
    private boolean ssrfProtectionDefault;

    @PostConstruct
    public void initDefaultSettings() {
        setDefault("ip_whitelist_enabled", "false", "启用IP白名单限制");
        setDefault("ip_whitelist", "", "IP白名单（每行一个，支持CIDR如192.168.1.0/24）");
        setDefault("ip_blacklist_enabled", "false", "启用IP黑名单限制");
        setDefault("ip_blacklist", "", "IP黑名单（每行一个，支持CIDR如192.168.1.0/24）");
        setDefault("lockout_enabled", "true", "启用连续登录失败锁定");
        setDefault("lockout_max_attempts", "5", "连续登录失败最大次数");
        setDefault("lockout_duration_minutes", "30", "锁定时长（分钟）");
        setDefault("max_sessions_enabled", "false", "启用同时在线人数限制");
        setDefault("max_sessions", "3", "同一账号最大同时在线人数");
        setDefault("status_page_public", "false", "公开状态页（允许匿名访问）");
        setDefault("ssrf_mode", ssrfProtectionDefault ? "strict" : "off",
                "监控请求SSRF防护模式：strict=禁止内网/保留地址，allow_internal=允许内网但禁止云元数据，off=不限制");
    }

    private void setDefault(String key, String value, String description) {
        try {
            SecuritySetting existing = securitySettingMapper.selectOne(
                new LambdaQueryWrapper<SecuritySetting>().eq(SecuritySetting::getSettingKey, key)
            );
            if (existing == null) {
                SecuritySetting setting = new SecuritySetting();
                setting.setSettingKey(key);
                setting.setSettingValue(value);
                setting.setDescription(description);
                setting.setCreatedAt(LocalDateTime.now());
                setting.setUpdatedAt(LocalDateTime.now());
                securitySettingMapper.insert(setting);
            }
        } catch (Exception e) {
            log.debug("Failed to init security setting {}: {}", key, e.getMessage());
        }
    }

    public Map<String, String> getAllSettings() {
        Map<String, String> map = new HashMap<>();
        try {
            List<SecuritySetting> settings = securitySettingMapper.selectList(
                new LambdaQueryWrapper<SecuritySetting>()
            );
            for (SecuritySetting s : settings) {
                map.put(s.getSettingKey(), s.getSettingValue());
            }
        } catch (Exception e) {
            log.error("Failed to load security settings", e);
        }
        return map;
    }

    public void updateSettings(Map<String, String> updates) {
        for (Map.Entry<String, String> entry : updates.entrySet()) {
            SecuritySetting existing = securitySettingMapper.selectOne(
                new LambdaQueryWrapper<SecuritySetting>().eq(SecuritySetting::getSettingKey, entry.getKey())
            );
            if (existing != null) {
                existing.setSettingValue(entry.getValue());
                existing.setUpdatedAt(LocalDateTime.now());
                securitySettingMapper.updateById(existing);
            } else {
                SecuritySetting setting = new SecuritySetting();
                setting.setSettingKey(entry.getKey());
                setting.setSettingValue(entry.getValue());
                setting.setCreatedAt(LocalDateTime.now());
                setting.setUpdatedAt(LocalDateTime.now());
                securitySettingMapper.insert(setting);
            }
        }
    }

    public String getSetting(String key, String defaultValue) {
        try {
            SecuritySetting setting = securitySettingMapper.selectOne(
                new LambdaQueryWrapper<SecuritySetting>().eq(SecuritySetting::getSettingKey, key)
            );
            return setting != null ? setting.getSettingValue() : defaultValue;
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public boolean isIpWhitelistEnabled() {
        return "true".equalsIgnoreCase(getSetting("ip_whitelist_enabled", "false"));
    }

    public boolean isIpBlacklistEnabled() {
        return "true".equalsIgnoreCase(getSetting("ip_blacklist_enabled", "false"));
    }

    public List<String> getWhitelistedIps() {
        return parseIpList(getSetting("ip_whitelist", ""));
    }

    public List<String> getBlacklistedIps() {
        return parseIpList(getSetting("ip_blacklist", ""));
    }

    public boolean isLockoutEnabled() {
        return "true".equalsIgnoreCase(getSetting("lockout_enabled", "false"));
    }

    public int getLockoutMaxAttempts() {
        return Integer.parseInt(getSetting("lockout_max_attempts", "5"));
    }

    public int getLockoutDurationMinutes() {
        return Integer.parseInt(getSetting("lockout_duration_minutes", "30"));
    }

    public boolean isMaxSessionsEnabled() {
        return "true".equalsIgnoreCase(getSetting("max_sessions_enabled", "false"));
    }

    public int getMaxSessions() {
        return Integer.parseInt(getSetting("max_sessions", "3"));
    }

    public boolean isStatusPagePublic() {
        return "true".equalsIgnoreCase(getSetting("status_page_public", "false"));
    }

    /** SSRF 防护模式：strict / allow_internal / off */
    public String getSsrfMode() {
        return getSetting("ssrf_mode", ssrfProtectionDefault ? "strict" : "off");
    }

    private List<String> parseIpList(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        return java.util.Arrays.stream(raw.split("[\\r\\n,]"))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .toList();
    }

    public boolean isIpAllowed(String clientIp) {
        if (isIpBlacklistEnabled()) {
            for (String pattern : getBlacklistedIps()) {
                if (matchIp(clientIp, pattern)) {
                    return false;
                }
            }
        }
        if (isIpWhitelistEnabled()) {
            for (String pattern : getWhitelistedIps()) {
                if (matchIp(clientIp, pattern)) {
                    return true;
                }
            }
            return false;
        }
        return true;
    }

    private boolean matchIp(String ip, String pattern) {
        if (pattern.contains("/")) {
            try {
                String[] parts = pattern.split("/");
                String network = parts[0];
                int prefixLen = Integer.parseInt(parts[1]);
                long ipNum = ipToLong(ip);
                long networkNum = ipToLong(network);
                long mask = -1L << (32 - prefixLen);
                return (ipNum & mask) == (networkNum & mask);
            } catch (Exception e) {
                return false;
            }
        }
        return ip.equals(pattern);
    }

    private long ipToLong(String ip) {
        String[] parts = ip.split("\\.");
        long result = 0;
        for (String part : parts) {
            result = result * 256 + Long.parseLong(part);
        }
        return result;
    }
}
