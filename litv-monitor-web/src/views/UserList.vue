<template>
  <div class="user-list">
    <div class="page-header">
      <h2>用户管理</h2>
      <el-button type="primary" @click="showDialog()" v-permission="'user:create'">
        <el-icon><Plus /></el-icon>
        添加用户
      </el-button>
    </div>

    <el-card>
      <el-table :data="users" stripe v-loading="loading">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="username" label="用户名" min-width="120" />
        <el-table-column prop="nickname" label="昵称" min-width="120" />
        <el-table-column prop="email" label="邮箱" min-width="180" />
        <el-table-column prop="role" label="角色" width="100">
          <template #default="{ row }">
            <el-tag :type="getRoleType(row.role)" size="small">
              {{ getRoleLabel(row.role) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="enabled" label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'info'" size="small">
              {{ row.enabled ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="在线" width="70" align="center">
          <template #default="{ row }">
            <el-tag v-if="onlineCounts[row.id] > 0" type="success" size="small" style="cursor:pointer" @click="showSessions(row)">
              {{ onlineCounts[row.id] }}
            </el-tag>
            <span v-else class="text-muted">0</span>
          </template>
        </el-table-column>
        <el-table-column label="锁定" width="60" align="center">
          <template #default="{ row }">
            <el-tooltip v-if="lockedUsers.includes(row.username)" content="账号被锁定，请联系管理员解锁！" placement="top">
              <el-icon style="color: #f56c6c; cursor: pointer"><Lock /></el-icon>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="showDialog(row)" v-permission="'user:edit'">编辑</el-button>
            <el-popconfirm title="确认删除?" @confirm="deleteUser(row.id)">
              <template #reference>
                <el-button type="danger" link :disabled="row.username === 'admin'" v-permission="'user:delete'">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="pagination.page"
        v-model:page-size="pagination.size"
        :total="pagination.total"
        layout="total, sizes, prev, pager, next"
        @change="loadUsers"
      />
    </el-card>

    <!-- Edit Dialog -->
    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑用户' : '添加用户'" width="500px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" :disabled="!!editingId" />
        </el-form-item>
        <el-form-item label="密码" :prop="editingId ? '' : 'password'">
          <el-input v-model="form.password" type="password" :placeholder="editingId ? '留空则不修改' : '请输入密码'" show-password />
        </el-form-item>
        <el-form-item label="昵称">
          <el-input v-model="form.nickname" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="form.email" />
        </el-form-item>
        <el-form-item label="角色" prop="role">
          <el-select v-model="form.role" :disabled="isEditingAdmin">
            <el-option label="管理员" value="ADMIN" />
            <el-option label="运维" value="OPERATOR" />
            <el-option label="仅查看" value="VIEWER" />
          </el-select>
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="form.enabled" :disabled="isEditingAdmin" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitForm" :loading="submitting" v-permission="editingId ? 'user:edit' : 'user:create'">确定</el-button>
      </template>
    </el-dialog>

    <!-- Sessions Dialog -->
    <el-dialog v-model="sessionsDialogVisible" :title="`在线会话 - ${sessionsUsername}`" width="700px">
      <el-table :data="sessions" stripe>
        <el-table-column label="" width="52">
          <template #default="{ row }">
            <el-tag v-if="row.jti === currentJti" type="success" size="small" effect="dark">当前</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="ipAddress" label="登录IP" width="140" />
        <el-table-column prop="userAgent" label="浏览器" show-overflow-tooltip>
          <template #default="{ row }">
            <span :title="row.userAgent">{{ truncateUA(row.userAgent) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="loginAt" label="登录时间" width="170">
          <template #default="{ row }">{{ formatSessionTime(row.loginAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="80" fixed="right">
          <template #default="{ row }">
            <el-popconfirm v-if="row.jti !== currentJti" title="确认踢出?" @confirm="kickSession(row.jti)">
              <template #reference>
                <el-button type="danger" link size="small" v-permission="'security:kick'">踢出</el-button>
              </template>
            </el-popconfirm>
            <el-tag v-else type="info" size="small" effect="plain">自己</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { userApi, securityApi } from '@/api'
import { ROLES } from '@/constants/enums'
import { formatTime } from '@/utils/format'

const loading = ref(false)
const submitting = ref(false)
const dialogVisible = ref(false)
const editingId = ref(null)
const users = ref([])
const formRef = ref(null)
const onlineCounts = ref({})

const sessionsDialogVisible = ref(false)
const sessionsUsername = ref('')
const sessions = ref([])
const currentJti = ref('')
const lockedUsers = ref([])

const getJtiFromToken = () => {
  try {
    const token = localStorage.getItem('token')
    if (!token) return ''
    const payload = JSON.parse(atob(token.split('.')[1]))
    return payload.jti || ''
  } catch {
    return ''
  }
}

const pagination = reactive({ page: 1, size: 10, total: 0 })

const form = reactive({
  username: '',
  password: '',
  nickname: '',
  email: '',
  role: 'VIEWER',
  enabled: true
})

const isEditingAdmin = computed(() => {
  if (!editingId.value) return false
  const user = users.value.find(u => u.id === editingId.value)
  return user && user.username === 'admin'
})

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
  role: [{ required: true, message: '请选择角色', trigger: 'change' }]
}

onMounted(() => {
  loadUsers()
  loadOnlineCounts()
  loadLockedUsers()
})

const loadUsers = async () => {
  loading.value = true
  try {
    const res = await userApi.list({
      page: pagination.page, size: pagination.size
    })
    users.value = res.data?.records || []
    pagination.total = res.data?.total || 0
  } catch (error) {
    ElMessage.error('加载失败')
  } finally {
    loading.value = false
  }
}

const loadOnlineCounts = async () => {
  try {
    const res = await securityApi.getOnlineCounts()
    onlineCounts.value = res.data || {}
  } catch (error) {
    // ignore
  }
}

const loadLockedUsers = async () => {
  try {
    const res = await securityApi.getLockedUsers()
    lockedUsers.value = res.data || []
  } catch (error) {
    // ignore
  }
}

const showDialog = (row) => {
  if (row) {
    editingId.value = row.id
    Object.assign(form, { ...row, password: '' })
  } else {
    editingId.value = null
    Object.assign(form, {
      username: '', password: '', nickname: '', email: '', role: 'VIEWER', enabled: true
    })
  }
  dialogVisible.value = true
}

const submitForm = async () => {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    const payload = { ...form }
    delete payload.id
    delete payload.mustChangePassword
    delete payload.createdAt
    delete payload.updatedAt
    if (editingId.value) {
      await userApi.update(editingId.value, payload)
      ElMessage.success('更新成功')
    } else {
      await userApi.create(payload)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    loadUsers()
  } catch (error) {
    ElMessage.error(error.message || '操作失败')
  } finally {
    submitting.value = false
  }
}

const deleteUser = async (id) => {
  try {
    await userApi.delete(id)
    ElMessage.success('删除成功')
    loadUsers()
  } catch (error) {
    ElMessage.error(error.message || '删除失败')
  }
}

const showSessions = async (row) => {
  sessionsUsername.value = row.username
  currentJti.value = getJtiFromToken()
  try {
    const res = await securityApi.getUserSessions(row.id)
    sessions.value = res.data || []
    sessionsDialogVisible.value = true
  } catch (error) {
    ElMessage.error('加载会话失败')
  }
}

const kickSession = async (jti) => {
  try {
    await securityApi.kickSession(jti)
    ElMessage.success('已踢出')
    showSessions({ id: users.value.find(u => u.username === sessionsUsername.value)?.id, username: sessionsUsername.value })
    loadOnlineCounts()
  } catch (error) {
    ElMessage.error('操作失败')
  }
}

const truncateUA = (ua) => {
  if (!ua) return '-'
  return ua.length > 50 ? ua.substring(0, 50) + '...' : ua
}

const formatSessionTime = (time) => {
  if (!time) return '-'
  return formatTime(time)
}

const getRoleType = (role) => ROLES[role]?.tagType || 'info'
const getRoleLabel = (role) => ROLES[role]?.label || role
</script>

<style lang="scss" scoped>
.user-list {
  .el-pagination {
    margin-top: 20px;
    justify-content: flex-end;
  }
  .text-muted {
    color: #c0c4cc;
  }
}
</style>
