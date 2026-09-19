package com.litv.monitor.util;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.litv.monitor.entity.SecuritySetting;
import com.litv.monitor.mapper.SecuritySettingMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

@Slf4j
public class IpUtils {

    private static final Pattern IPV4_PATTERN = Pattern.compile("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$");
    private static final Pattern IPV6_PATTERN = Pattern.compile("^[0-9a-fA-F:]+$");

    // Atomic snapshot to avoid partial reads
    private static volatile ConfigSnapshot snapshot = new ConfigSnapshot("X-Real-IP", false, Set.of("127.0.0.1", "::1"), 0);
    private static final long CACHE_TTL = 60_000;

    private static class ConfigSnapshot {
        final String ipSourceHeader;
        final boolean strictMode;
        final Set<String> trustedProxies;
        final long cacheTime;

        ConfigSnapshot(String ipSourceHeader, boolean strictMode, Set<String> trustedProxies, long cacheTime) {
            this.ipSourceHeader = ipSourceHeader;
            this.strictMode = strictMode;
            this.trustedProxies = trustedProxies;
            this.cacheTime = cacheTime;
        }
    }

    public static void init(SecuritySettingMapper mapper) {
        refreshCache(mapper);
    }

    public static void refreshCache(SecuritySettingMapper mapper) {
        try {
            String header = getSettingValue(mapper, "ip_source_header", "X-Real-IP");
            String strictModeStr = getSettingValue(mapper, "ip_strict_mode", "false");
            boolean strict = "true".equalsIgnoreCase(strictModeStr);
            String trustedProxies = getSettingValue(mapper, "xff_trusted_proxies", "127.0.0.1,::1");
            Set<String> proxies = new HashSet<>(Arrays.asList(trustedProxies.split(",")));
            // Atomic swap
            snapshot = new ConfigSnapshot(header, strict, proxies, System.currentTimeMillis());
            log.info("IP config refreshed: header={}, strictMode={}, trustedProxies={}", header, strict, proxies);
        } catch (Exception e) {
            log.warn("Failed to refresh IP config: {}", e.getMessage());
        }
    }

    public static String getClientIp(HttpServletRequest request, SecuritySettingMapper mapper) {
        ConfigSnapshot snap = snapshot;
        if (System.currentTimeMillis() - snap.cacheTime > CACHE_TTL) {
            refreshCache(mapper);
            snap = snapshot;
        }

        String headerName = snap.ipSourceHeader;
        boolean strictMode = snap.strictMode;
        Set<String> trustedProxies = snap.trustedProxies;

        String remoteAddr = request.getRemoteAddr();

        if ("REMOTE_ADDR".equalsIgnoreCase(headerName) || headerName == null || headerName.isEmpty()) {
            return remoteAddr;
        }

        boolean isTrusted = trustedProxies.contains(remoteAddr)
                || isPrivateIp(remoteAddr);

        if (!isTrusted) {
            return remoteAddr;
        }

        String[] headers = headerName.split(",");
        for (String header : headers) {
            String headerTrimmed = header.trim();
            if (headerTrimmed.isEmpty()) continue;

            String value = request.getHeader(headerTrimmed);
            if (value == null || value.isEmpty()) continue;

            String[] ips = value.split(",");
            String clientIp = ips[0].trim();

            if (!isValidIp(clientIp)) {
                if (strictMode) {
                    log.warn("Invalid IP format in header {}: {} - rejecting request", headerTrimmed, clientIp);
                    return null;
                }
                continue;
            }

            if (strictMode && ips.length > 1) {
                log.warn("XFF header {} has multiple IPs ({}) in strict mode - rejecting", headerTrimmed, value);
                return null;
            }

            return clientIp;
        }

        return remoteAddr;
    }

    private static boolean isPrivateIp(String ip) {
        if (ip == null || ip.isEmpty()) return false;

        // IPv4-mapped IPv6 loopback: ::ffff:127.0.0.1
        if (ip.startsWith("::ffff:")) {
            ip = ip.substring(7);
        }

        // IPv4 loopback
        if ("127.0.0.1".equals(ip) || "0.0.0.0".equals(ip)) return true;

        // IPv6 loopback
        if ("0:0:0:0:0:0:0:1".equals(ip) || "::1".equals(ip)) return true;

        // IPv4 private ranges (RFC 1918)
        if (ip.startsWith("10.")) return true;                          // 10.0.0.0/8
        if (ip.startsWith("192.168.")) return true;                     // 192.168.0.0/16
        if (ip.startsWith("172.")) {
            // 172.16.0.0/12 only: 172.16.x.x - 172.31.x.x
            String[] parts = ip.split("\\.");
            if (parts.length >= 2) {
                try {
                    int second = Integer.parseInt(parts[1]);
                    return second >= 16 && second <= 31;
                } catch (NumberFormatException e) {
                    return false;
                }
            }
        }

        // Link-local
        if (ip.startsWith("169.254.")) return true;

        return false;
    }

    static boolean isValidIp(String ip) {
        if (ip == null || ip.isEmpty()) return false;

        // IPv4
        if (IPV4_PATTERN.matcher(ip).matches()) {
            String[] parts = ip.split("\\.");
            for (String part : parts) {
                try {
                    int val = Integer.parseInt(part);
                    if (val < 0 || val > 255) return false;
                } catch (NumberFormatException e) {
                    return false;
                }
            }
            return true;
        }

        // IPv6: must contain colon, only hex chars and colons, at least one colon
        if (ip.contains(":") && IPV6_PATTERN.matcher(ip).matches()) {
            // Must have 2-7 colons (valid IPv6 has groups separated by colons)
            int colonCount = 0;
            for (char c : ip.toCharArray()) {
                if (c == ':') colonCount++;
            }
            return colonCount >= 2 && colonCount <= 7;
        }

        return false;
    }

    private static String getSettingValue(SecuritySettingMapper mapper, String key, String defaultValue) {
        SecuritySetting setting = mapper.selectOne(
                new LambdaQueryWrapper<SecuritySetting>().eq(SecuritySetting::getSettingKey, key));
        return setting != null && setting.getSettingValue() != null ? setting.getSettingValue() : defaultValue;
    }
}
