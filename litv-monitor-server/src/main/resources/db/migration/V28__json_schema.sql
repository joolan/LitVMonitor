-- Migration 28: JSON Schema for API compatibility monitoring
CREATE TABLE IF NOT EXISTS api_schema (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    monitor_id INTEGER NOT NULL,
    name TEXT NOT NULL,
    description TEXT,
    schema_json TEXT NOT NULL,
    enabled INTEGER DEFAULT 1,
    last_check_status TEXT,
    last_check_message TEXT,
    last_checked_at DATETIME,
    created_at DATETIME DEFAULT (datetime('now', 'localtime')),
    updated_at DATETIME DEFAULT (datetime('now', 'localtime')),
    FOREIGN KEY (monitor_id) REFERENCES monitor(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_api_schema_monitor_id ON api_schema(monitor_id);

-- Migration 29: Schema change history
CREATE TABLE IF NOT EXISTS api_schema_history (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    schema_id INTEGER NOT NULL,
    change_type TEXT NOT NULL, -- 'BREAKING', 'NON_BREAKING', 'METADATA'
    change_description TEXT,
    old_schema TEXT,
    new_schema TEXT,
    created_at DATETIME DEFAULT (datetime('now', 'localtime')),
    FOREIGN KEY (schema_id) REFERENCES api_schema(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_schema_history_schema_id ON api_schema_history(schema_id);
