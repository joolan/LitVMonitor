package com.litv.monitor.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultAlertTemplateInit implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM alert_template", Integer.class);
            log.info("Alert template count: {}", count);
            if (count != null && count == 0) {
                String sql = "INSERT INTO alert_template (id, name, trigger_type, content, cooldown_minutes, enabled, template_type) VALUES (?, ?, ?, ?, ?, ?, 'SYSTEM')";

                jdbcTemplate.update(sql, 1, "监控项失败告警", "FAIL",
                    "[LitVMonitor 监控项失败告警] {{monitorName}}\n请求地址：{{url}}\nHTTP状态码：{{statusCode}}\n错误信息：{{errorMessage}}\n执行时间：{{executedAt}}",
                    30, 1);

                jdbcTemplate.update(sql, 2, "响应超时告警", "RESPONSE_TIME",
                    "[LitVMonitor 响应超时告警] {{monitorName}}\n请求地址：{{url}}\n响应时间：{{responseTime}}\n执行时间：{{executedAt}}",
                    30, 1);

                jdbcTemplate.update(sql, 3, "任务失败告警", "GROUP_FAIL",
                    "[LitVMonitor 任务失败告警] {{monitorName}}\n任务执行失败：{{errorMessage}}\n执行时间：{{executedAt}}",
                    60, 1);

                jdbcTemplate.update(sql, 4, "SSL证书异常告警", "SSL_CERT",
                    "[LitVMonitor SSL证书异常告警] {{domain}}\n异常状态：{{status}}\n错误信息：{{errorMessage}}\n执行时间：{{executedAt}}",
                    1440, 1);

                jdbcTemplate.update(sql, 5, "通用告警模板", "ALL",
                    "[LitVMonitor 通用告警] {{monitorName}}\n触发类型：{{triggerType}}\n请求地址：{{url}}\n告警内容：{{errorMessage}}\n执行时间：{{executedAt}}",
                    30, 0);

                jdbcTemplate.update(sql, 6, "API Schema变更告警", "SCHEMA_CHANGE",
                    "[LitVMonitor API Schema变更告警] {{monitorName}}\n请求地址：{{url}}\n变更详情：{{errorMessage}}\n执行时间：{{executedAt}}",
                    30, 1);

                log.info("Inserted 6 default alert templates");
            } else {
                ensureSchemaChangeTemplateExists();
            }
        } catch (Exception e) {
            log.error("Failed to init default alert templates: {}", e.getMessage(), e);
        }
    }

    private void ensureSchemaChangeTemplateExists() {
        Integer exists = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM alert_template WHERE trigger_type = 'SCHEMA_CHANGE'", Integer.class);
        if (exists != null && exists == 0) {
            jdbcTemplate.update(
                "INSERT INTO alert_template (id, name, trigger_type, content, cooldown_minutes, enabled) VALUES (?, ?, ?, ?, ?, ?)",
                getNextId(), "API Schema变更告警", "SCHEMA_CHANGE",
                "[LitVMonitor API Schema变更告警] {{monitorName}}\n请求地址：{{url}}\n变更详情：{{errorMessage}}\n执行时间：{{executedAt}}",
                30, 1);
            log.info("Inserted missing SCHEMA_CHANGE alert template");
        }
    }

    private Long getNextId() {
        Long maxId = jdbcTemplate.queryForObject(
            "SELECT COALESCE(MAX(id), 0) FROM alert_template", Long.class);
        return maxId + 1;
    }
}
