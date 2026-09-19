package com.litv.monitor.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.TimeUnit;

/**
 * SSRF 防护：在连接前校验实际解析出的 IP。
 * 防护模式由「安全设置 → 监控请求安全」控制（数据库配置，运行时可改）：
 *   - strict        禁止回环/内网/链路本地/CGNAT/云元数据/IPv6 ULA
 *   - allow_internal 允许内网地址，但仍禁止回环/链路本地(含云元数据)/CGNAT 等
 *   - off           不限制（适用于纯内网部署）
 * 校验与连接使用同一 IP，可缓解 DNS rebinding。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SsrfProtectionService {

    private final SecuritySettingsService securitySettingsService;

    // 缓存模式，避免每次连接都查库；设置变更最多 5 秒后生效
    private final Cache<String, String> modeCache = Caffeine.newBuilder()
            .expireAfterWrite(5, TimeUnit.SECONDS)
            .maximumSize(1)
            .build();

    public String getMode() {
        return modeCache.get("mode", k -> securitySettingsService.getSsrfMode());
    }

    public void invalidateCache() {
        modeCache.invalidateAll();
    }

    public boolean isEnabled() {
        return !"off".equals(getMode());
    }

    public boolean isBlocked(InetAddress addr) {
        String mode = getMode();
        if ("off".equals(mode)) return false;
        if (addr == null) return true;

        // 任何模式下都禁止：回环、任意本地、链路本地（含 169.254.169.254 云元数据）、组播
        if (addr.isLoopbackAddress() || addr.isAnyLocalAddress()
                || addr.isLinkLocalAddress() || addr.isMulticastAddress()) {
            return true;
        }

        boolean strict = "strict".equals(mode);
        // 私有/内网站点本地地址：仅 strict 模式禁止
        if (strict && addr.isSiteLocalAddress()) {
            return true;
        }

        byte[] b = addr.getAddress();
        if (b.length == 4) {
            int a = b[0] & 0xFF;
            int c = b[1] & 0xFF;
            if (a == 0) return true;                                   // 0.0.0.0/8
            if (a == 100 && c >= 64 && c <= 127) return true;          // 100.64.0.0/10 (CGNAT)
            if (a == 192 && c == 0 && (b[2] & 0xFF) == 0) return true; // 192.0.0.0/24
            if (a == 198 && (c == 18 || c == 19)) return true;         // 198.18.0.0/15
            if (a == 169 && c == 254) return true;                     // 169.254.0.0/16
            if (strict) {
                if (a == 10) return true;
                if (a == 172 && c >= 16 && c <= 31) return true;
                if (a == 192 && c == 168) return true;
            }
        } else {
            if (strict && (b[0] & 0xFE) == 0xFC) return true;          // fc00::/7 unique local
            boolean mapped = true;
            for (int i = 0; i < 10; i++) {
                if (b[i] != 0) { mapped = false; break; }
            }
            if (mapped && (b[10] & 0xFF) == 0xFF && (b[11] & 0xFF) == 0xFF) {
                try {
                    InetAddress v4 = InetAddress.getByAddress(new byte[]{b[12], b[13], b[14], b[15]});
                    return isBlocked(v4);
                } catch (UnknownHostException e) {
                    return true;
                }
            }
        }
        return false;
    }

    public void assertAllowed(InetAddress addr) {
        if (isBlocked(addr)) {
            throw new SecurityException("SSRF 防护：禁止访问内网/保留地址 " + addr.getHostAddress()
                    + "（可在「安全设置 → 监控请求安全」调整防护模式）");
        }
    }

    /** 解析主机并校验，返回已校验的地址（连接时应使用该地址，避免二次解析）。 */
    public InetAddress resolveAndCheck(String host) throws UnknownHostException {
        InetAddress addr = InetAddress.getByName(host);
        assertAllowed(addr);
        return addr;
    }

    /** 校验主机解析出的所有地址。 */
    public void assertHostAllowed(String host) {
        if (!isEnabled()) return;
        try {
            for (InetAddress addr : InetAddress.getAllByName(host)) {
                assertAllowed(addr);
            }
        } catch (UnknownHostException e) {
            throw new SecurityException("无法解析主机: " + host);
        }
    }
}
