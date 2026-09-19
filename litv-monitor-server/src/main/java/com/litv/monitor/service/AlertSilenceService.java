package com.litv.monitor.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.litv.monitor.entity.AlertSilence;
import com.litv.monitor.mapper.AlertSilenceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlertSilenceService {

    private final AlertSilenceMapper alertSilenceMapper;
    private final ObjectMapper objectMapper;

    private final com.github.benmanes.caffeine.cache.Cache<String, List<AlertSilence>> enabledCache =
            com.github.benmanes.caffeine.cache.Caffeine.newBuilder()
                    .expireAfterWrite(30, java.util.concurrent.TimeUnit.SECONDS)
                    .maximumSize(1)
                    .build();

    private void invalidateCache() {
        enabledCache.invalidateAll();
    }

    public Page<AlertSilence> listSilences(Page<AlertSilence> page) {
        LambdaQueryWrapper<AlertSilence> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(AlertSilence::getCreatedAt);
        return alertSilenceMapper.selectPage(page, wrapper);
    }

    public List<AlertSilence> listAllEnabled() {
        return enabledCache.get("enabled", k -> alertSilenceMapper.selectList(
            new LambdaQueryWrapper<AlertSilence>().eq(AlertSilence::getEnabled, true)
        ));
    }

    public AlertSilence getSilenceById(Long id) {
        return alertSilenceMapper.selectById(id);
    }

    public AlertSilence createSilence(AlertSilence dto) {
        AlertSilence silence = new AlertSilence();
        BeanUtils.copyProperties(dto, silence);
        silence.setId(null);
        alertSilenceMapper.insert(silence);
        invalidateCache();
        return alertSilenceMapper.selectById(silence.getId());
    }

    public AlertSilence updateSilence(Long id, AlertSilence dto) {
        AlertSilence existing = alertSilenceMapper.selectById(id);
        if (existing == null) return null;
        BeanUtils.copyProperties(dto, existing);
        existing.setId(id);
        alertSilenceMapper.updateById(existing);
        invalidateCache();
        return alertSilenceMapper.selectById(id);
    }

    public boolean deleteSilence(Long id) {
        boolean deleted = alertSilenceMapper.deleteById(id) > 0;
        invalidateCache();
        return deleted;
    }

    public boolean toggleSilence(Long id, boolean enabled) {
        AlertSilence existing = alertSilenceMapper.selectById(id);
        if (existing == null) return false;
        existing.setEnabled(enabled);
        alertSilenceMapper.updateById(existing);
        invalidateCache();
        return true;
    }

    public boolean isSilenced(Long monitorId, Long groupId) {
        LocalDateTime now = LocalDateTime.now();
        List<AlertSilence> silences = listAllEnabled();
        for (AlertSilence s : silences) {
            if (!isInTimeWindow(s, now)) continue;
            if ("ALL".equals(s.getApplyTo())) return true;
            if (monitorId != null && "MONITOR".equals(s.getApplyTo())) {
                if (isInApplyIds(s.getApplyIds(), monitorId)) return true;
            }
            if (groupId != null && "GROUP".equals(s.getApplyTo())) {
                if (isInApplyIds(s.getApplyIds(), groupId)) return true;
            }
        }
        return false;
    }

    private boolean isInTimeWindow(AlertSilence silence, LocalDateTime now) {
        // Check date range first
        if (silence.getStartTime() != null && now.toLocalDate().isBefore(silence.getStartTime().toLocalDate())) return false;
        if (silence.getEndTime() != null && now.toLocalDate().isAfter(silence.getEndTime().toLocalDate())) return false;

        // Check time windows from scheduleConfig
        String configJson = silence.getScheduleConfig();
        if (configJson != null && !configJson.isEmpty()) {
            try {
                com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(configJson);
                com.fasterxml.jackson.databind.JsonNode windows = root.has("timeWindows") ? root.get("timeWindows") : null;
                String recurrenceType = root.has("recurrenceType") ? root.get("recurrenceType").asText() : null;
                if (windows != null && windows.isArray() && windows.size() > 0) {
                    java.time.LocalTime current = now.toLocalTime();
                    for (com.fasterxml.jackson.databind.JsonNode window : windows) {
                        if ("WEEKLY".equals(recurrenceType)) {
                            com.fasterxml.jackson.databind.JsonNode days = window.has("days") ? window.get("days") : null;
                            if (days == null || !days.isArray() || days.size() == 0) continue;
                            int dayOfWeek = now.getDayOfWeek().getValue();
                            boolean dayMatch = false;
                            for (com.fasterxml.jackson.databind.JsonNode day : days) {
                                if (day.asInt() == dayOfWeek) { dayMatch = true; break; }
                            }
                            if (!dayMatch) continue;
                        }

                        if ("MONTHLY".equals(recurrenceType)) {
                            com.fasterxml.jackson.databind.JsonNode dateNode = window.has("dateOfMonth") ? window.get("dateOfMonth") : null;
                            if (dateNode == null) continue;
                            if (dateNode.asInt() != now.getDayOfMonth()) continue;
                        }

                        String startTimeStr = window.get("start").asText();
                        String endTimeStr = window.get("end").asText();
                        java.time.LocalTime start = java.time.LocalTime.parse(startTimeStr);
                        java.time.LocalTime end = java.time.LocalTime.parse(endTimeStr);

                        boolean timeMatch;
                        if (start.isBefore(end) || start.equals(end)) {
                            timeMatch = !current.isBefore(start) && !current.isAfter(end);
                        } else {
                            // Overnight window (e.g., 22:00 - 06:00)
                            timeMatch = !current.isBefore(start) || !current.isAfter(end);
                        }
                        if (timeMatch) return true;
                    }
                    return false; // Has time windows but none matched
                }
            } catch (Exception e) {
                log.warn("Failed to parse schedule config for silence: {}", e.getMessage());
            }
        }

        // Fallback: no time windows configured, use startTime/endTime as full datetime
        if (silence.getStartTime() != null && silence.getEndTime() != null) {
            return now.isAfter(silence.getStartTime()) && now.isBefore(silence.getEndTime());
        }
        if (silence.getStartTime() != null && silence.getEndTime() == null) {
            return now.isAfter(silence.getStartTime());
        }
        return false;
    }

    private boolean isInApplyIds(String applyIds, Long id) {
        if (applyIds == null || applyIds.isEmpty()) return false;
        for (String idStr : applyIds.split(",")) {
            try {
                if (Long.parseLong(idStr.trim()) == id) return true;
            } catch (NumberFormatException ignored) {}
        }
        return false;
    }
}
