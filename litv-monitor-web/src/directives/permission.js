import { watch } from 'vue'
import { useUserStore } from '@/stores/user'
import { checkButton } from '@/permissions'

/**
 * v-permission 指令
 *
 * 用法：
 *   <el-button v-permission="'monitor:create'">添加</el-button>
 *   <el-button v-permission="row.enabled ? 'x:disable' : 'x:enable'">切换</el-button>
 *
 * 如果当前用户没有权限，元素会被隐藏（display:none）。
 * 注意：这是纯 UI 控制，真正的权限校验必须在后端完成。
 */
const applyPermission = (el, value) => {
  if (!value) return
  const userStore = useUserStore()
  const role = userStore.role || ''
  if (!checkButton(role, value)) {
    el.style.display = 'none'
    el.__vPermissionHidden = true
  } else if (el.__vPermissionHidden) {
    el.style.display = ''
    el.__vPermissionHidden = false
  }
}

export const permissionDirective = {
  mounted(el, binding) {
    applyPermission(el, binding.value)
    // 监听角色变化（userInfo 从 API 加载后会更新），组件卸载时停止，避免 watcher 泄漏
    const userStore = useUserStore()
    el.__vPermissionStop = watch(() => userStore.role, () => applyPermission(el, binding.value))
  },
  updated(el, binding) {
    // 绑定值变化时重新计算（如 row.enabled ? 'a' : 'b'）
    if (binding.value !== binding.oldValue) {
      applyPermission(el, binding.value)
    }
  },
  unmounted(el) {
    if (el.__vPermissionStop) {
      el.__vPermissionStop()
      el.__vPermissionStop = null
    }
  }
}
