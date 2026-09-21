package com.litv.monitor.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.DataSourceInitializer;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class DatabaseInitConfig {

    private final DataSource dataSource;

    @Bean
    public DataSourceInitializer dataSourceInitializer() {
        ResourceDatabasePopulator resourceDatabasePopulator = new ResourceDatabasePopulator();
        resourceDatabasePopulator.addScript(new ClassPathResource("db/schema.sql"));
        resourceDatabasePopulator.setSeparator(";");

        DataSourceInitializer dataSourceInitializer = new DataSourceInitializer();
        dataSourceInitializer.setDataSource(dataSource);
        dataSourceInitializer.setDatabasePopulator(resourceDatabasePopulator);
        return dataSourceInitializer;
    }

    @Bean
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean
    public Object migrateDatabase(JdbcTemplate jdbcTemplate) {
        // SQLite: 开启 WAL 模式提升并发读写性能（持久化设置，只需设置一次）
        try {
            jdbcTemplate.execute("PRAGMA journal_mode=WAL");
            jdbcTemplate.execute("PRAGMA synchronous=NORMAL");
            log.info("SQLite WAL mode enabled");
        } catch (Exception e) {
            log.warn("Failed to enable WAL mode: {}", e.getMessage());
        }

        // 迁移版本记录表：避免破坏性迁移（拷贝-删除-重建）在每次启动时重复执行
        try {
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS schema_version (" +
                    "version INTEGER PRIMARY KEY," +
                    "applied_at TIMESTAMP DEFAULT (datetime('now','localtime')))");
        } catch (Exception e) {
            log.warn("Failed to create schema_version table: {}", e.getMessage());
        }

        // Migration 1: group_variable.group_id 允许NULL
        if (!isMigrated(jdbcTemplate, 1)) {
            try {
                jdbcTemplate.execute(
                    "CREATE TABLE IF NOT EXISTS group_variable_v2 (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "group_id INTEGER," +
                    "name VARCHAR(100) NOT NULL," +
                    "value TEXT," +
                    "source_monitor_id INTEGER," +
                    "source_json_path VARCHAR(200)," +
                    "scope VARCHAR(20) DEFAULT 'GROUP'," +
                    "created_at TIMESTAMP DEFAULT (datetime('now','localtime'))," +
                    "FOREIGN KEY (group_id) REFERENCES monitor_group(id) ON DELETE CASCADE" +
                    ")"
                );
                jdbcTemplate.execute(
                    "INSERT OR IGNORE INTO group_variable_v2 (id, group_id, name, value, source_monitor_id, source_json_path, scope, created_at) " +
                    "SELECT id, group_id, name, value, source_monitor_id, source_json_path, scope, created_at FROM group_variable " +
                    "WHERE id NOT IN (SELECT id FROM group_variable_v2)"
                );
                jdbcTemplate.execute("DROP TABLE IF EXISTS group_variable");
                jdbcTemplate.execute("ALTER TABLE group_variable_v2 RENAME TO group_variable");
                markMigrated(jdbcTemplate, 1);
                log.info("Database migration completed: group_variable.group_id now allows NULL");
            } catch (Exception e) {
                log.debug("Migration 1 skipped or already applied: {}", e.getMessage());
            }
        }

        // Migration 2: execution_log 增加 variable_references, variable_settings 字段
        try { jdbcTemplate.execute("ALTER TABLE execution_log ADD COLUMN variable_references TEXT"); } catch (Exception e) { log.debug("Migration 2a skipped: {}", e.getMessage()); }
        try { jdbcTemplate.execute("ALTER TABLE execution_log ADD COLUMN variable_settings TEXT"); } catch (Exception e) { log.debug("Migration 2b skipped: {}", e.getMessage()); }

        // Migration 3: monitor 增加响应时间告警字段
        try { jdbcTemplate.execute("ALTER TABLE monitor ADD COLUMN response_time_threshold INTEGER"); } catch (Exception e) { log.debug("Migration 3a skipped: {}", e.getMessage()); }
        try { jdbcTemplate.execute("ALTER TABLE monitor ADD COLUMN response_time_consecutive_count INTEGER DEFAULT 1"); } catch (Exception e) { log.debug("Migration 3b skipped: {}", e.getMessage()); }
        try { jdbcTemplate.execute("ALTER TABLE monitor ADD COLUMN response_time_alert_enabled INTEGER DEFAULT 0"); } catch (Exception e) { log.debug("Migration 3c skipped: {}", e.getMessage()); }
        try { jdbcTemplate.execute("ALTER TABLE monitor ADD COLUMN response_time_alert_config_ids TEXT"); } catch (Exception e) { log.debug("Migration 3d skipped: {}", e.getMessage()); }

        // Migration 4: alert_log 增加详细字段
        try { jdbcTemplate.execute("ALTER TABLE alert_log ADD COLUMN monitor_name VARCHAR(100)"); } catch (Exception e) { log.debug("Migration 4a skipped: {}", e.getMessage()); }
        try { jdbcTemplate.execute("ALTER TABLE alert_log ADD COLUMN group_name VARCHAR(100)"); } catch (Exception e) { log.debug("Migration 4b skipped: {}", e.getMessage()); }
        try { jdbcTemplate.execute("ALTER TABLE alert_log ADD COLUMN alert_config_name VARCHAR(100)"); } catch (Exception e) { log.debug("Migration 4c skipped: {}", e.getMessage()); }
        try { jdbcTemplate.execute("ALTER TABLE alert_log ADD COLUMN trigger_type VARCHAR(20)"); } catch (Exception e) { log.debug("Migration 4d skipped: {}", e.getMessage()); }
        try { jdbcTemplate.execute("ALTER TABLE alert_log ADD COLUMN status_code INTEGER"); } catch (Exception e) { log.debug("Migration 4e skipped: {}", e.getMessage()); }
        try { jdbcTemplate.execute("ALTER TABLE alert_log ADD COLUMN response_time INTEGER"); } catch (Exception e) { log.debug("Migration 4f skipped: {}", e.getMessage()); }
        try { jdbcTemplate.execute("ALTER TABLE alert_log ADD COLUMN error_message TEXT"); } catch (Exception e) { log.debug("Migration 4g skipped: {}", e.getMessage()); }

        // Migration 5: alert_config 增加冷却时间字段
        try { jdbcTemplate.execute("ALTER TABLE alert_config ADD COLUMN cooldown_minutes INTEGER DEFAULT 30"); } catch (Exception e) { log.debug("Migration 5 skipped: {}", e.getMessage()); }

        // Migration 6: execution_id 字段
        try { jdbcTemplate.execute("ALTER TABLE execution_log ADD COLUMN execution_id VARCHAR(50)"); } catch (Exception e) { log.debug("Migration 6a skipped: {}", e.getMessage()); }
        try { jdbcTemplate.execute("ALTER TABLE alert_log ADD COLUMN execution_id VARCHAR(50)"); } catch (Exception e) { log.debug("Migration 6b skipped: {}", e.getMessage()); }

        // Migration 7: execution_log 增加 url 字段
        try { jdbcTemplate.execute("ALTER TABLE execution_log ADD COLUMN url TEXT"); } catch (Exception e) { log.debug("Migration 7 skipped: {}", e.getMessage()); }

        // Migration 8: ssl_certificate 增加 status 字段
        try { jdbcTemplate.execute("ALTER TABLE ssl_certificate ADD COLUMN status VARCHAR(20)"); } catch (Exception e) { log.debug("Migration 8 skipped: {}", e.getMessage()); }

        // Migration 9: domain_asset 增加 ssl_alert_on_execute 字段
        try { jdbcTemplate.execute("ALTER TABLE domain_asset ADD COLUMN ssl_alert_on_execute INTEGER DEFAULT 0"); } catch (Exception e) { log.debug("Migration 9 skipped: {}", e.getMessage()); }

        // Migration 10: monitor 增加 expected_regex, max_response_body_size
        try { jdbcTemplate.execute("ALTER TABLE monitor ADD COLUMN expected_regex VARCHAR(500)"); } catch (Exception e) { log.debug("Migration 10a skipped: {}", e.getMessage()); }
        try { jdbcTemplate.execute("ALTER TABLE monitor ADD COLUMN max_response_body_size INTEGER DEFAULT 0"); } catch (Exception e) { log.debug("Migration 10b skipped: {}", e.getMessage()); }

        // Migration 11: monitor_group 增加 running 字段
        try { jdbcTemplate.execute("ALTER TABLE monitor_group ADD COLUMN running INTEGER DEFAULT 0"); } catch (Exception e) { log.debug("Migration 11 skipped: {}", e.getMessage()); }

        // Migration 12: 创建告警静默表
        try { jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS alert_silence (id INTEGER PRIMARY KEY AUTOINCREMENT, name VARCHAR(100) NOT NULL, description TEXT, silence_type VARCHAR(20) NOT NULL DEFAULT 'ONE_TIME', start_time TIMESTAMP, end_time TIMESTAMP, cron_expression VARCHAR(50), apply_to VARCHAR(20) NOT NULL DEFAULT 'ALL', apply_ids TEXT, enabled INTEGER DEFAULT 1, created_by INTEGER, created_at TIMESTAMP DEFAULT (datetime('now','localtime')), updated_at TIMESTAMP DEFAULT (datetime('now','localtime')))"); } catch (Exception e) { log.debug("Migration 12 skipped: {}", e.getMessage()); }

        // Migration 13: 创建审计日志表
        try { jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS audit_log (id INTEGER PRIMARY KEY AUTOINCREMENT, user_id INTEGER, username VARCHAR(50), action VARCHAR(50) NOT NULL, target_type VARCHAR(50), target_id VARCHAR(50), target_name VARCHAR(200), detail TEXT, ip_address VARCHAR(50), created_at TIMESTAMP DEFAULT (datetime('now','localtime')))"); } catch (Exception e) { log.debug("Migration 13 skipped: {}", e.getMessage()); }

        // Migration 14: 创建索引
        try { jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_execution_log_monitor_id ON execution_log(monitor_id)"); } catch (Exception e) {}
        try { jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_execution_log_group_id ON execution_log(group_id)"); } catch (Exception e) {}
        try { jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_execution_log_executed_at ON execution_log(executed_at)"); } catch (Exception e) {}
        try { jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_execution_log_status ON execution_log(status)"); } catch (Exception e) {}
        try { jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_execution_log_monitor_id_executed_at ON execution_log(monitor_id, executed_at)"); } catch (Exception e) {}
        try { jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_alert_log_sent_at ON alert_log(sent_at)"); } catch (Exception e) {}
        try { jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_alert_log_trigger_type ON alert_log(trigger_type)"); } catch (Exception e) {}
        try { jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_alert_log_monitor_id ON alert_log(monitor_id)"); } catch (Exception e) {}

        // Migration 15: alert_silence 增加 schedule_config 字段
        try { jdbcTemplate.execute("ALTER TABLE alert_silence ADD COLUMN schedule_config TEXT"); } catch (Exception e) {}

        // Migration 16: audit_log 增加 request_body 字段
        try { jdbcTemplate.execute("ALTER TABLE audit_log ADD COLUMN request_body TEXT"); } catch (Exception e) {}

        // Migration 17: 重置所有任务的 running 状态
        try { jdbcTemplate.execute("UPDATE monitor_group SET running = 0 WHERE running = 1"); } catch (Exception e) {}

        // Migration 18: 安全设置表
        try { jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS security_setting (id INTEGER PRIMARY KEY AUTOINCREMENT, setting_key VARCHAR(100) NOT NULL UNIQUE, setting_value TEXT, description VARCHAR(200), created_at TIMESTAMP DEFAULT (datetime('now','localtime')), updated_at TIMESTAMP DEFAULT (datetime('now','localtime')))"); } catch (Exception e) {}

        // Migration 19: 用户会话表
        try { jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS user_session (id INTEGER PRIMARY KEY AUTOINCREMENT, user_id INTEGER NOT NULL, username VARCHAR(50) NOT NULL, jti VARCHAR(100) NOT NULL UNIQUE, ip_address VARCHAR(50), user_agent TEXT, login_at TIMESTAMP DEFAULT (datetime('now','localtime')), last_access_at TIMESTAMP DEFAULT (datetime('now','localtime')), active INTEGER DEFAULT 1, created_at TIMESTAMP DEFAULT (datetime('now','localtime')))"); } catch (Exception e) {}

        // Migration 20: alert_template 增加限频和恢复通知字段
        try {
            jdbcTemplate.execute("ALTER TABLE alert_template ADD COLUMN rate_limit_enabled INTEGER DEFAULT 0");
            jdbcTemplate.execute("ALTER TABLE alert_template ADD COLUMN rate_limit_count INTEGER DEFAULT 0");
            jdbcTemplate.execute("ALTER TABLE alert_template ADD COLUMN recovery_notify INTEGER DEFAULT 0");
        } catch (Exception e) {}

        // Migration 21: 创建告警限频状态表
        try {
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS alert_rate_limit (id INTEGER PRIMARY KEY AUTOINCREMENT, fingerprint VARCHAR(200) NOT NULL UNIQUE, current_count INTEGER DEFAULT 0, window_start TIMESTAMP, last_alert_success INTEGER DEFAULT 0, updated_at TIMESTAMP DEFAULT (datetime('now','localtime')), created_at TIMESTAMP DEFAULT (datetime('now','localtime')))");
        } catch (Exception e) {}

        // Migration 22: alert_template 增加恢复通知连续正常次数字段
        try { jdbcTemplate.execute("ALTER TABLE alert_template ADD COLUMN recovery_consecutive_count INTEGER DEFAULT 1"); } catch (Exception e) {}

        // Migration 23: alert_rate_limit 增加连续成功次数字段
        try { jdbcTemplate.execute("ALTER TABLE alert_rate_limit ADD COLUMN consecutive_success_count INTEGER DEFAULT 0"); } catch (Exception e) {}

        // Migration 24: monitor 增加签名和预请求脚本字段
        try { jdbcTemplate.execute("ALTER TABLE monitor ADD COLUMN sign_type VARCHAR(30) DEFAULT 'NONE'"); } catch (Exception e) {}
        try { jdbcTemplate.execute("ALTER TABLE monitor ADD COLUMN sign_config TEXT"); } catch (Exception e) {}
        try { jdbcTemplate.execute("ALTER TABLE monitor ADD COLUMN sign_target VARCHAR(20) DEFAULT 'HEADER'"); } catch (Exception e) {}
        try { jdbcTemplate.execute("ALTER TABLE monitor ADD COLUMN sign_field_name VARCHAR(100)"); } catch (Exception e) {}
        try { jdbcTemplate.execute("ALTER TABLE monitor ADD COLUMN pre_request_script TEXT"); } catch (Exception e) {}

        // Migration 25: 创建代理配置表
        try {
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS proxy_config (id INTEGER PRIMARY KEY AUTOINCREMENT, name VARCHAR(100) NOT NULL, proxy_type VARCHAR(10) NOT NULL DEFAULT 'HTTP', host VARCHAR(200) NOT NULL, port INTEGER NOT NULL, username VARCHAR(100), password VARCHAR(200), enabled INTEGER DEFAULT 1, active INTEGER DEFAULT 0, created_at TIMESTAMP DEFAULT (datetime('now','localtime')), updated_at TIMESTAMP DEFAULT (datetime('now','localtime')))");
        } catch (Exception e) {}

        // Migration 26: audit_log 增加 request_url 字段
        try { jdbcTemplate.execute("ALTER TABLE audit_log ADD COLUMN request_url TEXT"); } catch (Exception e) {}

        // Migration 27: 补充缺失索引
        try { jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_user_session_user_id_active ON user_session(user_id, active)"); } catch (Exception e) {}
        try { jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_monitor_enabled ON monitor(enabled)"); } catch (Exception e) {}
        try { jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_alert_log_execution_id ON alert_log(execution_id)"); } catch (Exception e) {}
        try { jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_alert_log_alert_config_id ON alert_log(alert_config_id)"); } catch (Exception e) {}
        try { jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_ssl_certificate_domain_asset_id ON ssl_certificate(domain_asset_id)"); } catch (Exception e) {}
        try { jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_group_variable_group_id ON group_variable(group_id)"); } catch (Exception e) {}

        // Migration 28: API Schema for compatibility monitoring
        try {
            jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS api_schema (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "monitor_id INTEGER NOT NULL," +
                "name VARCHAR(200) NOT NULL," +
                "description TEXT," +
                "schema_json TEXT NOT NULL," +
                "enabled INTEGER DEFAULT 1," +
                "last_check_status VARCHAR(20)," +
                "last_check_message TEXT," +
                "last_checked_at DATETIME," +
                "created_at DATETIME DEFAULT (datetime('now', 'localtime'))," +
                "updated_at DATETIME DEFAULT (datetime('now', 'localtime'))," +
                "FOREIGN KEY (monitor_id) REFERENCES monitor(id) ON DELETE CASCADE)"
            );
        } catch (Exception e) {}

        // Migration 29: Schema change history
        try {
            jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS api_schema_history (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "schema_id INTEGER NOT NULL," +
                "change_type VARCHAR(20) NOT NULL," +
                "change_description TEXT," +
                "old_schema TEXT," +
                "new_schema TEXT," +
                "created_at DATETIME DEFAULT (datetime('now', 'localtime'))," +
                "FOREIGN KEY (schema_id) REFERENCES api_schema(id) ON DELETE CASCADE)"
            );
        } catch (Exception e) {}

        // Migration 32: Inspection mode
        try {
            jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS inspection_config (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "name VARCHAR(200) NOT NULL," +
                "description TEXT," +
                "enabled INTEGER DEFAULT 1," +
                "last_run_at DATETIME," +
                "created_at DATETIME DEFAULT (datetime('now', 'localtime'))," +
                "updated_at DATETIME DEFAULT (datetime('now', 'localtime')))"
            );
        } catch (Exception e) {}

        // Migration 33: Inspection history
        try {
            jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS inspection_history (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "config_id INTEGER NOT NULL," +
                "status VARCHAR(20) NOT NULL DEFAULT 'RUNNING'," +
                "total_monitors INTEGER DEFAULT 0," +
                "success_count INTEGER DEFAULT 0," +
                "fail_count INTEGER DEFAULT 0," +
                "duration_ms INTEGER DEFAULT 0," +
                "report_content TEXT," +
                "started_at DATETIME DEFAULT (datetime('now', 'localtime'))," +
                "completed_at DATETIME," +
                "FOREIGN KEY (config_id) REFERENCES inspection_config(id) ON DELETE CASCADE)"
            );
        } catch (Exception e) {}

        // Migration 34: Inspection detail
        try {
            jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS inspection_detail (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "history_id INTEGER NOT NULL," +
                "monitor_id INTEGER NOT NULL," +
                "monitor_name VARCHAR(200)," +
                "monitor_url TEXT," +
                "status VARCHAR(20)," +
                "response_time INTEGER," +
                "status_code INTEGER," +
                "error_message TEXT," +
                "created_at DATETIME DEFAULT (datetime('now', 'localtime'))," +
                "FOREIGN KEY (history_id) REFERENCES inspection_history(id) ON DELETE CASCADE)"
            );
        } catch (Exception e) {}

        // Migration 36: alert_log 移除外键约束（日志表不应有FK）
        // 仅在未执行过时重建，避免每次启动都拷贝-删除-重建并永久丢失索引
        if (!isMigrated(jdbcTemplate, 36)) {
            try {
                jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS alert_log_v2 AS SELECT * FROM alert_log");
                jdbcTemplate.execute("DROP TABLE IF EXISTS alert_log");
                jdbcTemplate.execute(
                    "CREATE TABLE IF NOT EXISTS alert_log (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "monitor_id INTEGER," +
                    "monitor_name VARCHAR(100)," +
                    "group_id INTEGER," +
                    "group_name VARCHAR(100)," +
                    "execution_id VARCHAR(50)," +
                    "alert_config_id INTEGER," +
                    "alert_config_name VARCHAR(100)," +
                    "alert_type VARCHAR(20)," +
                    "trigger_type VARCHAR(20)," +
                    "status_code INTEGER," +
                    "response_time INTEGER," +
                    "error_message TEXT," +
                    "alert_content TEXT," +
                    "status VARCHAR(20)," +
                    "sent_at TIMESTAMP DEFAULT (datetime('now','localtime')))"
                );
                jdbcTemplate.execute("INSERT OR IGNORE INTO alert_log SELECT * FROM alert_log_v2");
                jdbcTemplate.execute("DROP TABLE IF EXISTS alert_log_v2");
                markMigrated(jdbcTemplate, 36);
            } catch (Exception e) {
                log.warn("Migration 36 failed: {}", e.getMessage());
            }
        }
        // 重建 alert_log 索引（重建表会丢失索引）
        try { jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_alert_log_sent_at ON alert_log(sent_at)"); } catch (Exception e) {}
        try { jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_alert_log_trigger_type ON alert_log(trigger_type)"); } catch (Exception e) {}
        try { jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_alert_log_monitor_id ON alert_log(monitor_id)"); } catch (Exception e) {}
        try { jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_alert_log_execution_id ON alert_log(execution_id)"); } catch (Exception e) {}
        try { jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_alert_log_alert_config_id ON alert_log(alert_config_id)"); } catch (Exception e) {}

        // Migration 37: Inspection config - add monitor_ids and schedule_cron
        try {
            jdbcTemplate.execute("ALTER TABLE inspection_config ADD COLUMN monitor_ids TEXT");
        } catch (Exception e) {}
        try {
            jdbcTemplate.execute("ALTER TABLE inspection_config ADD COLUMN schedule_cron VARCHAR(50)");
        } catch (Exception e) {}

        // Migration 38: API Schema refactor - remove monitor_id, add monitor schema alert fields
        if (!isMigrated(jdbcTemplate, 38)) {
            if (!hasColumn(jdbcTemplate, "api_schema", "monitor_id")) {
                // 目标结构已达成（无 monitor_id 列），直接标记，避免外键约束导致重复重建失败
                markMigrated(jdbcTemplate, 38);
            } else {
                try {
                    jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS api_schema_v2 AS SELECT id, name, description, schema_json, enabled, last_check_status, last_check_message, last_checked_at, created_at, updated_at FROM api_schema");
                    jdbcTemplate.execute("DROP TABLE IF EXISTS api_schema");
                    jdbcTemplate.execute(
                        "CREATE TABLE IF NOT EXISTS api_schema (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "name VARCHAR(200) NOT NULL," +
                        "description TEXT," +
                        "schema_json TEXT NOT NULL," +
                        "enabled INTEGER DEFAULT 1," +
                        "last_check_status VARCHAR(20)," +
                        "last_check_message TEXT," +
                        "last_checked_at DATETIME," +
                        "created_at DATETIME DEFAULT (datetime('now', 'localtime'))," +
                        "updated_at DATETIME DEFAULT (datetime('now', 'localtime')))"
                    );
                    jdbcTemplate.execute("INSERT OR IGNORE INTO api_schema SELECT * FROM api_schema_v2");
                    jdbcTemplate.execute("DROP TABLE IF EXISTS api_schema_v2");
                    markMigrated(jdbcTemplate, 38);
                } catch (Exception e) {
                    log.warn("Migration 38 failed: {}", e.getMessage());
                }
            }
        }
        try {
            jdbcTemplate.execute("ALTER TABLE monitor ADD COLUMN schema_alert_enabled INTEGER DEFAULT 0");
        } catch (Exception e) {}
        try {
            jdbcTemplate.execute("ALTER TABLE monitor ADD COLUMN expected_schema_json TEXT");
        } catch (Exception e) {}
        try {
            jdbcTemplate.execute("ALTER TABLE monitor ADD COLUMN schema_alert_change_types VARCHAR(100) DEFAULT 'ADDED,REMOVED,MODIFIED,BREAKING'");
        } catch (Exception e) {}
        try {
            jdbcTemplate.execute("ALTER TABLE monitor ADD COLUMN schema_alert_channel_ids TEXT");
        } catch (Exception e) {}

        // Migration 39: execution_log + inspection_detail 增加 Schema 变更信息字段
        try { jdbcTemplate.execute("ALTER TABLE execution_log ADD COLUMN schema_check_status VARCHAR(30)"); } catch (Exception e) {}
        try { jdbcTemplate.execute("ALTER TABLE inspection_detail ADD COLUMN schema_change_info TEXT"); } catch (Exception e) {}
        try { jdbcTemplate.execute("ALTER TABLE inspection_history ADD COLUMN schema_change_count INTEGER DEFAULT 0"); } catch (Exception e) {}

        // Migration 40: Fix api_schema_history - remove ON DELETE CASCADE (history should persist when schema is deleted)
        if (!isMigrated(jdbcTemplate, 40)) {
            try {
                jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS api_schema_history_v2 (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "schema_id INTEGER NOT NULL," +
                    "change_type VARCHAR(20) NOT NULL," +
                    "change_description TEXT," +
                    "old_schema TEXT," +
                    "new_schema TEXT," +
                    "created_at DATETIME DEFAULT (datetime('now', 'localtime'))," +
                    "FOREIGN KEY (schema_id) REFERENCES api_schema(id))");
                jdbcTemplate.execute("INSERT OR IGNORE INTO api_schema_history_v2 SELECT * FROM api_schema_history");
                jdbcTemplate.execute("DROP TABLE IF EXISTS api_schema_history");
                jdbcTemplate.execute("ALTER TABLE api_schema_history_v2 RENAME TO api_schema_history");
                markMigrated(jdbcTemplate, 40);
            } catch (Exception e) {
                log.debug("Migration 40 skipped: {}", e.getMessage());
            }
        }

        // Migration 41: Fix all datetime columns — replace T separator with space
        String[][] fixes = {
            {"execution_log", "executed_at"},
            {"alert_log", "sent_at"},
            {"monitor", "created_at"}, {"monitor", "updated_at"},
            {"monitor_group", "created_at"}, {"monitor_group", "updated_at"},
            {"domain_asset", "first_seen_at"}, {"domain_asset", "last_seen_at"}, {"domain_asset", "created_at"},
            {"ssl_certificate", "not_before"}, {"ssl_certificate", "not_after"}, {"ssl_certificate", "checked_at"},
            {"domain_ip_history", "first_seen_at"}, {"domain_ip_history", "last_seen_at"},
            {"audit_log", "created_at"},
            {"user_session", "login_at"}, {"user_session", "last_access_at"}, {"user_session", "created_at"},
            {"alert_silence", "start_time"}, {"alert_silence", "end_time"}, {"alert_silence", "created_at"}, {"alert_silence", "updated_at"},
            {"alert_rate_limit", "window_start"}, {"alert_rate_limit", "updated_at"}, {"alert_rate_limit", "created_at"},
            {"sys_user", "created_at"}, {"sys_user", "updated_at"},
            {"alert_config", "created_at"},
            {"inspection_config", "last_run_at"}, {"inspection_config", "created_at"}, {"inspection_config", "updated_at"},
            {"inspection_history", "started_at"}, {"inspection_history", "completed_at"},
            {"inspection_detail", "created_at"},
            {"api_schema", "last_checked_at"}, {"api_schema", "created_at"}, {"api_schema", "updated_at"},
            {"api_schema_history", "created_at"},
            {"alert_template", "created_at"},
            {"group_variable", "created_at"},
            {"proxy_config", "created_at"}, {"proxy_config", "updated_at"},
            {"security_setting", "created_at"}, {"security_setting", "updated_at"}
        };
        for (String[] fix : fixes) {
            try {
                jdbcTemplate.execute("UPDATE " + fix[0] + " SET " + fix[1] + " = REPLACE(" + fix[1] + ",'T',' ') WHERE " + fix[1] + " LIKE '%T%'");
            } catch (Exception e) {
                log.debug("Migration 41 skip {}.{}: {}", fix[0], fix[1], e.getMessage());
            }
        }

        // Migration 42: execution_log 索引优化
        try { jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_execution_log_schema_status ON execution_log(schema_check_status)"); } catch (Exception e) {}
        try { jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_execution_log_executed_schema ON execution_log(executed_at, schema_check_status)"); } catch (Exception e) {}

        // Migration 43: 移除依赖拓扑功能，删除 monitor_dependency 表
        try { jdbcTemplate.execute("DROP TABLE IF EXISTS monitor_dependency"); } catch (Exception e) {}

        // Migration 44: 统一时间格式为 yyyy-MM-dd HH:mm:ss（去 T 分隔符、去小数秒）
        for (String[] fix : fixes) {
            try {
                jdbcTemplate.execute("UPDATE " + fix[0] + " SET " + fix[1] +
                    " = substr(replace(" + fix[1] + ",'T',' '),1,19) WHERE " + fix[1] + " IS NOT NULL AND " + fix[1] + " != ''");
            } catch (Exception e) {
                log.debug("Migration 44 skip {}.{}: {}", fix[0], fix[1], e.getMessage());
            }
        }

        // Migration 45: 删除无任何实体/Mapper 引用的孤儿表
        try { jdbcTemplate.execute("DROP TABLE IF EXISTS report_config"); } catch (Exception e) {}
        try { jdbcTemplate.execute("DROP TABLE IF EXISTS report_history"); } catch (Exception e) {}
        try { jdbcTemplate.execute("DROP TABLE IF EXISTS api_schema_v2"); } catch (Exception e) {}

        // Migration 46: monitor 新增 show_on_status_page 字段
        try { jdbcTemplate.execute("ALTER TABLE monitor ADD COLUMN show_on_status_page INTEGER DEFAULT 1"); } catch (Exception e) {}

        // Migration 47: monitor 新增告警开关+失败阈值+告警渠道
        try { jdbcTemplate.execute("ALTER TABLE monitor ADD COLUMN alert_enabled INTEGER DEFAULT 0"); } catch (Exception e) {}
        try { jdbcTemplate.execute("ALTER TABLE monitor ADD COLUMN alert_consecutive_count INTEGER DEFAULT 3"); } catch (Exception e) {}
        try { jdbcTemplate.execute("ALTER TABLE monitor ADD COLUMN alert_config_ids TEXT"); } catch (Exception e) {}

        // Migration 48: monitor_group 新增失败判定标准
        try { jdbcTemplate.execute("ALTER TABLE monitor_group ADD COLUMN fail_criteria_type VARCHAR(20) DEFAULT 'ANY'"); } catch (Exception e) {}
        try { jdbcTemplate.execute("ALTER TABLE monitor_group ADD COLUMN fail_count_threshold INTEGER DEFAULT 1"); } catch (Exception e) {}
        try { jdbcTemplate.execute("ALTER TABLE monitor_group ADD COLUMN fail_percent_threshold INTEGER DEFAULT 50"); } catch (Exception e) {}

        // Migration 49: alert_template 新增兜底告警渠道
        try { jdbcTemplate.execute("ALTER TABLE alert_template ADD COLUMN fallback_channel_ids TEXT"); } catch (Exception e) {}

        // Migration 50: security_setting 新增 IP 来源配置
        try {
            jdbcTemplate.execute("INSERT OR IGNORE INTO security_setting (setting_key, setting_value, description) VALUES ('ip_source_header', 'X-Real-IP', '获取客户端IP的请求头名称，多个用逗号分隔')");
        } catch (Exception e) {}
        try {
            jdbcTemplate.execute("INSERT OR IGNORE INTO security_setting (setting_key, setting_value, description) VALUES ('ip_strict_mode', 'false', '严格IP模式：XFF多IP时是否拒绝请求')");
        } catch (Exception e) {}
        try {
            jdbcTemplate.execute("INSERT OR IGNORE INTO security_setting (setting_key, setting_value, description) VALUES ('xff_trusted_proxies', '127.0.0.1,::1', '可信代理IP列表，用于XFF解析时的信任判断')");
        } catch (Exception e) {}

        // Migration 51: 修正代理配置 enabled 与 active 不一致的历史数据
        // （存在 active=1 但 enabled=0 的记录，导致活动代理被静默忽略）
        try { jdbcTemplate.execute("UPDATE proxy_config SET enabled = 1 WHERE active = 1 AND (enabled IS NULL OR enabled = 0)"); } catch (Exception e) {}

        // Migration 52: 用户备忘录表
        try { jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS user_memo (id INTEGER PRIMARY KEY AUTOINCREMENT, username VARCHAR(50) NOT NULL UNIQUE, content TEXT, created_at TIMESTAMP DEFAULT (datetime('now','localtime')), updated_at TIMESTAMP DEFAULT (datetime('now','localtime')))"); } catch (Exception e) {}

        // Migration 53: 周期任务提醒表
        try {
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS reminder_task (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "username TEXT NOT NULL," +
                "title TEXT NOT NULL," +
                "description TEXT," +
                "category TEXT NOT NULL DEFAULT 'other'," +
                "due_date TEXT NOT NULL," +
                "recurrence_type TEXT NOT NULL DEFAULT 'once'," +
                "recurrence_config TEXT," +
                "advance_enabled INTEGER NOT NULL DEFAULT 1," +
                "advance_minutes INTEGER NOT NULL DEFAULT 60," +
                "advance_days INTEGER DEFAULT 0," +
                "alert_channel_ids TEXT," +
                "enabled INTEGER NOT NULL DEFAULT 1," +
                "completed INTEGER NOT NULL DEFAULT 0," +
                "last_reminded_at TEXT," +
                "last_snoozed_at TEXT," +
                "next_due_at TEXT," +
                "created_at TEXT NOT NULL," +
                "updated_at TEXT NOT NULL)");
        } catch (Exception e) {}
        try { jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_reminder_username ON reminder_task(username)"); } catch (Exception e) {}
        try { jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_reminder_enabled ON reminder_task(enabled, completed)"); } catch (Exception e) {}
        try { jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_reminder_next_due ON reminder_task(next_due_at)"); } catch (Exception e) {}

        // Migration 54: 多协议监控支持
        try { jdbcTemplate.execute("ALTER TABLE monitor ADD COLUMN monitor_type VARCHAR(20) DEFAULT 'HTTP'"); } catch (Exception e) {}
        try { jdbcTemplate.execute("ALTER TABLE monitor ADD COLUMN config TEXT"); } catch (Exception e) {}
        try { jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_monitor_type ON monitor(monitor_type)"); } catch (Exception e) {}

        // Migration 55: 用户强制改密标记
        try { jdbcTemplate.execute("ALTER TABLE sys_user ADD COLUMN must_change_password INTEGER DEFAULT 0"); } catch (Exception e) {}

        // Migration 56: alert_template 新增模板类型(SYSTEM/CUSTOM)
        try { jdbcTemplate.execute("ALTER TABLE alert_template ADD COLUMN template_type VARCHAR(10) DEFAULT 'CUSTOM'"); } catch (Exception e) {}
        // 将初始6个系统模板标记为SYSTEM
        try { jdbcTemplate.execute("UPDATE alert_template SET template_type = 'SYSTEM' WHERE id IN (1,2,3,4,5,6) AND (template_type IS NULL OR template_type = 'CUSTOM')"); } catch (Exception e) {}

        // Migration 57: domain_asset 新增 starred 字段
        try { jdbcTemplate.execute("ALTER TABLE domain_asset ADD COLUMN starred INTEGER DEFAULT 0"); } catch (Exception e) {}

        // Migration 58: alert_silence start_time/end_time 改为可空（支持永久静默）
        try {
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS alert_silence_new (id INTEGER PRIMARY KEY AUTOINCREMENT, name VARCHAR(100) NOT NULL, description TEXT, silence_type VARCHAR(20) NOT NULL DEFAULT 'ONE_TIME', start_time TIMESTAMP, end_time TIMESTAMP, cron_expression VARCHAR(50), schedule_config TEXT, apply_to VARCHAR(20) NOT NULL DEFAULT 'ALL', apply_ids TEXT, enabled INTEGER DEFAULT 1, created_by INTEGER, created_at TIMESTAMP DEFAULT (datetime('now','localtime')), updated_at TIMESTAMP DEFAULT (datetime('now','localtime')))");
            jdbcTemplate.execute("INSERT OR IGNORE INTO alert_silence_new SELECT id, name, description, silence_type, start_time, end_time, cron_expression, schedule_config, apply_to, apply_ids, enabled, created_by, created_at, updated_at FROM alert_silence");
            jdbcTemplate.execute("DROP TABLE IF EXISTS alert_silence");
            jdbcTemplate.execute("ALTER TABLE alert_silence_new RENAME TO alert_silence");
        } catch (Exception e) { log.debug("Migration 58 skipped: {}", e.getMessage()); }

        return null;
    }

    /** 破坏性迁移是否已执行过（避免每次启动重复拷贝-删除-重建）。 */
    private boolean isMigrated(JdbcTemplate jdbcTemplate, int version) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM schema_version WHERE version = ?", Integer.class, version);
            return count != null && count > 0;
        } catch (Exception e) {
            return false;
        }
    }

    private void markMigrated(JdbcTemplate jdbcTemplate, int version) {
        try {
            jdbcTemplate.update("INSERT OR IGNORE INTO schema_version(version) VALUES (?)", version);
        } catch (Exception e) {
            log.warn("Failed to record migration version {}: {}", version, e.getMessage());
        }
    }

    /** 表是否包含指定列（用于判断破坏性迁移是否已达成目标结构）。 */
    private boolean hasColumn(JdbcTemplate jdbcTemplate, String table, String column) {
        try {
            Boolean found = jdbcTemplate.query(
                    "PRAGMA table_info(" + table + ")",
                    rs -> {
                        while (rs.next()) {
                            if (column.equalsIgnoreCase(rs.getString("name"))) {
                                return true;
                            }
                        }
                        return false;
                    });
            return Boolean.TRUE.equals(found);
        } catch (Exception e) {
            return false;
        }
    }
}
