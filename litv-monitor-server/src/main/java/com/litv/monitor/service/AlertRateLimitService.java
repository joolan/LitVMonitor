package com.litv.monitor.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.litv.monitor.entity.AlertRateLimit;
import com.litv.monitor.entity.AlertTemplate;
import com.litv.monitor.mapper.AlertRateLimitMapper;
import com.litv.monitor.mapper.AlertTemplateMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlertRateLimitService {

    private final AlertRateLimitMapper rateLimitMapper;
    private final AlertTemplateMapper alertTemplateMapper;

    /**
     * Build fingerprint for alert deduplication.
     * Same fingerprint = same alert source.
     */
    public String buildFingerprint(String triggerType, Long monitorId, Long groupId, String domain, String url) {
        return switch (triggerType) {
            case "SSL_CERT" -> "domain:" + (domain != null ? domain : "unknown");
            case "GROUP_FAIL" -> "group:" + (groupId != null ? groupId : "0");
            default -> "monitor:" + (monitorId != null ? monitorId : "0") + ":group:" + (groupId != null ? groupId : "0");
        };
    }

    /**
     * Check if alert is rate-limited based on the template's config.
     * Returns true if should SUPPRESS notification.
     */
    public boolean isRateLimited(String fingerprint, String triggerType) {
        AlertTemplate template = findTemplateWithRateLimit(triggerType);
        if (template == null) return false;

        int limit = template.getRateLimitCount() != null ? template.getRateLimitCount() : 0;
        if (limit <= 0) return false;

        AlertRateLimit record = getOrCreateRecord(fingerprint);
        return record.getCurrentCount() >= limit;
    }

    /**
     * Increment alert count. Returns true if this is a recovery (was failing, now success).
     */
    @Transactional
    public boolean incrementAndCheckRecovery(String fingerprint, boolean isSuccess) {
        AlertRateLimit record = getOrCreateRecord(fingerprint);

        boolean wasFailing = Boolean.FALSE.equals(record.getLastAlertSuccess());
        boolean isRecovery = isSuccess && wasFailing && record.getCurrentCount() > 0;

        LocalDateTime now = LocalDateTime.now();
        if (isSuccess) {
            rateLimitMapper.resetOnSuccess(fingerprint, now);
        } else {
            rateLimitMapper.incrementFailure(fingerprint, now);
        }
        return isRecovery;
    }

    /**
     * Check if this is a recovery (was failing, now success) WITHOUT incrementing count.
     * Also checks if consecutive success count meets the required threshold.
     */
    public boolean checkRecovery(String fingerprint, boolean isSuccess, String triggerType) {
        AlertRateLimit record = getOrCreateRecord(fingerprint);
        // Use currentCount > 0 to determine if we're in a failing state
        // (lastAlertSuccess can flip to true on first success, but currentCount stays > 0 until resetCount)
        boolean wasFailing = record.getCurrentCount() > 0;
        boolean hasAlertHistory = wasFailing;
        
        if (!isSuccess || !hasAlertHistory) {
            return false;
        }
        
        // Check consecutive success count requirement
        int requiredCount = getRecoveryConsecutiveCount(triggerType);
        int currentConsecutive = record.getConsecutiveSuccessCount() != null ? record.getConsecutiveSuccessCount() : 0;
        
        // If required count is 1, immediate recovery is allowed
        if (requiredCount <= 1) {
            return true;
        }
        
        // Need to track consecutive successes (current success not yet counted, so +1)
        return (currentConsecutive + 1) >= requiredCount;
    }

    /**
     * Increment alert count and track consecutive success count.
     */
    @Transactional
    public void incrementCount(String fingerprint, boolean isSuccess) {
        // Ensure the row exists, then update atomically to avoid lost updates.
        getOrCreateRecord(fingerprint);
        LocalDateTime now = LocalDateTime.now();
        if (isSuccess) {
            rateLimitMapper.incrementSuccess(fingerprint, now);
        } else {
            rateLimitMapper.incrementFailure(fingerprint, now);
        }
    }

    /**
     * Reset rate limit count (called after recovery notification sent).
     */
    @Transactional
    public void resetCount(String fingerprint) {
        getOrCreateRecord(fingerprint);
        rateLimitMapper.resetAll(fingerprint, LocalDateTime.now());
    }

    /**
     * Get recovery consecutive count from template.
     */
    private int getRecoveryConsecutiveCount(String triggerType) {
        AlertTemplate template = findTemplateWithRateLimit(triggerType);
        if (template == null) return 1;
        return template.getRecoveryConsecutiveCount() != null ? template.getRecoveryConsecutiveCount() : 1;
    }

    /**
     * Check if recovery notify is enabled for this trigger type's template.
     */
    public boolean isRecoveryNotifyEnabled(String triggerType) {
        AlertTemplate template = findTemplateWithRateLimit(triggerType);
        if (template == null) return false;
        return Boolean.TRUE.equals(template.getRecoveryNotify());
    }

    private AlertRateLimit getOrCreateRecord(String fingerprint) {
        AlertRateLimit record = rateLimitMapper.selectOne(
            new LambdaQueryWrapper<AlertRateLimit>()
                .eq(AlertRateLimit::getFingerprint, fingerprint)
        );
        if (record == null) {
            try {
                record = new AlertRateLimit();
                record.setFingerprint(fingerprint);
                record.setCurrentCount(0);
                record.setLastAlertSuccess(null);
                rateLimitMapper.insert(record);
                // Re-select to get the ID and avoid stale state
                record = rateLimitMapper.selectOne(
                    new LambdaQueryWrapper<AlertRateLimit>()
                        .eq(AlertRateLimit::getFingerprint, fingerprint)
                );
            } catch (Exception e) {
                // Another thread inserted first — just select it
                record = rateLimitMapper.selectOne(
                    new LambdaQueryWrapper<AlertRateLimit>()
                        .eq(AlertRateLimit::getFingerprint, fingerprint)
                );
                if (record == null) {
                    log.error("Failed to create or find rate limit record for fingerprint: {}", fingerprint, e);
                    // Return a default in-memory record to avoid NPE
                    record = new AlertRateLimit();
                    record.setFingerprint(fingerprint);
                    record.setCurrentCount(0);
                }
            }
        }
        return record;
    }

    /**
     * Find an enabled template with rate limit enabled for the given triggerType.
     * Falls back to ALL type if no exact match.
     */
    private AlertTemplate findTemplateWithRateLimit(String triggerType) {
        // Try exact triggerType match first
        AlertTemplate template = alertTemplateMapper.selectOne(
            new LambdaQueryWrapper<AlertTemplate>()
                .eq(AlertTemplate::getTriggerType, triggerType)
                .eq(AlertTemplate::getEnabled, true)
                .eq(AlertTemplate::getRateLimitEnabled, true)
                .orderByAsc(AlertTemplate::getId)
                .last("LIMIT 1")
        );
        if (template != null) return template;

        // Fallback to ALL type
        if (!"ALL".equals(triggerType)) {
            template = alertTemplateMapper.selectOne(
                new LambdaQueryWrapper<AlertTemplate>()
                    .eq(AlertTemplate::getTriggerType, "ALL")
                    .eq(AlertTemplate::getEnabled, true)
                    .eq(AlertTemplate::getRateLimitEnabled, true)
                    .orderByAsc(AlertTemplate::getId)
                    .last("LIMIT 1")
            );
        }
        return template;
    }
}
