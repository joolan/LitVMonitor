import { computed } from 'vue'
import { useUserStore } from '@/stores/user'
import { checkButton, getAllowedMenuKeys, MENUS, MENU_GROUPS } from '@/permissions'

/**
 * 权限 composable
 * 
 * 使用方式：
 *   const { role, hasButton, allowedMenus, allowedMenuGroups, hasMenu } = usePermission()
 *   
 *   // 检查按钮权限
 *   v-if="hasButton('monitor:create')"
 *   
 *   // 检查菜单权限
 *   v-if="hasMenu('user')"
 */
export function usePermission() {
  const userStore = useUserStore()
  const role = computed(() => userStore.role || '')

  /**
   * 检查当前用户是否有指定按钮权限
   * @param {string} buttonKey - 如 'monitor:create'
   * @returns {boolean}
   */
  const hasButton = (buttonKey) => {
    return checkButton(role.value, buttonKey)
  }

  /**
   * 当前用户有权限的菜单 key 列表
   */
  const allowedMenuKeys = computed(() => {
    return getAllowedMenuKeys(role.value)
  })

  /**
   * 当前用户有权限的菜单列表（过滤后的）
   */
  const allowedMenus = computed(() => {
    return MENUS.filter(m => {
      // 检查角色权限
      if (m.roles && m.roles.length > 0 && !m.roles.includes(role.value)) {
        return false
      }
      return true
    })
  })

  /**
   * 当前用户有权限的分组列表（仅包含有可见子菜单的分组）
   */
  const allowedMenuGroups = computed(() => {
    const allowedKeys = allowedMenuKeys.value
    return MENU_GROUPS.filter(group => {
      return MENUS.some(m => m.group === group.key && allowedKeys.includes(m.key))
    })
  })

  /**
   * 检查当前用户是否有指定菜单的访问权限
   * @param {string} menuKey - 如 'user'
   * @returns {boolean}
   */
  const hasMenu = (menuKey) => {
    return allowedMenuKeys.value.includes(menuKey)
  }

  /**
   * 获取分组下的有权限的子菜单
   * @param {string} groupKey - 如 'system-group'
   * @returns {Array}
   */
  const getGroupMenus = (groupKey) => {
    return MENUS.filter(m => m.group === groupKey && allowedMenuKeys.value.includes(m.key))
  }

  return {
    role,
    hasButton,
    allowedMenuKeys,
    allowedMenus,
    allowedMenuGroups,
    hasMenu,
    getGroupMenus
  }
}
