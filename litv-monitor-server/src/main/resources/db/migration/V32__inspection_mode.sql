-- Migration 32: Inspection mode
CREATE TABLE IF NOT EXISTS inspection_config (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    enabled INTEGER DEFAULT 1,
    last_run_at DATETIME,
    created_at DATETIME DEFAULT (datetime('now', 'localtime')),
    updated_at DATETIME DEFAULT (datetime('now', 'localtime'))
);

-- Migration 33: Inspection history
CREATE TABLE IF NOT EXISTS inspection_history (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    config_id INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'RUNNING',
    total_monitors INTEGER DEFAULT 0,
    success_count INTEGER DEFAULT 0,
    fail_count INTEGER DEFAULT 0,
    duration_ms INTEGER DEFAULT 0,
    report_content TEXT,
    started_at DATETIME DEFAULT (datetime('now', 'localtime')),
    completed_at DATETIME,
    FOREIGN KEY (config_id) REFERENCES inspection_config(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_inspection_history_config_id ON inspection_history(config_id);

-- Migration 34: Inspection detail
CREATE TABLE IF NOT EXISTS inspection_detail (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    history_id INTEGER NOT NULL,
    monitor_id INTEGER NOT NULL,
    monitor_name VARCHAR(200),
    monitor_url TEXT,
    status VARCHAR(20),
    response_time INTEGER,
    status_code INTEGER,
    error_message TEXT,
    created_at DATETIME DEFAULT (datetime('now', 'localtime')),
    FOREIGN KEY (history_id) REFERENCES inspection_history(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_inspection_detail_history_id ON inspection_detail(history_id);
