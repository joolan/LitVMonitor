-- LitVMonitor Database Schema

-- ========================================
-- 用户表
-- ========================================
CREATE TABLE IF NOT EXISTS sys_user (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    nickname VARCHAR(100),
    email VARCHAR(100),
    role VARCHAR(20) NOT NULL DEFAULT 'VIEWER',
    enabled INTEGER DEFAULT 1,
    created_at TIMESTAMP DEFAULT (datetime('now','localtime')),
    updated_at TIMESTAMP DEFAULT (datetime('now','localtime'))
);

-- ========================================
-- 监控项表
-- ========================================
CREATE TABLE IF NOT EXISTS monitor (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    url VARCHAR(2048) NOT NULL,
    method VARCHAR(10) DEFAULT 'GET',
    headers TEXT,
    body TEXT,
    body_type VARCHAR(20),
    expected_status INTEGER,
    expected_text VARCHAR(500),
    json_path VARCHAR(200),
    json_expected VARCHAR(500),
    timeout INTEGER DEFAULT 30,
    retry_count INTEGER DEFAULT 1,
    enabled INTEGER DEFAULT 1,
    variable_extract_config TEXT,
    response_time_threshold INTEGER,
    response_time_consecutive_count INTEGER DEFAULT 1,
    response_time_alert_enabled INTEGER DEFAULT 0,
    response_time_alert_config_ids TEXT,
    created_by INTEGER,
    created_at TIMESTAMP DEFAULT (datetime('now','localtime')),
    updated_at TIMESTAMP DEFAULT (datetime('now','localtime'))
);

-- ========================================
-- 监控集合表
-- ========================================
CREATE TABLE IF NOT EXISTS monitor_group (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    cron_expression VARCHAR(50),
    schedule_type VARCHAR(20) DEFAULT 'INTERVAL',
    schedule_config TEXT,
    retry_interval INTEGER DEFAULT 60,
    enabled INTEGER DEFAULT 1,
    alert_on_fail INTEGER DEFAULT 0,
    fail_threshold INTEGER DEFAULT 1,
    alert_config_ids TEXT,
    created_by INTEGER,
    created_at TIMESTAMP DEFAULT (datetime('now','localtime')),
    updated_at TIMESTAMP DEFAULT (datetime('now','localtime'))
);

-- ========================================
-- 集合-监控项关联表
-- ========================================
CREATE TABLE IF NOT EXISTS group_monitor (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    group_id INTEGER NOT NULL,
    monitor_id INTEGER NOT NULL,
    sort_order INTEGER DEFAULT 0,
    continue_on_fail INTEGER DEFAULT 1,
    is_group_start INTEGER DEFAULT 0,
    variable_scope VARCHAR(20) DEFAULT 'GROUP',
    created_at TIMESTAMP DEFAULT (datetime('now','localtime')),
    FOREIGN KEY (group_id) REFERENCES monitor_group(id) ON DELETE CASCADE,
    FOREIGN KEY (monitor_id) REFERENCES monitor(id) ON DELETE CASCADE
);

-- ========================================
-- 全局变量表
-- ========================================
CREATE TABLE IF NOT EXISTS global_variable (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name VARCHAR(100) NOT NULL UNIQUE,
    value TEXT,
    description VARCHAR(500),
    is_secret INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT (datetime('now','localtime')),
    updated_at TIMESTAMP DEFAULT (datetime('now','localtime'))
);

-- ========================================
-- 集合变量表
-- ========================================
CREATE TABLE IF NOT EXISTS group_variable (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    group_id INTEGER,
    name VARCHAR(100) NOT NULL,
    value TEXT,
    source_monitor_id INTEGER,
    source_json_path VARCHAR(200),
    scope VARCHAR(20) DEFAULT 'GROUP',
    created_at TIMESTAMP DEFAULT (datetime('now','localtime')),
    FOREIGN KEY (group_id) REFERENCES monitor_group(id) ON DELETE CASCADE
);

-- ========================================
-- 执行记录表
-- ========================================
CREATE TABLE IF NOT EXISTS execution_log (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    monitor_id INTEGER,
    monitor_name VARCHAR(100),
    group_id INTEGER,
    execution_id VARCHAR(50),
    url TEXT,
    domain VARCHAR(255),
    ip_address VARCHAR(50),
    status VARCHAR(20) NOT NULL,
    status_code INTEGER,
    response_time INTEGER,
    response_body TEXT,
    request_headers TEXT,
    request_body TEXT,
    error_message TEXT,
    variable_references TEXT,
    variable_settings TEXT,
    executed_at TIMESTAMP DEFAULT (datetime('now','localtime')),
    FOREIGN KEY (monitor_id) REFERENCES monitor(id) ON DELETE SET NULL,
    FOREIGN KEY (group_id) REFERENCES monitor_group(id) ON DELETE SET NULL
);

