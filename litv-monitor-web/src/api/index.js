import axios from 'axios'
import { ElMessage, ElMessageBox } from 'element-plus'

const api = axios.create({
  baseURL: '/api',
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json'
  }
})

// 登录失效全局提示（同一时间只弹一次，避免并发请求重复弹窗）
let reloginPrompting = false
const promptRelogin = () => {
  if (reloginPrompting) return
  if (window.location.pathname === '/login') return
  reloginPrompting = true
  ElMessageBox.confirm('登录状态已失效，是否重新登录？', '登录失效', {
    confirmButtonText: '重新登录',
    cancelButtonText: '取消',
    type: 'warning',
    closeOnClickModal: false
  }).then(() => {
    localStorage.removeItem('token')
    localStorage.removeItem('userInfo')
    window.location.href = '/login'
  }).catch(() => {
    // 用户取消：保留当前页面，不再重复弹窗
  })
}

// Request interceptor
api.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

// Response interceptor
api.interceptors.response.use(
  (response) => {
    const res = response.data
    if (res.code && res.code !== 200) {
      return Promise.reject(new Error(res.message || '请求失败'))
    }
    return res
  },
  (error) => {
    const status = error.response?.status
    const url = error.config?.url || ''
    if (status === 401) {
      // 登录接口的401：返回后端实际错误信息（如"用户名或密码错误"）
      if (url.includes('/auth/login')) {
        const msg = error.response?.data?.message || '用户名或密码错误'
        return Promise.reject(new Error(msg))
      }
      // 其他接口的401：会话失效，提示重新登录
      promptRelogin()
      return Promise.reject(new Error('登录已过期，请重新登录'))
    }
    if (status === 428) {
      // 需先修改初始密码
      localStorage.removeItem('token')
      localStorage.removeItem('userInfo')
      window.location.href = '/login'
      return Promise.reject(new Error('请先修改初始密码'))
    }
    if (status === 403) {
      // 已登录但无权限（或 IP 被拒绝）
      const msg = error.response?.data?.message || '没有权限执行该操作'
      ElMessage.error(msg)
      return Promise.reject(new Error(msg))
    }
    const message = error.response?.data?.message
      || error.response?.data?.error
      || error.message
      || '请求失败'
    return Promise.reject(new Error(message))
  }
)

// Auth API (login/logout handled by stores/user.js with raw axios)
// authApi removed - not used

// Monitor API
export const monitorApi = {
  list: (params) => api.get('/monitor/list', { params }),
  get: (id) => api.get(`/monitor/${id}`),
  create: (data) => api.post('/monitor', data),
  update: (id, data) => api.put(`/monitor/${id}`, data),
  delete: (id) => api.delete(`/monitor/${id}`),
  test: (id) => api.post(`/monitor/${id}/test`),
  getGroups: (id) => api.get(`/monitor/${id}/groups`),
  addToGroup: (id, data) => api.post(`/monitor/${id}/groups`, data),
  updateInGroup: (id, groupId, data) => api.put(`/monitor/${id}/groups/${groupId}`, data),
  removeFromGroup: (id, groupId) => api.delete(`/monitor/${id}/groups/${groupId}`),
  batchUpdateStatus: (ids, enabled) => api.put('/monitor/batch/status', { ids, enabled }),
  copy: (id) => api.post(`/monitor/${id}/copy`),
  export: () => api.get('/monitor/export', { responseType: 'blob' }),
  import: (formData) => api.post('/monitor/import', formData, { headers: { 'Content-Type': 'multipart/form-data' } })
}

// Group API
export const groupApi = {
  list: (params) => api.get('/group/list', { params }),
  get: (id) => api.get(`/group/${id}`),
  create: (data) => api.post('/group', data),
  update: (id, data) => api.put(`/group/${id}`, data),
  delete: (id) => api.delete(`/group/${id}`),
  getMonitors: (id) => api.get(`/group/${id}/monitors`),
  run: (id) => api.post(`/group/${id}/run`)
}

