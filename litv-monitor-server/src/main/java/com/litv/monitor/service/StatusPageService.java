package com.litv.monitor.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
public class StatusPageService {

    private final JdbcTemplate jdbcTemplate;
    private final SecuritySettingsService securitySettingsService;

    public boolean isPublicAccessAllowed() {
        return securitySettingsService.isStatusPagePublic();
    }

    public Map<String, Object> getStatusPageData() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("monitors", getMonitorStatuses());
        result.put("overallStatus", calculateOverallStatus());
        result.put("lastUpdated", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        return result;
    }

    private List<Map<String, Object>> getMonitorStatuses() {
        List<Map<String, Object>> monitors = new ArrayList<>();

        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT m.id, m.name, m.url, m.enabled, " +
                "(SELECT el.status FROM execution_log el WHERE el.monitor_id = m.id ORDER BY el.executed_at DESC LIMIT 1) as last_status, " +
                "(SELECT el.executed_at FROM execution_log el WHERE el.monitor_id = m.id ORDER BY el.executed_at DESC LIMIT 1) as last_executed, " +
                "(SELECT el.response_time FROM execution_log el WHERE el.monitor_id = m.id ORDER BY el.executed_at DESC LIMIT 1) as last_response_time " +
                "FROM monitor m WHERE m.enabled = 1 AND m.show_on_status_page = 1 ORDER BY m.name"
            );

            for (Map<String, Object> row : rows) {
                Map<String, Object> monitor = new LinkedHashMap<>();
                monitor.put("id", row.get("id"));
                monitor.put("name", row.get("name"));
                monitor.put("url", row.get("url"));
                monitor.put("enabled", row.get("enabled"));
                monitor.put("lastStatus", row.get("last_status"));
                monitor.put("lastExecuted", row.get("last_executed"));
                monitor.put("lastResponseTime", row.get("last_response_time"));

                String status = row.get("last_status") != null ? String.valueOf(row.get("last_status")) : "UNKNOWN";
                monitor.put("status", mapStatus(status));

                monitors.add(monitor);
            }
        } catch (Exception e) {
            // ignore
        }

        return monitors;
    }

    private String mapStatus(String dbStatus) {
        if ("SUCCESS".equals(dbStatus)) {
            return "operational";
        } else if ("FAIL".equals(dbStatus)) {
            return "down";
        }
        return "unknown";
    }

    private Map<String, Object> calculateOverallStatus() {
        Map<String, Object> overall = new LinkedHashMap<>();

        try {
            Long totalEnabled = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM monitor WHERE enabled = 1 AND show_on_status_page = 1", Long.class);
            Long failCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT monitor_id) FROM execution_log " +
                "WHERE status = 'FAIL' AND executed_at >= ? " +
                "AND monitor_id IN (SELECT id FROM monitor WHERE enabled = 1 AND show_on_status_page = 1)",
                Long.class,
                java.time.LocalDateTime.now().minusHours(1)
                        .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

            totalEnabled = totalEnabled != null ? totalEnabled : 0;
            failCount = failCount != null ? failCount : 0;

            String status;
            if (failCount == 0) {
                status = "operational";
            } else if (failCount < totalEnabled) {
                status = "degraded";
            } else {
                status = "down";
            }

            overall.put("status", status);
            overall.put("totalMonitors", totalEnabled);
            overall.put("affectedMonitors", failCount);
        } catch (Exception e) {
            overall.put("status", "unknown");
            overall.put("totalMonitors", 0);
            overall.put("affectedMonitors", 0);
        }

        return overall;
    }
}