-- ========================================
-- 告警配置表
-- ========================================
CREATE TABLE IF NOT EXISTS alert_config (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name VARCHAR(100) NOT NULL,
    type VARCHAR(20) NOT NULL,
    config TEXT NOT NULL,
    alert_template TEXT,
    enabled INTEGER DEFAULT 1,
    cooldown_minutes INTEGER DEFAULT 30,
    created_at TIMESTAMP DEFAULT (datetime('now','localtime'))
);

-- ========================================
-- 告警记录表
-- ========================================
CREATE TABLE IF NOT EXISTS alert_log (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    monitor_id INTEGER,
    monitor_name VARCHAR(100),
    group_id INTEGER,
    group_name VARCHAR(100),
    execution_id VARCHAR(50),
    alert_config_id INTEGER,
    alert_config_name VARCHAR(100),
    alert_type VARCHAR(20),
    trigger_type VARCHAR(20),
    status_code INTEGER,
    response_time INTEGER,
    error_message TEXT,
    alert_content TEXT,
    status VARCHAR(20),
    sent_at TIMESTAMP DEFAULT (datetime('now','localtime')),
    FOREIGN KEY (monitor_id) REFERENCES monitor(id) ON DELETE SET NULL,
    FOREIGN KEY (group_id) REFERENCES monitor_group(id) ON DELETE SET NULL
);

-- ========================================
-- 域名资产表
-- ========================================
CREATE TABLE IF NOT EXISTS domain_asset (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    domain VARCHAR(255) NOT NULL,
    ip_address VARCHAR(50),
    port INTEGER DEFAULT 443,
    first_seen_at TIMESTAMP DEFAULT (datetime('now','localtime')),
    last_seen_at TIMESTAMP DEFAULT (datetime('now','localtime')),
    monitor_count INTEGER DEFAULT 0,
    is_alive INTEGER DEFAULT 1,
    ssl_alert_enabled INTEGER DEFAULT 0,
    ssl_alert_config_ids TEXT,
    ssl_alert_days_before INTEGER DEFAULT 30,
    ssl_alert_one_day_before INTEGER DEFAULT 1,
    ssl_alert_on_execute INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT (datetime('now','localtime')),
    UNIQUE(domain, port)
);

-- ========================================
-- SSL证书表
-- ========================================
CREATE TABLE IF NOT EXISTS ssl_certificate (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    domain_asset_id INTEGER,
    domain VARCHAR(255) NOT NULL,
    port INTEGER DEFAULT 443,
    issuer VARCHAR(255),
    subject VARCHAR(255),
    serial_number VARCHAR(100),
    not_before TIMESTAMP,
    not_after TIMESTAMP,
    remaining_days INTEGER,
    fingerprint VARCHAR(100),
    is_valid INTEGER DEFAULT 1,
    status VARCHAR(20),
    checked_at TIMESTAMP DEFAULT (datetime('now','localtime')),
    FOREIGN KEY (domain_asset_id) REFERENCES domain_asset(id) ON DELETE CASCADE
);

-- 默认管理员由程序启动时自动创建

-- ========================================
-- 域名IP历史记录表
-- ========================================
CREATE TABLE IF NOT EXISTS domain_ip_history (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    domain VARCHAR(255) NOT NULL,
    ip_address VARCHAR(50) NOT NULL,
    port INTEGER DEFAULT 443,
    first_seen_at TIMESTAMP DEFAULT (datetime('now','localtime')),
    last_seen_at TIMESTAMP DEFAULT (datetime('now','localtime')),
    UNIQUE(domain, ip_address, port)
);

-- ========================================
-- 告警模板表 (按触发类型配置)
-- ========================================
CREATE TABLE IF NOT EXISTS alert_template (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name VARCHAR(100) NOT NULL,
    trigger_type VARCHAR(20) NOT NULL,
    content TEXT,
    cooldown_minutes INTEGER DEFAULT 30,
    enabled INTEGER DEFAULT 1,
    rate_limit_enabled INTEGER DEFAULT 0,
    rate_limit_count INTEGER DEFAULT 5,
    recovery_notify INTEGER DEFAULT 0,
    template_type VARCHAR(10) DEFAULT 'CUSTOM',
    created_at TIMESTAMP DEFAULT (datetime('now','localtime'))
);

-- ========================================
-- 安全设置表
-- ========================================
CREATE TABLE IF NOT EXISTS security_setting (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    setting_key VARCHAR(100) NOT NULL UNIQUE,
    setting_value TEXT,
    description VARCHAR(200),
    created_at TIMESTAMP DEFAULT (datetime('now','localtime')),
    updated_at TIMESTAMP DEFAULT (datetime('now','localtime'))
);

-- ========================================
-- 用户会话表
-- ========================================
CREATE TABLE IF NOT EXISTS user_session (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id INTEGER NOT NULL,
    username VARCHAR(50) NOT NULL,
    jti VARCHAR(100) NOT NULL UNIQUE,
    ip_address VARCHAR(50),
    user_agent TEXT,
    login_at TIMESTAMP DEFAULT (datetime('now','localtime')),
    last_access_at TIMESTAMP DEFAULT (datetime('now','localtime')),
    active INTEGER DEFAULT 1,
    created_at TIMESTAMP DEFAULT (datetime('now','localtime'))
);
