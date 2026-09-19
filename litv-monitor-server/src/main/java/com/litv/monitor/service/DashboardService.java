package com.litv.monitor.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.litv.monitor.dto.DashboardOverview;
import com.litv.monitor.entity.AlertLog;
import com.litv.monitor.mapper.AlertLogMapper;
import com.litv.monitor.util.DateTimeUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final MonitorService monitorService;
    private final MonitorGroupService groupService;
    private final DomainAssetService domainAssetService;
    private final SslService sslService;
    private final AlertLogMapper alertLogMapper;
    private final JdbcTemplate jdbcTemplate;

    private static final java.time.format.DateTimeFormatter FMT =
            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 统一用 Java 侧（北京时间）计算时间边界，避免 SQLite datetime('now') 的时区不一致。 */
    private String sinceHours(int hours) {
        return LocalDateTime.now().minusHours(hours).format(FMT);
    }

    private String sinceMinutes(int minutes) {
        return LocalDateTime.now().minusMinutes(minutes).format(FMT);
    }

    public DashboardOverview getOverview() {
        return DashboardOverview.builder()
                .monitorEnabledCount(monitorService.countEnabledMonitors())
                .monitorDisabledCount(monitorService.countDisabledMonitors())
                .groupEnabledCount(groupService.countEnabledGroups())
                .groupDisabledCount(groupService.countDisabledGroups())
                .totalDomains(domainAssetService.countDomains())
                .sslExpiringCount(sslService.countSslExpiring())
                .sslExpiredCount(sslService.countSslExpired())
                .sslMismatchCount(sslService.countSslMismatch())
                .responseTimeoutStats(buildResponseTimeoutStats())
                .alertTrend(buildAlertTrend())
                .alertTypeDistribution(buildAlertTypeDistribution())
                .build();
    }

    private Map<String, Long> buildResponseTimeoutStats() {
        Map<String, Long> stats = new LinkedHashMap<>();
        LocalDateTime now = LocalDateTime.now();

        stats.put("10m", countDistinctResponseTimeoutMonitors(now.minusMinutes(10), now));
        stats.put("1h", countDistinctResponseTimeoutMonitors(now.minusHours(1), now));
        stats.put("6h", countDistinctResponseTimeoutMonitors(now.minusHours(6), now));

        return stats;
    }

    private List<Map<String, Object>> buildAlertTrend() {
        List<Map<String, Object>> trend = new ArrayList<>();
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT strftime('%m-%d %H:00', sent_at) as time_label, " +
                "COUNT(*) as alert_count " +
                "FROM alert_log " +
                "WHERE sent_at IS NOT NULL " +
                "AND REPLACE(sent_at, 'T', ' ') >= ? " +
                "GROUP BY strftime('%Y-%m-%d %H', sent_at) " +
                "ORDER BY strftime('%Y-%m-%d %H', sent_at) ASC",
                sinceHours(24)
            );
            for (Map<String, Object> row : rows) {
                Map<String, Object> point = new LinkedHashMap<>();
                point.put("time", String.valueOf(row.get("time_label")));
                point.put("count", row.get("alert_count"));
                trend.add(point);
            }
        } catch (Exception e) {
            // ignore
        }
        return trend;
    }

    private List<Map<String, Object>> buildAlertTypeDistribution() {
        List<Map<String, Object>> distribution = new ArrayList<>();
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT trigger_type as type, COUNT(*) as alert_count " +
                "FROM alert_log " +
                "WHERE sent_at IS NOT NULL " +
                "AND REPLACE(sent_at, 'T', ' ') >= ? " +
                "GROUP BY trigger_type " +
                "ORDER BY alert_count DESC",
                sinceHours(24)
            );
            for (Map<String, Object> row : rows) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("type", String.valueOf(row.get("type")));
                item.put("count", row.get("alert_count"));
                distribution.add(item);
            }
        } catch (Exception e) {
            // ignore
        }
        return distribution;
    }

    public List<Map<String, Object>> getUrlResponseTimeTrend(String granularity) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (!"minute".equalsIgnoreCase(granularity) && !"hour".equalsIgnoreCase(granularity)) {
            granularity = "hour";
        }
        boolean isMinute = "minute".equalsIgnoreCase(granularity);
        String timeFormat = isMinute ? "%m-%d %H:%M" : "%m-%d %H:00";
        String groupFormat = isMinute ? "%Y-%m-%d %H:%M" : "%Y-%m-%d %H";
        String sinceValue = isMinute ? sinceMinutes(60) : sinceHours(24);

        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT url, strftime('" + timeFormat + "', executed_at) as time_label, " +
                "CAST(AVG(response_time) AS INTEGER) as avg_time " +
                "FROM execution_log " +
                "WHERE REPLACE(executed_at, 'T', ' ') >= ? " +
                "AND response_time IS NOT NULL AND response_time > 0 " +
                "AND url IS NOT NULL AND url != '' " +
                "GROUP BY url, strftime('" + groupFormat + "', executed_at) " +
                "ORDER BY url, strftime('" + groupFormat + "', executed_at) ASC",
                sinceValue
            );
            for (Map<String, Object> row : rows) {
                Map<String, Object> point = new LinkedHashMap<>();
                point.put("url", String.valueOf(row.get("url")));
                point.put("time", String.valueOf(row.get("time_label")));
                point.put("avgResponseTime", row.get("avg_time"));
                result.add(point);
            }
        } catch (Exception e) {
            // ignore
        }
        return result;
    }

    private long countDistinctResponseTimeoutMonitors(LocalDateTime startTime, LocalDateTime endTime) {
        String sql = "SELECT COUNT(DISTINCT monitor_id) FROM alert_log WHERE trigger_type = 'RESPONSE_TIME' AND status IN ('SENT', 'NO_CHANNEL', 'FAILED') AND sent_at >= ? AND sent_at <= ? AND monitor_id IS NOT NULL";
        Long count = jdbcTemplate.queryForObject(sql, Long.class, startTime, endTime);
        return count != null ? count : 0;
    }

    public Map<String, Object> getUptimeStats(Long monitorId, String domain, int hours) {
        Map<String, Object> result = new LinkedHashMap<>();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startTime = now.minusHours(hours);

        StringBuilder sql = new StringBuilder(
            "SELECT " +
            "COUNT(*) as total, " +
            "SUM(CASE WHEN status = 'SUCCESS' THEN 1 ELSE 0 END) as success_count, " +
            "SUM(CASE WHEN status = 'FAIL' THEN 1 ELSE 0 END) as fail_count " +
            "FROM execution_log " +
            "WHERE executed_at >= ? " +
            "AND executed_at <= ? "
        );

        List<Object> params = new ArrayList<>();
        params.add(com.litv.monitor.util.DateTimeUtil.format(startTime));
        params.add(com.litv.monitor.util.DateTimeUtil.format(now));

        if (monitorId != null) {
            sql.append("AND monitor_id = ? ");
            params.add(monitorId);
        }
        if (domain != null && !domain.isEmpty()) {
            sql.append("AND domain = ? ");
            params.add(domain);
        }

        try {
            Map<String, Object> row = jdbcTemplate.queryForMap(sql.toString(), params.toArray());
            long total = ((Number) row.get("total")).longValue();
            long successCount = ((Number) row.get("success_count")).longValue();
            long failCount = ((Number) row.get("fail_count")).longValue();

            double uptimePercent = total > 0 ? (double) successCount / total * 100 : 0;

            result.put("total", total);
            result.put("successCount", successCount);
            result.put("failCount", failCount);
            result.put("uptimePercent", Math.round(uptimePercent * 100.0) / 100.0);
            result.put("hours", hours);
        } catch (Exception e) {
            result.put("total", 0);
            result.put("successCount", 0);
            result.put("failCount", 0);
            result.put("uptimePercent", 0);
            result.put("hours", hours);
        }

        return result;
    }

    public Map<String, Object> getSchemaCheckStats(int hours) {
        Map<String, Object> result = new LinkedHashMap<>();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startTime = now.minusHours(hours);

        try {
            Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT " +
                "COUNT(*) as total, " +
                "SUM(CASE WHEN schema_check_status IS NOT NULL " +
                "  AND schema_check_status NOT IN ('未启用','未配置Schema','非JSON响应','无响应体') THEN 1 ELSE 0 END) as checked, " +
                "SUM(CASE WHEN schema_check_status = '无变更' THEN 1 ELSE 0 END) as no_change, " +
                "SUM(CASE WHEN schema_check_status IS NOT NULL " +
                "  AND schema_check_status NOT IN ('未启用','未配置Schema','非JSON响应','无响应体','无变更','无匹配变更') " +
                "  AND schema_check_status != '' THEN 1 ELSE 0 END) as has_change, " +
                "SUM(CASE WHEN schema_check_status LIKE '%ADDED%' THEN 1 ELSE 0 END) as added, " +
                "SUM(CASE WHEN schema_check_status LIKE '%REMOVED%' THEN 1 ELSE 0 END) as removed, " +
                "SUM(CASE WHEN schema_check_status LIKE '%MODIFIED%' THEN 1 ELSE 0 END) as modified, " +
                "SUM(CASE WHEN schema_check_status LIKE '%BREAKING%' THEN 1 ELSE 0 END) as breaking " +
                "FROM execution_log WHERE executed_at >= ?",
                DateTimeUtil.format(startTime)
            );
            result.put("totalExecutions", ((Number) row.get("total")).longValue());
            result.put("checkedCount", ((Number) row.get("checked")).longValue());
            result.put("noChangeCount", ((Number) row.get("no_change")).longValue());
            result.put("hasChangeCount", ((Number) row.get("has_change")).longValue());
            result.put("addedCount", ((Number) row.get("added")).longValue());
            result.put("removedCount", ((Number) row.get("removed")).longValue());
            result.put("modifiedCount", ((Number) row.get("modified")).longValue());
            result.put("breakingCount", ((Number) row.get("breaking")).longValue());
        } catch (Exception e) {
            result.put("totalExecutions", 0);
            result.put("checkedCount", 0);
            result.put("noChangeCount", 0);
            result.put("hasChangeCount", 0);
            result.put("addedCount", 0);
            result.put("removedCount", 0);
            result.put("modifiedCount", 0);
            result.put("breakingCount", 0);
        }

        result.put("hours", hours);
        return result;
    }
}
