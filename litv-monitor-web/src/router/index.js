import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { MENUS } from '@/permissions'

// Build route-meta permission mapping from MENUS config
const menuPermissionMap = {}
MENUS.forEach(m => {
  menuPermissionMap[m.path] = m.roles
})

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue'),
    meta: { requiresAuth: false }
  },
  {
    path: '/status',
    name: 'StatusPage',
    component: () => import('@/views/StatusPage.vue'),
    meta: { requiresAuth: false }
  },
  {
    path: '/',
    component: () => import('@/layout/MainLayout.vue'),
    meta: { requiresAuth: true },
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/Dashboard.vue'),
        meta: { title: '仪表盘', icon: 'Monitor' }
      },
      {
        path: 'monitor',
        name: 'Monitor',
        component: () => import('@/views/MonitorList.vue'),
        meta: { title: '监控项管理', icon: 'Connection' }
      },
      {
        path: 'group',
        name: 'Group',
        component: () => import('@/views/GroupList.vue'),
        meta: { title: '监控任务', icon: 'Folder' }
      },
      {
        path: 'variable',
        name: 'Variable',
        component: () => import('@/views/VariableList.vue'),
        meta: { title: '变量管理', icon: 'Setting' }
      },
      {
        path: 'proxy',
        name: 'Proxy',
        component: () => import('@/views/ProxyList.vue'),
        meta: { title: '代理设置', icon: 'Connection' }
      },
      {
        path: 'api-schema',
        name: 'ApiSchema',
        component: () => import('@/views/ApiSchemaList.vue'),
        meta: { title: 'API Schema', icon: 'Document' }
      },
      {
        path: 'domain',
        name: 'Domain',
        component: () => import('@/views/DomainList.vue'),
        meta: { title: '域名证书', icon: 'Link' }
      },
      {
        path: 'alert-template',
        name: 'AlertTemplate',
        component: () => import('@/views/AlertTemplateList.vue'),
        meta: { title: '告警模板', icon: 'Bell' }
      },
      {
        path: 'alert-channel',
        name: 'AlertChannel',
        component: () => import('@/views/AlertChannelList.vue'),
        meta: { title: '告警渠道', icon: 'Bell' }
      },
      {
        path: 'alert-log',
        name: 'AlertLog',
        component: () => import('@/views/AlertLogList.vue'),
        meta: { title: '告警记录', icon: 'Bell' }
      },
      {
        path: 'inspection',
        name: 'Inspection',
        component: () => import('@/views/InspectionList.vue'),
        meta: { title: '巡检模式', icon: 'Monitor' }
      },
      {
        path: 'reminder',
        name: 'Reminder',
        component: () => import('@/views/ReminderList.vue'),
        meta: { title: '周期提醒', icon: 'AlarmClock' }
      },
      {
        path: 'log',
        name: 'Log',
        component: () => import('@/views/LogList.vue'),
        meta: { title: '监控执行日志', icon: 'Document' }
      },
      {
        path: 'alert-silence',
        name: 'AlertSilence',
        component: () => import('@/views/AlertSilenceList.vue'),
        meta: { title: '告警静默', icon: 'Bell' }
      },
      {
        path: 'audit-log',
        name: 'AuditLog',
        component: () => import('@/views/AuditLogList.vue'),
        meta: { title: '审计日志', icon: 'Document' }
      },
      {
        path: 'backup',
        name: 'Backup',
        component: () => import('@/views/BackupList.vue'),
        meta: { title: '数据备份', icon: 'Download' }
      },
      {
        path: 'user',
        name: 'User',
        component: () => import('@/views/UserList.vue'),
        meta: { title: '用户管理', icon: 'User' }
      },
      {
        path: 'security-settings',
        name: 'SecuritySettings',
        component: () => import('@/views/SecuritySettings.vue'),
        meta: { title: '安全设置', icon: 'Lock' }
      },
      {
        path: 'manual',
        name: 'Manual',
        component: () => import('@/views/Manual.vue'),
        meta: { title: '使用手册', icon: 'Document' }
      },
      {
        path: ':pathMatch(.*)*',
        name: 'NotFound',
        component: () => import('@/views/NotFound.vue'),
        meta: { title: '页面不存在' }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('token')
  
  if (to.meta.requiresAuth !== false && !token) {
    next('/login')
  } else if (to.path === '/login' && token) {
    next('/dashboard')
  } else {
    // Check role-based permission from store instead of localStorage
    const allowedRoles = menuPermissionMap[to.path]
    if (allowedRoles && allowedRoles.length > 0) {
      const userStore = useUserStore()
      const userRole = userStore.userInfo?.role
      if (!userRole || !allowedRoles.includes(userRole)) {
        next('/dashboard')
        return
      }
    }
    next()
  }
})

export default router
