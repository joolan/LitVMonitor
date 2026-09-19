package com.litv.monitor.config;

import com.litv.monitor.mapper.SecuritySettingMapper;
import com.litv.monitor.service.ProxyConfigService;
import com.litv.monitor.util.IpUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Order(3)
@RequiredArgsConstructor
public class ProxyConfigInit implements CommandLineRunner {

    private final ProxyConfigService proxyConfigService;
    private final SecuritySettingMapper securitySettingMapper;

    @Override
    public void run(String... args) {
        try {
            proxyConfigService.initProxyOnStartup();
            log.info("Proxy config initialized");
        } catch (Exception e) {
            log.warn("Failed to init proxy config: {}", e.getMessage());
        }
        try {
            IpUtils.init(securitySettingMapper);
            log.info("IP config initialized");
        } catch (Exception e) {
            log.warn("Failed to init IP config: {}", e.getMessage());
        }
    }
}