// Variable API
export const variableApi = {
  listGlobal: (params) => api.get('/variable/global/list', { params }),
  listAllGlobal: () => api.get('/variable/global/all'),
  createGlobal: (data) => api.post('/variable/global', data),
  updateGlobal: (id, data) => api.put(`/variable/global/${id}`, data),
  deleteGlobal: (id) => api.delete(`/variable/global/${id}`),
  listAllGroup: () => api.get('/variable/group/all'),
  createGroup: (data) => api.post('/variable/group', data),
  updateGroup: (id, data) => api.put(`/variable/group/${id}`, data),
  deleteGroup: (id) => api.delete(`/variable/group/${id}`)
}

// Domain API
export const domainApi = {
  list: (params) => api.get('/domain/list', { params }),
  get: (id) => api.get(`/domain/${id}`),
  update: (id, data) => api.put(`/domain/${id}`, data),
  delete: (id) => api.delete(`/domain/${id}`),
  getSsl: (id) => api.get(`/domain/${id}/ssl`),
  getExpiringSsl: () => api.get('/domain/ssl/expiring'),
  getIpHistory: (id) => api.get(`/domain/${id}/ip-history`),
  toggleStar: (id) => api.put(`/domain/${id}/star`)
}

// Alert API
export const alertApi = {
  listConfigs: () => api.get('/alert/config/list'),
  listLogs: (params) => api.get('/alert/log/list', { params }),
  getLog: (id) => api.get(`/alert/log/${id}`)
}

// Alert Template API
export const alertTemplateApi = {
  list: () => api.get('/alert/template/list'),
  get: (id) => api.get(`/alert/template/${id}`),
  create: (data) => api.post('/alert/template', data),
  update: (id, data) => api.put(`/alert/template/${id}`, data),
  enable: (id) => api.put(`/alert/template/${id}/enable`),
  disable: (id) => api.put(`/alert/template/${id}/disable`),
  delete: (id) => api.delete(`/alert/template/${id}`)
}

// Alert Channel API
export const alertChannelApi = {
  list: () => api.get('/alert/channel/list'),
  get: (id) => api.get(`/alert/channel/${id}`),
  create: (data) => api.post('/alert/channel', data),
  update: (id, data) => api.put(`/alert/channel/${id}`, data),
  delete: (id) => api.delete(`/alert/channel/${id}`),
  test: (id) => api.post(`/alert/channel/${id}/test`)
}

// Log API
export const logApi = {
  list: (params) => api.get('/log/list', { params }),
  cleanup: (days) => api.delete('/log/cleanup', { params: { days } })
}

// Dashboard API
export const dashboardApi = {
  overview: () => api.get('/dashboard/overview'),
  urlTrend: (granularity) => api.get('/dashboard/url-trend', { params: { granularity } }),
  uptime: (params) => api.get('/dashboard/uptime', { params }),
  schemaChanges: (params) => api.get('/dashboard/schema-changes', { params })
}

// User API
export const userApi = {
  list: (params) => api.get('/user/list', { params }),
  create: (data) => api.post('/user', data),
  update: (id, data) => api.put(`/user/${id}`, data),
  delete: (id) => api.delete(`/user/${id}`),
  updateProfile: (data) => api.put('/user/profile', data)
}

// Security Settings API
export const securityApi = {
  getSettings: () => api.get('/security/settings'),
  updateSettings: (data) => api.put('/security/settings', data),
  getUserSessions: (userId) => api.get(`/security/sessions/user/${userId}`),
  getOnlineCounts: () => api.get('/security/sessions/online-count'),
  kickSession: (jti) => api.post('/security/sessions/kick', { jti }),
  unlockUser: (userId) => api.post('/security/unlock', { userId }),
  getLockedUsers: () => api.get('/security/lockout/users')
}

