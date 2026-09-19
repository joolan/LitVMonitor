<template>
  <el-container class="layout-container">
    <el-aside :width="isCollapse ? '64px' : '220px'" class="sidebar">
      <div class="logo">
        <img src="/vite.svg" alt="Logo" />
        <div v-show="!isCollapse" class="logo-text">
          <span>LitVMonitor</span>
          <span class="version" v-if="versionText">{{ versionText }}</span>
        </div>
      </div>
      <el-menu
        :default-active="activeMenu"
        :collapse="isCollapse"
        router
        :background-color="menuBgColor"
        :text-color="menuTextColor"
        active-text-color="#409eff"
      >
        <!-- 一级菜单 -->
        <template v-for="menu in topLevelMenus" :key="menu.key">
          <el-menu-item :index="menu.path">
            <el-icon><component :is="menu.icon" /></el-icon>
            <template #title>{{ menu.title }}</template>
          </el-menu-item>
        </template>

        <!-- 分组子菜单 -->
        <template v-for="group in allowedMenuGroups" :key="group.key">
          <el-sub-menu :index="group.key">
            <template #title>
              <el-icon><component :is="group.icon" /></el-icon>
              <span>{{ group.title }}</span>
            </template>
            <template v-for="menu in getGroupMenus(group.key)" :key="menu.key">
              <el-menu-item :index="menu.path">
                <template #title>{{ menu.title }}</template>
              </el-menu-item>
            </template>
          </el-sub-menu>
        </template>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="header">
        <div class="header-left">
          <el-icon class="collapse-btn" @click="isCollapse = !isCollapse">
            <Fold v-if="!isCollapse" />
            <Expand v-else />
          </el-icon>
          <el-breadcrumb separator="/">
            <el-breadcrumb-item :to="{ path: '/' }">首页</el-breadcrumb-item>
            <el-breadcrumb-item>{{ currentRoute.meta.title }}</el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        <div class="header-right">
          <el-popover placement="bottom-end" :width="280" trigger="click">
            <template #reference>
              <div class="theme-btn" title="切换主题">
                <el-icon :size="18"><Brush /></el-icon>
              </div>
            </template>
            <div class="theme-panel">
              <div class="theme-panel__title">主题皮肤</div>
              <div class="theme-grid">
                <div
                  v-for="t in THEMES"
                  :key="t.key"
                  class="theme-item"
                  :class="{ active: themeStore.current === t.key }"
                  @click="themeStore.applyTheme(t.key)"
                >
                  <div class="theme-colors">
                    <span class="color-dot" :style="{ background: t.colors[0] }"></span>
                    <span class="color-dot" :style="{ background: t.colors[1] }"></span>
                  </div>
                  <span class="theme-label">{{ t.label }}</span>
                </div>
              </div>
            </div>
          </el-popover>

          <el-dropdown @command="handleCommand">
            <span class="user-info">
              <el-icon><User /></el-icon>
              {{ userStore.userInfo?.nickname || userStore.userInfo?.username || '用户' }}
              <el-icon class="el-icon--right"><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">修改资料</el-dropdown-item>
                <el-dropdown-item divided command="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <el-main class="main-content">
        <router-view />
      </el-main>
    </el-container>
  </el-container>

  <!-- Profile Dialog -->
  <el-dialog v-model="profileDialogVisible" title="修改资料" width="480px">
    <el-form ref="profileFormRef" :model="profileForm" :rules="profileRules" label-width="100px">
      <el-form-item label="用户名">
        <el-input :model-value="userStore.userInfo?.username" disabled />
      </el-form-item>
      <el-form-item label="昵称">
        <el-input v-model="profileForm.nickname" placeholder="请输入昵称" />
      </el-form-item>
      <el-form-item label="邮箱">
        <el-input v-model="profileForm.email" placeholder="请输入邮箱" />
      </el-form-item>
      <el-divider content-position="left">修改密码（不修改请留空）</el-divider>
      <el-form-item label="旧密码" prop="oldPassword">
        <el-input v-model="profileForm.oldPassword" type="password" placeholder="请输入旧密码" show-password />
      </el-form-item>
      <el-form-item label="新密码" prop="newPassword">
        <el-input v-model="profileForm.newPassword" type="password" placeholder="请输入新密码（至少6位）" show-password />
      </el-form-item>
      <el-form-item label="确认新密码" prop="newPasswordConfirm">
        <el-input v-model="profileForm.newPasswordConfirm" type="password" placeholder="请再次输入新密码" show-password />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="profileDialogVisible = false">取消</el-button>
      <el-button type="primary" @click="submitProfile" :loading="profileSubmitting">确定</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useThemeStore, THEMES } from '@/stores/theme'
