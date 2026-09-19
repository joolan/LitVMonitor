-- Migration: group_variable.group_id 允许NULL (集合变量不再绑定集合)
-- 使用 try/catch 方式: 如果已迁移过则跳过
CREATE TABLE IF NOT EXISTS group_variable_new (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    group_id INTEGER,
    name VARCHAR(100) NOT NULL,
    value TEXT,
    source_monitor_id INTEGER,
    source_json_path VARCHAR(200),
    scope VARCHAR(20) DEFAULT 'GROUP',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (group_id) REFERENCES monitor_group(id) ON DELETE CASCADE
);
INSERT OR IGNORE INTO group_variable_new (id, group_id, name, value, source_monitor_id, source_json_path, scope, created_at)
SELECT id, group_id, name, value, source_monitor_id, source_json_path, scope, created_at FROM group_variable WHERE group_id IS NOT NULL;
DROP TABLE IF EXISTS group_variable;
ALTER TABLE group_variable_new RENAME TO group_variable;