// Alert Silence API
export const alertSilenceApi = {
  list: (params) => api.get('/alert/silence/list', { params }),
  get: (id) => api.get(`/alert/silence/${id}`),
  create: (data) => api.post('/alert/silence', data),
  update: (id, data) => api.put(`/alert/silence/${id}`, data),
  delete: (id) => api.delete(`/alert/silence/${id}`),
  enable: (id) => api.put(`/alert/silence/${id}/enable`),
  disable: (id) => api.put(`/alert/silence/${id}/disable`)
}

// Audit Log API
export const auditApi = {
  list: (params) => api.get('/audit/log', { params })
}

// Backup API
export const backupApi = {
  create: () => api.post('/backup/create'),
  list: () => api.get('/backup/list'),
  restore: (data) => api.post('/backup/restore', data),
  delete: (path) => api.delete('/backup/delete', { params: { path } }),
  cleanup: (data) => api.post('/backup/cleanup', data)
}

// Proxy API
export const proxyApi = {
  list: () => api.get('/proxy/list'),
  get: (id) => api.get(`/proxy/${id}`),
  create: (data) => api.post('/proxy', data),
  update: (id, data) => api.put(`/proxy/${id}`, data),
  delete: (id) => api.delete(`/proxy/${id}`),
  activate: (id) => api.put(`/proxy/${id}/activate`),
  deactivate: () => api.put('/proxy/deactivate'),
  test: (id) => api.post(`/proxy/${id}/test`)
}

// API Schema API
export const schemaApi = {
  listAll: () => api.get('/api-schema/list'),
  get: (id) => api.get(`/api-schema/${id}`),
  create: (data) => api.post('/api-schema', data),
  update: (id, data) => api.put(`/api-schema/${id}`, data),
  delete: (id) => api.delete(`/api-schema/${id}`),
  getHistory: (id) => api.get(`/api-schema/${id}/history`),
  compareVersions: (historyId) => api.get(`/api-schema/history/${historyId}/compare`),
  getAllHistory: (params) => api.get('/api-schema/history/all', { params })
}

// Inspection API
export const inspectionApi = {
  listConfigs: () => api.get('/inspection/config'),
  getConfig: (id) => api.get(`/inspection/config/${id}`),
  createConfig: (data) => api.post('/inspection/config', data),
  updateConfig: (id, data) => api.put(`/inspection/config/${id}`, data),
  deleteConfig: (id) => api.delete(`/inspection/config/${id}`),
  listHistory: (configId) => api.get(`/inspection/history/${configId}`),
  getHistoryById: (id) => api.get(`/inspection/history/detail/${id}`),
  getHistoryDetails: (historyId) => api.get(`/inspection/history/${historyId}/details`),
  runInspection: (configId) => api.post(`/inspection/run/${configId}`)
}

// Status API (public, no auth required)
export const statusApi = {
  getStatus: () => api.get('/status')
}

// Memo API (per-user)
export const memoApi = {
  get: () => api.get('/memo'),
  save: (content) => api.post('/memo', { content })
}

// Version API
export const versionApi = {
  get: () => api.get('/version')
}

// Reminder API
export const reminderApi = {
  list: (params) => api.get('/reminder/list', { params }),
  get: (id) => api.get(`/reminder/${id}`),
  create: (data) => api.post('/reminder', data),
  update: (id, data) => api.put(`/reminder/${id}`, data),
  delete: (id) => api.delete(`/reminder/${id}`),
  complete: (id) => api.put(`/reminder/${id}/complete`),
  snooze: (id, minutes) => api.put(`/reminder/${id}/snooze`, { minutes }),
  enable: (id) => api.put(`/reminder/${id}/enable`),
  disable: (id) => api.put(`/reminder/${id}/disable`),
  dashboard: () => api.get('/reminder/dashboard'),
  preview: (data) => api.post('/reminder/preview', data)
}

export default api
