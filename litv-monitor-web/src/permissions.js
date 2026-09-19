/**
 * 前端权限配置 - 集中定义菜单和按钮权限
 * 
 * 使用方式：
 * - 菜单：MainLayout.vue 根据 MENUS 渲染侧边栏
 * - 按钮：组件中使用 v-permission="'monitor:create'" 或 hasPermission('monitor:create')
 * - 路由：router/index.js 根据 MENU_MAP 做路由守卫
 * 
 * 扩展方式：
 * - 新增菜单：在 MENUS 数组中添加一项，指定 path/title/icon/roles
 * - 新增按钮：在 BUTTONS 对象中添加 '模块:操作' 键，指定 roles
 * - 新增角色：在 ROLES 中添加角色定义，然后在 MENUS/BUTTONS 中引用
 */

// 角色定义
export const ROLE_LIST = [
  { value: 'ADMIN', label: '管理员', tagType: 'danger' },
  { value: 'OPERATOR', label: '操作员', tagType: 'warning' },
  { value: 'VIEWER', label: '只读用户', tagType: 'info' }
]

// 所有角色 key
export const ALL_ROLES = ROLE_LIST.map(r => r.value)

// 菜单权限配置
// roles: 允许看到此菜单的角色列表，空数组或不填表示所有角色可见
export const MENUS = [
  { key: 'dashboard', path: '/dashboard', title: '仪表盘', icon: 'Monitor', roles: [] },
  { key: 'monitor', path: '/monitor', title: '监控项管理', icon: 'Connection', roles: [] },
  { key: 'group', path: '/group', title: '监控任务', icon: 'Folder', roles: [] },
  { key: 'domain', path: '/domain', title: '域名证书', icon: 'OfficeBuilding', roles: [] },
  { key: 'log', path: '/log', title: '监控执行日志', icon: 'Document', roles: [] },
  // 监控告警子菜单
  { key: 'alert-template', path: '/alert-template', title: '告警模板', icon: 'Bell', roles: ['ADMIN', 'OPERATOR'], group: 'alert-group' },
  { key: 'alert-channel', path: '/alert-channel', title: '告警渠道', icon: 'Bell', roles: ['ADMIN', 'OPERATOR'], group: 'alert-group' },
  { key: 'alert-log', path: '/alert-log', title: '告警记录', icon: 'Bell', roles: [], group: 'alert-group' },
  { key: 'alert-silence', path: '/alert-silence', title: '告警静默', icon: 'Bell', roles: ['ADMIN', 'OPERATOR'], group: 'alert-group' },
  { key: 'inspection', path: '/inspection', title: '巡检模式', icon: 'Monitor', roles: ['ADMIN', 'OPERATOR'], group: 'alert-group' },
  { key: 'reminder', path: '/reminder', title: '周期提醒', icon: 'AlarmClock', roles: [] },
  // 系统管理子菜单
  { key: 'variable', path: '/variable', title: '变量管理', icon: 'Setting', roles: ['ADMIN', 'OPERATOR'], group: 'system-group' },
  { key: 'proxy', path: '/proxy', title: '代理设置', icon: 'Connection', roles: ['ADMIN'], group: 'system-group' },
  { key: 'api-schema', path: '/api-schema', title: 'API Schema', icon: 'Document', roles: ['ADMIN', 'OPERATOR'], group: 'system-group' },
  { key: 'user', path: '/user', title: '用户管理', icon: 'User', roles: ['ADMIN'], group: 'system-group' },
  { key: 'security-settings', path: '/security-settings', title: '安全设置', icon: 'Lock', roles: ['ADMIN'], group: 'system-group' },
  { key: 'backup', path: '/backup', title: '数据备份', icon: 'Download', roles: ['ADMIN'], group: 'system-group' },
  { key: 'audit-log', path: '/audit-log', title: '审计日志', icon: 'Document', roles: ['ADMIN', 'OPERATOR'], group: 'system-group' },
  // 使用手册
  { key: 'manual', path: '/manual', title: '使用手册', icon: 'Reading', roles: [] }
]

// 侧边栏分组定义
export const MENU_GROUPS = [
  { key: 'alert-group', title: '监控告警', icon: 'Bell' },
  { key: 'system-group', title: '系统管理', icon: 'Setting' }
]

