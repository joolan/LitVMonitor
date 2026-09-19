<template>
  <div class="login-container">
    <div class="login-card">
      <div class="login-header">
        <img src="/vite.svg" alt="Logo" />
        <h1>LitVMonitor</h1>
        <p>轻量级HTTP/API监控系统</p>
      </div>
      <el-form ref="loginFormRef" :model="loginForm" :rules="rules" class="login-form">
        <el-form-item prop="username">
          <el-input
            v-model="loginForm.username"
            placeholder="请输入用户名"
            :prefix-icon="User"
          />
        </el-form-item>
        <el-form-item prop="password">
          <el-input
            v-model="loginForm.password"
            type="password"
            placeholder="请输入密码"
            :prefix-icon="Lock"
            show-password
            @keyup.enter="handleLogin"
          />
        </el-form-item>
        <el-form-item>
          <el-button
            type="primary"
            :loading="loading"
            class="login-btn"
            @click="handleLogin"
          >
            登录
          </el-button>
        </el-form-item>
      </el-form>
      <div class="login-footer">
        <router-link to="/status">查看公开状态页</router-link>
      </div>
    </div>

    <el-dialog
      v-model="forceChangeVisible"
      title="首次登录需修改密码"
      width="440px"
      :close-on-click-modal="false"
      :close-on-press-escape="false"
      :show-close="false"
    >
      <el-alert type="warning" :closable="false" show-icon
        title="为保证账号安全，请先修改初始密码后再使用系统" style="margin-bottom: 16px" />
      <el-form ref="pwdFormRef" :model="pwdForm" :rules="pwdRules" label-width="90px">
        <el-form-item label="原密码" prop="oldPassword">
          <el-input v-model="pwdForm.oldPassword" type="password" show-password />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input v-model="pwdForm.newPassword" type="password" show-password />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input v-model="pwdForm.confirmPassword" type="password" show-password
            @keyup.enter="submitForceChange" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="cancelForceChange">取消</el-button>
        <el-button type="primary" :loading="pwdLoading" @click="submitForceChange">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { User, Lock } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { userApi } from '@/api'

const router = useRouter()
const userStore = useUserStore()

const loginFormRef = ref(null)
const loading = ref(false)

const loginForm = reactive({
  username: '',
  password: ''
})

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

// Forced password change (bootstrap / reset accounts)
const forceChangeVisible = ref(false)
const pwdLoading = ref(false)
const pwdFormRef = ref(null)
const pwdForm = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })
const pwdRules = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 8, message: '密码长度不能少于8位', trigger: 'blur' },
    {
      validator: (rule, value, cb) => {
        if (value && (!/[A-Z]/.test(value) || !/[a-z]/.test(value) || !/\d/.test(value))) {
          cb(new Error('需同时包含大写字母、小写字母和数字'))
        } else cb()
      },
      trigger: 'blur'
    }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (rule, value, cb) => {
        if (value !== pwdForm.newPassword) cb(new Error('两次输入的密码不一致'))
        else cb()
      },
      trigger: 'blur'
    }
  ]
}

const handleLogin = async () => {
  const valid = await loginFormRef.value.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    const data = await userStore.login(loginForm.username, loginForm.password)
    if (data?.mustChangePassword) {
      pwdForm.oldPassword = loginForm.password
      forceChangeVisible.value = true
      return
    }
    ElMessage.success('登录成功')
    router.push('/dashboard')
  } catch (error) {
    ElMessage.error(error.message || '登录失败')
  } finally {
    loading.value = false
  }
}

const submitForceChange = async () => {
  const valid = await pwdFormRef.value.validate().catch(() => false)
  if (!valid) return
  pwdLoading.value = true
  try {
    await userApi.updateProfile({
      oldPassword: pwdForm.oldPassword,
      newPassword: pwdForm.newPassword,
      newPasswordConfirm: pwdForm.confirmPassword
    })
    ElMessage.success('密码修改成功，请使用新密码重新登录')
    forceChangeVisible.value = false
    pwdForm.oldPassword = ''
    pwdForm.newPassword = ''
    pwdForm.confirmPassword = ''
    await userStore.logout()
  } catch (error) {
    ElMessage.error(error.message || '修改失败')
  } finally {
    pwdLoading.value = false
  }
}

const cancelForceChange = async () => {
  forceChangeVisible.value = false
  await userStore.logout()
}
</script>

<style lang="scss" scoped>
.login-container {
  width: 100%;
  height: 100vh;
  display: flex;
  justify-content: center;
  align-items: center;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color-scheme: light;

  :deep(.el-input__wrapper) {
    background-color: #fff !important;
    box-shadow: none !important;
    border: 1px solid #dcdfe6;
  }
  :deep(.el-input__wrapper:hover) {
    border-color: #c0c4cc;
  }
  :deep(.el-input__wrapper.is-focus) {
    border-color: #409eff;
  }
  :deep(.el-input__inner) {
    color: #303133 !important;
  }
  :deep(.el-input__inner::placeholder) {
    color: #a8abb2 !important;
  }
}

.login-card {
  width: 400px;
  padding: 40px;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.1);
}

.login-header {
  text-align: center;
  margin-bottom: 30px;

  img {
    width: 64px;
    height: 64px;
    margin-bottom: 16px;
  }

  h1 {
    font-size: 24px;
    color: #303133;
    margin-bottom: 8px;
  }

  p {
    color: #909399;
    font-size: 14px;
  }
}

.login-form {
  .login-btn {
    width: 100%;
  }
}

.login-footer {
  text-align: center;
  margin-top: 16px;

  a {
    color: #409eff;
    font-size: 13px;
    text-decoration: none;

    &:hover {
      text-decoration: underline;
    }
  }
}
</style>
