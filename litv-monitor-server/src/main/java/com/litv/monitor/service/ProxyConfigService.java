package com.litv.monitor.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.litv.monitor.entity.ProxyConfig;
import com.litv.monitor.mapper.ProxyConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProxyConfigService {

    private final ProxyConfigMapper proxyConfigMapper;

    private volatile ProxyConfig activeProxyConfig;

    public List<ProxyConfig> listAll() {
        return proxyConfigMapper.selectList(
                new LambdaQueryWrapper<ProxyConfig>()
                        .orderByDesc(ProxyConfig::getActive)
                        .orderByDesc(ProxyConfig::getCreatedAt)
                        .last("LIMIT 1000")
        );
    }

    public ProxyConfig getById(Long id) {
        return proxyConfigMapper.selectById(id);
    }

    public ProxyConfig create(ProxyConfig config) {
        if (config.getEnabled() == null) config.setEnabled(true);
        if (config.getActive() == null) config.setActive(false);
        if (config.getProxyType() == null || config.getProxyType().isEmpty()) config.setProxyType("HTTP");
        if (Boolean.TRUE.equals(config.getActive())) {
            // Only one proxy may be active; keep `enabled` consistent with `active`
            proxyConfigMapper.update(null, new LambdaUpdateWrapper<ProxyConfig>().set(ProxyConfig::getActive, false));
            config.setEnabled(true);
        }
        proxyConfigMapper.insert(config);
        if (Boolean.TRUE.equals(config.getActive())) {
            refreshActiveProxy();
        }
        return proxyConfigMapper.selectById(config.getId());
    }

    public ProxyConfig update(Long id, ProxyConfig config) {
        ProxyConfig existing = proxyConfigMapper.selectById(id);
        if (existing == null) return null;
        config.setId(id);
        // 密码留空表示不修改（前端不再回传明文密码）
        if (config.getPassword() == null || config.getPassword().isEmpty()) {
            config.setPassword(null);
        }
        proxyConfigMapper.updateById(config);
        refreshActiveProxy();
        return proxyConfigMapper.selectById(id);
    }

    public void delete(Long id) {
        ProxyConfig existing = proxyConfigMapper.selectById(id);
        proxyConfigMapper.deleteById(id);
        if (existing != null && Boolean.TRUE.equals(existing.getActive())) {
            activeProxyConfig = null;
            log.info("Active proxy deleted, all proxies disabled");
        }
    }

    @Transactional
    public ProxyConfig setActive(Long id) {
        // Disable all first
        LambdaUpdateWrapper<ProxyConfig> disableAll = new LambdaUpdateWrapper<ProxyConfig>()
                .set(ProxyConfig::getActive, false);
        proxyConfigMapper.update(null, disableAll);

        // Enable the selected one (also keep `enabled` consistent with `active`)
        LambdaUpdateWrapper<ProxyConfig> enableOne = new LambdaUpdateWrapper<ProxyConfig>()
                .eq(ProxyConfig::getId, id)
                .set(ProxyConfig::getActive, true)
                .set(ProxyConfig::getEnabled, true);
        proxyConfigMapper.update(null, enableOne);

        refreshActiveProxy();
        return proxyConfigMapper.selectById(id);
    }

    public ProxyConfig deactivateAll() {
        LambdaUpdateWrapper<ProxyConfig> disableAll = new LambdaUpdateWrapper<ProxyConfig>()
                .set(ProxyConfig::getActive, false);
        proxyConfigMapper.update(null, disableAll);
        activeProxyConfig = null;
        log.info("All proxies deactivated");
        return null;
    }

    public ProxyConfig getActiveProxyConfig() {
        return activeProxyConfig;
    }

    public void refreshActiveProxy() {
        // `enabled` is not exposed by the UI and is redundant with `active`; only
        // `active` should decide which proxy is used. Gating on `enabled` caused the
        // active proxy to be silently ignored when enabled happened to be 0.
        ProxyConfig config = proxyConfigMapper.selectOne(
                new LambdaQueryWrapper<ProxyConfig>()
                        .eq(ProxyConfig::getActive, true)
                        .last("LIMIT 1")
        );

        if (config != null) {
            if (!Boolean.TRUE.equals(config.getEnabled())) {
                log.warn("Active proxy id={} has enabled={}; using it anyway", config.getId(), config.getEnabled());
            }
            activeProxyConfig = config;
            log.info("Active proxy set: {} {}:{}, auth={}", config.getProxyType(), config.getHost(), config.getPort(),
                    config.getUsername() != null && !config.getUsername().isEmpty());
        } else {
            activeProxyConfig = null;
            log.info("No active proxy configured");
        }
    }

    public void initProxyOnStartup() {
        long count = proxyConfigMapper.selectCount(null);
        if (count > 0) {
            refreshActiveProxy();
        }
    }
}