import { usePermission } from '@/composables/usePermission'
import { ElMessage, ElMessageBox } from 'element-plus'
import { userApi, versionApi } from '@/api'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const themeStore = useThemeStore()
const { allowedMenus, allowedMenuGroups, getGroupMenus } = usePermission()

const isCollapse = ref(false)
const versionText = ref('')

const activeMenu = computed(() => route.path)
const currentRoute = computed(() => route)

const menuBgColor = computed(() => {
  return themeStore.current === 'dark' ? '#1e293b' :
         themeStore.current === 'green' ? '#00695c' :
         themeStore.current === 'purple' ? '#4c1d95' :
         themeStore.current === 'red' ? '#7f1d1d' :
         themeStore.current === 'orange' ? '#78350f' : '#304156'
})

const menuTextColor = computed(() => {
  return themeStore.current === 'dark' ? '#94a3b8' : '#bfcbd9'
})

// 一级菜单（没有 group 的菜单）
const topLevelMenus = computed(() => {
  return allowedMenus.value.filter(m => !m.group)
})

// Profile dialog
const profileDialogVisible = ref(false)
const profileSubmitting = ref(false)
const profileFormRef = ref(null)
const profileForm = reactive({
  nickname: '',
  email: '',
  oldPassword: '',
  newPassword: '',
  newPasswordConfirm: ''
})

const validateNewPasswordConfirm = (rule, value, callback) => {
  if (profileForm.newPassword && value !== profileForm.newPassword) {
    callback(new Error('两次输入的密码不一致'))
  } else {
    callback()
  }
}

const validateOldPassword = (rule, value, callback) => {
  if (profileForm.newPassword && !value) {
    callback(new Error('修改密码时请输入旧密码'))
  } else {
    callback()
  }
}

const profileRules = {
  oldPassword: [{ validator: validateOldPassword, trigger: 'blur' }],
  newPassword: [{ min: 6, message: '密码长度不能少于6位', trigger: 'blur' }],
  newPasswordConfirm: [{ validator: validateNewPasswordConfirm, trigger: 'blur' }]
}

onMounted(async () => {
  themeStore.init()
  if (userStore.isLoggedIn && !userStore.userInfo) {
    await userStore.fetchUserInfo()
  }
  try {
    const res = await versionApi.get()
    if (res.data) {
      versionText.value = res.data.backend + ' / ' + res.data.frontend
    }
  } catch {}
})

