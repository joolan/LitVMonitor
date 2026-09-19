import { defineStore } from 'pinia'
import { ref, watch } from 'vue'

const STORAGE_KEY = 'litv-theme'

export const THEMES = [
  { key: 'default', label: '默认', icon: 'Monitor', colors: ['#409eff', '#337ecc'] },
  { key: 'dark', label: '深色', icon: 'Moon', colors: ['#1a1a2e', '#16213e'] },
  { key: 'green', label: '翠绿', icon: 'Cherry', colors: ['#00b894', '#00a381'] },
  { key: 'purple', label: '极光紫', icon: 'MagicStick', colors: ['#6c5ce7', '#5a4bd1'] },
  { key: 'red', label: '中国红', icon: 'Sunny', colors: ['#e74c3c', '#c0392b'] },
  { key: 'orange', label: '活力橙', icon: 'Sunny', colors: ['#f39c12', '#e67e22'] }
]

function getStoredTheme() {
  try {
    return localStorage.getItem(STORAGE_KEY) || 'default'
  } catch {
    return 'default'
  }
}

function storeTheme(key) {
  try {
    localStorage.setItem(STORAGE_KEY, key)
  } catch {}
}

export const useThemeStore = defineStore('theme', () => {
  const current = ref(getStoredTheme())

  function applyTheme(key) {
    const root = document.documentElement
    // Remove all theme classes
    root.classList.remove('theme-default', 'theme-dark', 'theme-green', 'theme-purple', 'theme-red', 'theme-orange')
    // Apply new theme class
    root.classList.add(`theme-${key}`)
    current.value = key
    storeTheme(key)
  }

  function init() {
    applyTheme(current.value)
  }

  return { current, THEMES, applyTheme, init }
})
