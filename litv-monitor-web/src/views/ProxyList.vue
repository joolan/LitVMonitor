<template>
  <div class="proxy-list">
    <div class="page-header">
      <h2>代理设置</h2>
      <el-button type="primary" @click="showDialog()" v-permission="'proxy:create'">
        <el-icon><Plus /></el-icon>
        添加代理
      </el-button>
    </div>

    <el-card>
      <el-alert type="info" :closable="false" show-icon style="margin-bottom: 16px">
        <template #title>
          配置HTTP/SOCKS5代理。所有监控项的HTTP请求将通过当前启用的代理发送。同时只能启用一个代理，或停用全部代理。
        </template>
      </el-alert>

      <el-table :data="proxies" stripe v-loading="loading">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="name" label="名称" min-width="150" />
        <el-table-column prop="proxyType" label="类型" width="100">
          <template #default="{ row }">
            <el-tag :type="row.proxyType === 'SOCKS5' ? 'warning' : 'primary'" size="small">
              {{ row.proxyType }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="地址" min-width="200">
          <template #default="{ row }">
            <span>{{ row.host }}:{{ row.port }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="username" label="认证" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.username" type="success" size="small">有</el-tag>
            <el-tag v-else type="info" size="small">无</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.active" type="success" size="small" effect="dark">已启用</el-tag>
            <el-tag v-else type="info" size="small">未启用</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="180">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button v-if="!row.active" type="success" link @click="activateProxy(row.id)" v-permission="'proxy:activate'">
              启用
            </el-button>
            <el-button v-if="row.active" type="warning" link @click="deactivateAll" v-permission="'proxy:activate'">
              停用
            </el-button>
            <el-button type="info" link @click="testProxy(row.id)" :loading="testingId === row.id" v-permission="'proxy:test'">
              测试
            </el-button>
            <el-button type="primary" link @click="showDialog(row)" v-permission="'proxy:edit'">编辑</el-button>
            <el-popconfirm title="确认删除此代理配置?" @confirm="deleteProxy(row.id)">
              <template #reference>
                <el-button type="danger" link v-permission="'proxy:delete'">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- Dialog -->
    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑代理' : '添加代理'" width="550px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="120px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" placeholder="如: 公司代理、测试代理" />
        </el-form-item>
        <el-form-item label="代理类型" prop="proxyType">
          <el-radio-group v-model="form.proxyType">
            <el-radio value="HTTP">HTTP</el-radio>
            <el-radio value="SOCKS5">SOCKS5</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="主机" prop="host">
          <el-input v-model="form.host" placeholder="如: 127.0.0.1 或 proxy.example.com" />
        </el-form-item>
        <el-form-item label="端口" prop="port">
          <el-input-number v-model="form.port" :min="1" :max="65535" style="width: 200px" />
        </el-form-item>
        <el-form-item label="用户名">
          <el-input v-model="form.username" placeholder="可选，留空则不认证" />
        </el-form-item>
        <el-form-item v-if="form.username" label="密码">
          <el-input v-model="form.password" type="password" show-password
            :placeholder="editingId ? '留空表示不修改' : '代理密码'" />
        </el-form-item>
        <el-form-item label="立即启用">
          <el-switch v-model="form.active" />
          <div style="color: #909399; font-size: 12px; margin-top: 4px">启用后将替代当前代理（同时只能启用一个）</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitForm" :loading="submitting">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { proxyApi } from '@/api'
import { formatTime } from '@/utils/format'

const loading = ref(false)
const submitting = ref(false)
const dialogVisible = ref(false)
const editingId = ref(null)
const togglingId = ref(null)
const testingId = ref(null)
const proxies = ref([])
const formRef = ref(null)

const form = reactive({
  name: '',
  proxyType: 'HTTP',
  host: '',
  port: 8080,
  username: '',
  password: '',
  active: false,
  enabled: true
})

const rules = {
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  proxyType: [{ required: true, message: '请选择类型', trigger: 'change' }],
  host: [{ required: true, message: '请输入主机地址', trigger: 'blur' }],
  port: [{ required: true, message: '请输入端口', trigger: 'blur' }]
}

onMounted(() => {
  loadProxies()
})

const loadProxies = async () => {
  loading.value = true
  try {
    const res = await proxyApi.list()
    proxies.value = res.data || []
  } catch (error) {
    ElMessage.error('加载失败')
  } finally {
    loading.value = false
  }
}

const showDialog = (row) => {
  if (row) {
    editingId.value = row.id
    Object.assign(form, {
      name: row.name,
      proxyType: row.proxyType || 'HTTP',
      host: row.host,
      port: row.port,
      username: row.username || '',
      password: '',   // 不回填密码，留空表示不修改
      active: row.active || false,
      enabled: row.enabled !== false
    })
  } else {
    editingId.value = null
    Object.assign(form, {
      name: '', proxyType: 'HTTP', host: '', port: 8080,
      username: '', password: '', active: false, enabled: true
    })
  }
  dialogVisible.value = true
}

const submitForm = async () => {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    const data = { ...form }
    if (!data.username) data.password = ''
    // 编辑时密码留空 = 不修改，不提交该字段
    if (editingId.value && !data.password) delete data.password

    if (editingId.value) {
      await proxyApi.update(editingId.value, data)
      ElMessage.success('更新成功')
    } else {
      await proxyApi.create(data)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    loadProxies()
  } catch (error) {
    ElMessage.error(error.message || '操作失败')
  } finally {
    submitting.value = false
  }
}

const deleteProxy = async (id) => {
  try {
    await proxyApi.delete(id)
    ElMessage.success('删除成功')
    loadProxies()
  } catch (error) {
    ElMessage.error('删除失败')
  }
}

const activateProxy = async (id) => {
  togglingId.value = id
  try {
    await proxyApi.activate(id)
    ElMessage.success('已启用该代理')
    loadProxies()
  } catch (error) {
    ElMessage.error('启用失败')
  } finally {
    togglingId.value = null
  }
}

const deactivateAll = async () => {
  try {
    await proxyApi.deactivate()
    ElMessage.success('已停用所有代理')
    loadProxies()
  } catch (error) {
    ElMessage.error('操作失败')
  }
}

const testProxy = async (id) => {
  testingId.value = id
  try {
    await proxyApi.test(id)
    ElMessage.success('代理测试成功')
  } catch (error) {
    ElMessage.error(error.message || '代理测试失败')
  } finally {
    testingId.value = null
  }
}

</script>

<style lang="scss" scoped>
.proxy-list {
  .el-pagination {
    margin-top: 20px;
    justify-content: flex-end;
  }
}
</style>