// 按钮/操作权限配置
// key 格式: '模块:操作'，value.roles 为允许执行的角色列表
export const BUTTONS = {
  // 监控项
  'monitor:create': { roles: ['ADMIN', 'OPERATOR'] },
  'monitor:edit': { roles: ['ADMIN', 'OPERATOR'] },
  'monitor:delete': { roles: ['ADMIN', 'OPERATOR'] },
  'monitor:test': { roles: ['ADMIN', 'OPERATOR'] },
  'monitor:copy': { roles: ['ADMIN', 'OPERATOR'] },
  'monitor:batch-status': { roles: ['ADMIN', 'OPERATOR'] },

  // 监控任务
  'group:create': { roles: ['ADMIN', 'OPERATOR'] },
  'group:edit': { roles: ['ADMIN', 'OPERATOR'] },
  'group:delete': { roles: ['ADMIN', 'OPERATOR'] },
  'group:run': { roles: ['ADMIN', 'OPERATOR'] },

  // 变量
  'variable:create': { roles: ['ADMIN', 'OPERATOR'] },
  'variable:edit': { roles: ['ADMIN', 'OPERATOR'] },
  'variable:delete': { roles: ['ADMIN', 'OPERATOR'] },

  // 域名
  'domain:edit': { roles: ['ADMIN', 'OPERATOR'] },
  'domain:delete': { roles: ['ADMIN', 'OPERATOR'] },

  // 告警模板
  'alert-template:create': { roles: ['ADMIN', 'OPERATOR'] },
  'alert-template:edit': { roles: ['ADMIN', 'OPERATOR'] },
  'alert-template:delete': { roles: ['ADMIN', 'OPERATOR'] },
  'alert-template:enable': { roles: ['ADMIN', 'OPERATOR'] },
  'alert-template:disable': { roles: ['ADMIN', 'OPERATOR'] },

  // 告警渠道
  'alert-channel:create': { roles: ['ADMIN', 'OPERATOR'] },
  'alert-channel:edit': { roles: ['ADMIN', 'OPERATOR'] },
  'alert-channel:delete': { roles: ['ADMIN', 'OPERATOR'] },
  'alert-channel:test': { roles: ['ADMIN', 'OPERATOR'] },

  // 告警静默
  'alert-silence:create': { roles: ['ADMIN', 'OPERATOR'] },
  'alert-silence:edit': { roles: ['ADMIN', 'OPERATOR'] },
  'alert-silence:delete': { roles: ['ADMIN', 'OPERATOR'] },
  'alert-silence:enable': { roles: ['ADMIN', 'OPERATOR'] },
  'alert-silence:disable': { roles: ['ADMIN', 'OPERATOR'] },

  // 执行日志
  'log:delete': { roles: ['ADMIN', 'OPERATOR'] },
  'log:cleanup': { roles: ['ADMIN', 'OPERATOR'] },

  // 用户管理
  'user:create': { roles: ['ADMIN'] },
  'user:edit': { roles: ['ADMIN'] },
  'user:delete': { roles: ['ADMIN'] },

  // 安全设置
  'security:settings': { roles: ['ADMIN'] },
  'security:kick': { roles: ['ADMIN'] },
  'security:unlock': { roles: ['ADMIN'] },

  // 数据备份
  'backup:create': { roles: ['ADMIN'] },
  'backup:download': { roles: ['ADMIN'] },
  'backup:restore': { roles: ['ADMIN'] },
  'backup:delete': { roles: ['ADMIN'] },

  // 代理设置
  'proxy:create': { roles: ['ADMIN'] },
  'proxy:edit': { roles: ['ADMIN'] },
  'proxy:delete': { roles: ['ADMIN'] },
  'proxy:activate': { roles: ['ADMIN'] },
  'proxy:test': { roles: ['ADMIN'] },

  // API Schema
  'api-schema:create': { roles: ['ADMIN', 'OPERATOR'] },
  'api-schema:edit': { roles: ['ADMIN', 'OPERATOR'] },
  'api-schema:delete': { roles: ['ADMIN', 'OPERATOR'] },
  'api-schema:validate': { roles: ['ADMIN', 'OPERATOR'] },

  // Inspection
  'inspection:create': { roles: ['ADMIN', 'OPERATOR'] },
  'inspection:edit': { roles: ['ADMIN', 'OPERATOR'] },
  'inspection:delete': { roles: ['ADMIN', 'OPERATOR'] },
  'inspection:run': { roles: ['ADMIN', 'OPERATOR'] },

  // 周期提醒
  'reminder:create': { roles: ['ADMIN', 'OPERATOR'] },
  'reminder:edit': { roles: ['ADMIN', 'OPERATOR'] },
  'reminder:delete': { roles: ['ADMIN'] },
  'reminder:complete': { roles: ['ADMIN', 'OPERATOR'] },
  'reminder:snooze': { roles: ['ADMIN', 'OPERATOR'] }
}

/**
 * 检查角色是否有权限
 * @param {string} role - 用户角色
 * @param {string[]} allowedRoles - 允许的角色列表，空数组表示所有角色允许
 * @returns {boolean}
 */
export function checkRole(role, allowedRoles) {
  if (!allowedRoles || allowedRoles.length === 0) return true
  return allowedRoles.includes(role)
}

/**
 * 检查是否有按钮权限
 * @param {string} role - 用户角色
 * @param {string} buttonKey - 按钮权限 key，如 'monitor:create'
 * @returns {boolean}
 */
export function checkButton(role, buttonKey) {
  const btn = BUTTONS[buttonKey]
  if (!btn) return false // 未定义的权限默认拒绝
  return checkRole(role, btn.roles)
}

/**
 * 获取当前角色有权限的菜单 keys
 * @param {string} role - 用户角色
 * @returns {string[]}
 */
export function getAllowedMenuKeys(role) {
  return MENUS.filter(m => checkRole(role, m.roles)).map(m => m.key)
}
