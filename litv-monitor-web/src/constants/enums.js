// 触发类型
export const TRIGGER_TYPES = {
  FAIL: { label: '执行失败', tagType: 'danger' },
  RESPONSE_TIME: { label: '响应超时', tagType: 'warning' },
  GROUP_FAIL: { label: '任务失败', tagType: 'danger' },
  SSL_CERT: { label: 'SSL证书', tagType: 'warning' },
  SCHEMA_CHANGE: { label: 'Schema变更', tagType: 'warning' },
  TEST: { label: '测试', tagType: 'info' },
  ALL: { label: '通用', tagType: 'info' }
}

// 告警发送状态
export const ALERT_STATUS = {
  SENT: { label: '已发送', tagType: 'success' },
  FAILED: { label: '发送失败', tagType: 'danger' },
  SUPPRESSED: { label: '已抑制', tagType: 'warning' },
  NO_CHANNEL: { label: '无渠道', tagType: 'info' },
  CONFIG_UNAVAILABLE: { label: '渠道不可用', tagType: 'info' },
  SILENCED: { label: '已静默', tagType: 'info' }
}

// 渠道类型
export const CHANNEL_TYPES = {
  EMAIL: { label: '邮件', tagType: '' },
  WEBHOOK: { label: 'Webhook', tagType: '' }
}

// 用户角色
export const ROLES = {
  ADMIN: { label: '管理员', tagType: 'danger' },
  OPERATOR: { label: '操作员', tagType: 'warning' },
  VIEWER: { label: '只读用户', tagType: 'info' }
}

// 执行状态
export const EXECUTION_STATUS = {
  SUCCESS: { label: '成功', tagType: 'success' },
  FAIL: { label: '失败', tagType: 'danger' },
  ERROR: { label: '异常', tagType: 'danger' },
  TIMEOUT: { label: '超时', tagType: 'warning' },
  SLOW: { label: '响应慢', tagType: 'warning' }
}

// SSL证书状态
export const SSL_STATUS = [
  { value: 'VALID', label: '有效', tagType: 'success' },
  { value: 'EXPIRED', label: '已过期', tagType: 'danger' },
  { value: 'MISMATCH', label: '不匹配', tagType: 'warning' }
]

// 静默类型
export const SILENCE_TYPE = {
  ONE_TIME: { label: '一次性' },
  RECURRING: { label: '周期性' }
}

// 重复方式
export const RECURRENCE_TYPE = {
  DAILY: { label: '每天' },
  WEEKLY: { label: '每周' },
  MONTHLY: { label: '每月' }
}

// 适用范围
export const APPLY_TO = {
  ALL: { label: '全部' },
  MONITOR: { label: '监控项' },
  GROUP: { label: '任务' }
}

// 审计操作
export const AUDIT_ACTIONS = {
  CREATE: { label: '创建', tagType: 'success' },
  UPDATE: { label: '更新', tagType: 'warning' },
  DELETE: { label: '删除', tagType: 'danger' },
  LOGIN: { label: '登录', tagType: '' },
  RUN: { label: '执行', tagType: '' },
  ENABLE: { label: '启用', tagType: 'success' },
  DISABLE: { label: '禁用', tagType: 'info' },
  TEST: { label: '测试', tagType: '' },
  RESTORE: { label: '恢复', tagType: 'warning' },
  KICK: { label: '踢出会话', tagType: 'danger' },
  UNLOCK: { label: '解锁账号', tagType: 'success' },
  UPDATE_PROFILE: { label: '修改资料', tagType: 'warning' }
}
