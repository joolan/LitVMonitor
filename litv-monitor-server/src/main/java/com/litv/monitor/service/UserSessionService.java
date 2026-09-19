package com.litv.monitor.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.litv.monitor.entity.UserSession;
import com.litv.monitor.mapper.UserSessionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserSessionService {

    private final UserSessionMapper userSessionMapper;
    private final SecuritySettingsService securitySettingsService;

    private final Cache<String, Integer> failedAttempts = Caffeine.newBuilder()
            .expireAfterWrite(30, TimeUnit.MINUTES)
            .maximumSize(500)
            .build();
    private final Cache<String, LocalDateTime> lockoutExpiry = Caffeine.newBuilder()
            .expireAfterWrite(30, TimeUnit.MINUTES)
            .maximumSize(500)
            .build();

    /** IP 维度登录失败计数（缓解针对多个用户名的暴力破解/撞库）。 */
    private static final int IP_MAX_ATTEMPTS = 30;
    private final Cache<String, Integer> ipFailedAttempts = Caffeine.newBuilder()
            .expireAfterWrite(15, TimeUnit.MINUTES)
            .maximumSize(5000)
            .build();

    @PostConstruct
    public void init() {
        // Mark all sessions as inactive on startup (server restarted)
        try {
            userSessionMapper.update(null,
                new LambdaUpdateWrapper<UserSession>()
                    .eq(UserSession::getActive, true)
                    .set(UserSession::getActive, false)
            );
        } catch (Exception e) {
            log.debug("Failed to reset sessions on startup: {}", e.getMessage());
        }
    }

    public String createSession(Long userId, String username, String ip, String userAgent) {
        String jti = UUID.randomUUID().toString();

        // Check max sessions and evict oldest if needed
        if (securitySettingsService.isMaxSessionsEnabled()) {
            int maxSessions = securitySettingsService.getMaxSessions();
            List<UserSession> activeSessions = userSessionMapper.selectList(
                new LambdaQueryWrapper<UserSession>()
                    .eq(UserSession::getUserId, userId)
                    .eq(UserSession::getActive, true)
                    .orderByAsc(UserSession::getLoginAt)
            );
            if (activeSessions.size() >= maxSessions) {
                // Evict oldest session
                UserSession oldest = activeSessions.get(0);
                oldest.setActive(false);
                userSessionMapper.updateById(oldest);
                log.info("Evicted oldest session for user {} (jti={})", username, oldest.getJti());
            }
        }

        UserSession session = new UserSession();
        session.setUserId(userId);
        session.setUsername(username);
        session.setJti(jti);
        session.setIpAddress(ip);
        session.setUserAgent(userAgent);
        session.setLoginAt(LocalDateTime.now());
        session.setLastAccessAt(LocalDateTime.now());
        session.setActive(true);
        userSessionMapper.insert(session);

        return jti;
    }

    public boolean isSessionActive(String jti) {
        if (jti == null) return false;
        UserSession session = userSessionMapper.selectOne(
            new LambdaQueryWrapper<UserSession>().eq(UserSession::getJti, jti)
        );
        return session != null && session.getActive();
    }

    public void removeSession(String jti) {
        if (jti == null) return;
        UserSession session = userSessionMapper.selectOne(
            new LambdaQueryWrapper<UserSession>().eq(UserSession::getJti, jti)
        );
        if (session != null) {
            session.setActive(false);
            userSessionMapper.updateById(session);
        }
    }

    public void removeAllSessionsForUser(Long userId) {
        userSessionMapper.update(null,
            new LambdaUpdateWrapper<UserSession>()
                .eq(UserSession::getUserId, userId)
                .eq(UserSession::getActive, true)
                .set(UserSession::getActive, false)
        );
    }

    public List<UserSession> getActiveSessions(Long userId) {
        return userSessionMapper.selectList(
            new LambdaQueryWrapper<UserSession>()
                .eq(UserSession::getUserId, userId)
                .eq(UserSession::getActive, true)
                .orderByDesc(UserSession::getLoginAt)
        );
    }

    public long countActiveSessions(Long userId) {
        return userSessionMapper.selectCount(
            new LambdaQueryWrapper<UserSession>()
                .eq(UserSession::getUserId, userId)
                .eq(UserSession::getActive, true)
        );
    }

    public void kickSession(String jti) {
        removeSession(jti);
    }

    // --- Login attempt tracking ---

    public void recordFailedAttempt(String username) {
        Integer current = failedAttempts.getIfPresent(username);
        int attempts = (current != null ? current : 0) + 1;
        failedAttempts.put(username, attempts);
        if (securitySettingsService.isLockoutEnabled() &&
            attempts >= securitySettingsService.getLockoutMaxAttempts()) {
            lockoutExpiry.put(username,
                LocalDateTime.now().plusMinutes(securitySettingsService.getLockoutDurationMinutes()));
            log.warn("User {} locked out due to {} failed attempts", username, attempts);
        }
    }

    public void clearFailedAttempts(String username) {
        failedAttempts.invalidate(username);
        lockoutExpiry.invalidate(username);
    }

    public boolean isLockedOut(String username) {
        LocalDateTime expiry = lockoutExpiry.getIfPresent(username);
        if (expiry == null) return false;
        if (LocalDateTime.now().isAfter(expiry)) {
            lockoutExpiry.invalidate(username);
            failedAttempts.invalidate(username);
            return false;
        }
        return true;
    }

    public void unlockUser(String username) {
        failedAttempts.invalidate(username);
        lockoutExpiry.invalidate(username);
    }

    // --- IP 维度限速 ---

    public void recordFailedAttemptByIp(String ip) {
        if (ip == null || ip.isEmpty()) return;
        Integer current = ipFailedAttempts.getIfPresent(ip);
        ipFailedAttempts.put(ip, (current != null ? current : 0) + 1);
    }

    public boolean isIpThrottled(String ip) {
        if (ip == null || ip.isEmpty()) return false;
        Integer current = ipFailedAttempts.getIfPresent(ip);
        return current != null && current >= IP_MAX_ATTEMPTS;
    }

    public void clearIpFailedAttempts(String ip) {
        if (ip != null && !ip.isEmpty()) {
            ipFailedAttempts.invalidate(ip);
        }
    }

    public void resetAllLockouts() {
        failedAttempts.invalidateAll();
        lockoutExpiry.invalidateAll();
    }

}