const handleCommand = async (command) => {
  if (command === 'logout') {
    try {
      await ElMessageBox.confirm('确认退出登录？', '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      })
    } catch {
      return // 用户取消
    }
    // 无论后端是否返回成功，都必须清理本地登录态并跳转登录页
    try {
      await userStore.logout()
    } catch {
      userStore.clearToken()
    }
    router.push('/login')
  } else if (command === 'profile') {
    // Load current user info
    profileForm.nickname = userStore.userInfo?.nickname || ''
    profileForm.email = userStore.userInfo?.email || ''
    profileForm.oldPassword = ''
    profileForm.newPassword = ''
    profileForm.newPasswordConfirm = ''
    profileDialogVisible.value = true
  }
}

const submitProfile = async () => {
  const valid = await profileFormRef.value.validate().catch(() => false)
  if (!valid) return

  // If password fields are filled, validate them
  if (profileForm.newPassword || profileForm.oldPassword) {
    if (!profileForm.oldPassword) {
      ElMessage.error('请输入旧密码')
      return
    }
    if (!profileForm.newPassword) {
      ElMessage.error('请输入新密码')
      return
    }
    if (profileForm.newPassword.length < 6) {
      ElMessage.error('新密码长度不能少于6位')
      return
    }
    if (profileForm.newPassword !== profileForm.newPasswordConfirm) {
      ElMessage.error('两次输入的密码不一致')
      return
    }
  }

  profileSubmitting.value = true
  try {
    await userApi.updateProfile({
      oldPassword: profileForm.oldPassword || null,
      newPassword: profileForm.newPassword || null,
      nickname: profileForm.nickname,
      email: profileForm.email
    })
    ElMessage.success('修改成功')
    profileDialogVisible.value = false
    // Refresh user info
    await userStore.fetchUserInfo()
  } catch (error) {
    ElMessage.error(error.message || '修改失败')
  } finally {
    profileSubmitting.value = false
  }
}
</script>

<style lang="scss" scoped>
.layout-container {
  height: 100vh;
}

.sidebar {
  background-color: var(--sidebar-bg);
  transition: width 0.3s, background-color 0.3s;
  overflow: hidden;
  display: flex;
  flex-direction: column;

  .logo {
    height: 60px;
    flex-shrink: 0;
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 10px;
    color: #fff;
    font-size: 18px;
    font-weight: 600;

    img {
      width: 32px;
      height: 32px;
    }

    .logo-text {
      display: flex;
      flex-direction: column;
      line-height: 1.2;

      .version {
        font-size: 10px;
        font-weight: 400;
        opacity: 0.6;
        letter-spacing: 0;
      }
    }
  }

  .el-menu {
    flex: 1;
    overflow-y: auto;
    overflow-x: hidden;
  }
}

.header {
  background: var(--header-bg);
  display: flex;
  align-items: center;
  justify-content: space-between;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.08);
  padding: 0 20px;
  transition: background-color 0.3s;

  .header-left {
    display: flex;
    align-items: center;
    gap: 20px;

    .collapse-btn {
      font-size: 20px;
      cursor: pointer;
      color: var(--text-secondary);
      transition: color 0.3s;

      &:hover {
        color: var(--primary-color);
      }
    }
  }

  .header-right {
    display: flex;
    align-items: center;
    gap: 12px;

    .user-info {
      display: flex;
      align-items: center;
      gap: 8px;
      cursor: pointer;
      color: var(--text-secondary);

      &:hover {
        color: var(--primary-color);
      }
    }
  }
}

.theme-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 8px;
  cursor: pointer;
  color: var(--text-secondary);
  transition: all 0.2s;

  &:hover {
    background: var(--bg-color);
    color: var(--primary-color);
  }
}

.theme-panel {
  &__title {
    font-size: 14px;
    font-weight: 600;
    color: var(--text-color);
    margin-bottom: 12px;
  }
}

.theme-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
}

.theme-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 10px 6px;
  border-radius: 8px;
  cursor: pointer;
  border: 2px solid transparent;
  transition: all 0.2s;

  &:hover {
    background: var(--bg-color);
  }

  &.active {
    border-color: var(--primary-color);
    background: var(--bg-color);
  }
}

.theme-colors {
  display: flex;
  gap: 4px;
}

.color-dot {
  width: 16px;
  height: 16px;
  border-radius: 50%;
  border: 1px solid rgba(0, 0, 0, 0.08);
}

.theme-label {
  font-size: 11px;
  color: var(--text-secondary);
}

.main-content {
  background-color: var(--bg-color);
  padding: 20px;
  overflow-y: auto;
}
</style>
