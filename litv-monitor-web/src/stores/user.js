import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import axios from 'axios'

export const useUserStore = defineStore('user', () => {
  const token = ref(localStorage.getItem('token') || '')
  const storedUserInfo = localStorage.getItem('userInfo')
  const userInfo = ref(storedUserInfo ? JSON.parse(storedUserInfo) : null)
  const mustChangePassword = ref(false)

  const isLoggedIn = computed(() => !!token.value)
  const role = computed(() => userInfo.value?.role || '')

  const setToken = (newToken) => {
    token.value = newToken
    localStorage.setItem('token', newToken)
    axios.defaults.headers.common['Authorization'] = `Bearer ${newToken}`
  }

  const clearToken = () => {
    token.value = ''
    userInfo.value = null
    mustChangePassword.value = false
    localStorage.removeItem('token')
    localStorage.removeItem('userInfo')
    delete axios.defaults.headers.common['Authorization']
  }

  const fetchUserInfo = async () => {
    try {
      const response = await axios.get('/api/auth/me')
      userInfo.value = response.data.data
      localStorage.setItem('userInfo', JSON.stringify(response.data.data))
    } catch (error) {
      console.error('Failed to fetch user info:', error)
      clearToken()
    }
  }

  const login = async (username, password) => {
    const response = await axios.post('/api/auth/login', { username, password })
    const res = response.data
    if (res.code && res.code !== 200) {
      throw new Error(res.message || '登录失败')
    }
    const data = res.data
    setToken(data.token)
    userInfo.value = {
      username: data.username,
      nickname: data.nickname,
      email: data.email,
      role: data.role
    }
    mustChangePassword.value = !!data.mustChangePassword
    localStorage.setItem('userInfo', JSON.stringify(userInfo.value))
    return data
  }

  const getJtiFromToken = () => {
    try {
      const t = token.value
      if (!t) return null
      let payload = t.split('.')[1]
      if (!payload) return null
      // JWT 使用 base64url，需先还原为 base64 再补 padding
      payload = payload.replace(/-/g, '+').replace(/_/g, '/')
      while (payload.length % 4) payload += '='
      const json = JSON.parse(atob(payload))
      return json.jti || null
    } catch {
      return null
    }
  }

  const logout = async () => {
    const jti = getJtiFromToken()
    try {
      await axios.post('/api/auth/logout', jti ? { jti } : {})
    } catch {
      // 后端不可达或 token 失效时也必须完成本地登出
    } finally {
      clearToken()
    }
  }

  // Initialize axios header if token exists
  if (token.value) {
    axios.defaults.headers.common['Authorization'] = `Bearer ${token.value}`
  }

  return {
    token,
    userInfo,
    mustChangePassword,
    isLoggedIn,
    role,
    setToken,
    clearToken,
    fetchUserInfo,
    login,
    logout
  }
})
