<template>
  <div class="variable-list">
    <div class="page-header">
      <h2>变量管理</h2>
    </div>

    <el-card>
      <el-tabs v-model="activeTab" @tab-change="handleTabChange">
        <!-- 全局变量 -->
        <el-tab-pane label="全局变量" name="global">
          <div class="tab-header">
            <el-button type="primary" @click="showDialog('global')" v-permission="'variable:create'">
              <el-icon><Plus /></el-icon>
              添加全局变量
            </el-button>
          </div>
          <el-alert type="info" :closable="false" show-icon style="margin-bottom: 16px">
            <template #title>
              全局变量可被所有任务和监控项使用，值可编辑并持久化存储
            </template>
          </el-alert>

          <el-table :data="globalVariables" stripe v-loading="loading">
            <el-table-column prop="id" label="ID" width="60" />
            <el-table-column prop="name" label="变量名" min-width="150">
              <template #default="{ row }">
                <code v-text="'{{global.' + row.name + '}}'" />
              </template>
            </el-table-column>
            <el-table-column prop="value" label="值" min-width="200" show-overflow-tooltip />
            <el-table-column prop="description" label="描述" min-width="150" show-overflow-tooltip />
            <el-table-column prop="isSecret" label="敏感" width="80">
              <template #default="{ row }">
                <el-tag :type="row.isSecret ? 'warning' : 'info'" size="small">
                  {{ row.isSecret ? '是' : '否' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="createdAt" label="创建时间" width="180">
              <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="150" fixed="right">
              <template #default="{ row }">
                <el-button type="primary" link @click="showDialog('global', row)" v-permission="'variable:edit'">编辑</el-button>
                <el-popconfirm title="确认删除?" @confirm="deleteVariable('global', row.id)">
                  <template #reference>
                    <el-button type="danger" link v-permission="'variable:delete'">删除</el-button>
                  </template>
                </el-popconfirm>
              </template>
            </el-table-column>
          </el-table>

          <el-pagination
            v-model:current-page="globalPagination.page"
            v-model:page-size="globalPagination.size"
            :total="globalPagination.total"
            layout="total, sizes, prev, pager, next"
            @change="loadGlobalVariables"
          />
        </el-tab-pane>

        <!-- 任务变量 -->
        <el-tab-pane label="任务变量" name="group">
          <div class="tab-header">
            <el-button type="primary" @click="showDialog('group')" v-permission="'variable:create'">
              <el-icon><Plus /></el-icon>
              添加任务变量
            </el-button>
          </div>
          <el-alert type="info" :closable="false" show-icon style="margin-bottom: 16px">
            <template #title>
              任务变量仅在任务执行时有效，任何监控项都可在变量赋值中选择任务变量进行读写，每次任务运行时重置
            </template>
          </el-alert>

          <el-table :data="groupVariables" stripe v-loading="loading">
            <el-table-column prop="id" label="ID" width="60" />
            <el-table-column prop="name" label="变量名" min-width="150">
              <template #default="{ row }">
                <code v-text="'{{group.' + row.name + '}}'" />
              </template>
            </el-table-column>
            <el-table-column prop="createdAt" label="创建时间" width="180">
              <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="150" fixed="right">
              <template #default="{ row }">
                <el-popconfirm title="确认删除?" @confirm="deleteVariable('group', row.id)">
                  <template #reference>
                    <el-button type="danger" link v-permission="'variable:delete'">删除</el-button>
                  </template>
                </el-popconfirm>
              </template>
            </el-table-column>
          </el-table>

          <el-pagination
            v-model:current-page="groupPagination.page"
            v-model:page-size="groupPagination.size"
            :total="groupPagination.total"
            layout="total, sizes, prev, pager, next"
            @change="loadGroupVariables"
          />
        </el-tab-pane>

        <!-- 内置变量 -->
        <el-tab-pane label="内置变量" name="builtin">
          <el-alert type="warning" :closable="false" show-icon style="margin-bottom: 16px">
            <template #title>
              内置变量由系统自动生成，每次请求时重新计算，无需创建。可在URL、请求头、请求体中使用 <code v-text="'{{env.xxx}}'"></code> 语法引用。
            </template>
          </el-alert>

          <el-table :data="builtinVars" stripe>
            <el-table-column prop="name" label="变量名" width="220">
              <template #default="{ row }">
                <code v-text="'{{env.' + row.name + '}}'" />
              </template>
            </el-table-column>
            <el-table-column prop="desc" label="说明" min-width="200" />
            <el-table-column prop="example" label="示例值" min-width="220" />
            <el-table-column prop="scope" label="作用域" width="100">
              <template #default="{ row }">
                <el-tag :type="row.scope === '时间' ? 'primary' : row.scope === '随机' ? 'success' : 'info'" size="small">
                  {{ row.scope }}
                </el-tag>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- Dialog -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="500px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="变量名" prop="name">
          <el-input v-model="form.name" placeholder="例如: baseUrl, TOKEN" :disabled="!!editingId" />
        </el-form-item>
        <template v-if="currentTab === 'global'">
          <el-form-item label="值" prop="value">
            <el-input v-model="form.value" type="textarea" :rows="3"
              :placeholder="editingId && form.isSecret ? '留空则不修改原值' : ''" />
          </el-form-item>
          <el-form-item label="描述">
            <el-input v-model="form.description" />
          </el-form-item>
          <el-form-item label="敏感信息">
            <el-switch v-model="form.isSecret" />
          </el-form-item>
        </template>
        <template v-if="currentTab === 'group'">
          <el-form-item>
            <el-text type="info" size="small">
              任务变量仅需定义变量名，值在任务运行时由监控项自动提取赋值
            </el-text>
          </el-form-item>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitForm" :loading="submitting" v-permission="editingVariable ? 'variable:edit' : 'variable:create'">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { variableApi } from '@/api'
import { formatTime } from '@/utils/format'

const loading = ref(false)
const submitting = ref(false)
const dialogVisible = ref(false)
const editingId = ref(null)
const currentTab = ref('global')
const activeTab = ref('global')
const globalVariables = ref([])
const groupVariables = ref([])
const formRef = ref(null)

const globalPagination = reactive({ page: 1, size: 10, total: 0 })
const groupPagination = reactive({ page: 1, size: 10, total: 0 })

const builtinVars = ref([
  { name: 'timestamp', desc: '当前时间戳（毫秒）', example: '1757270400000', scope: '时间' },
  { name: 'timestamp_s', desc: '当前时间戳（秒）', example: '1757270400', scope: '时间' },
  { name: 'time', desc: '北京时间 HH:mm:ss', example: '16:30:45', scope: '时间' },
  { name: 'date', desc: '北京时间 yyyy-MM-dd', example: '2026-09-07', scope: '时间' },
  { name: 'datetime', desc: '北京时间 yyyy-MM-dd HH:mm:ss', example: '2026-09-07 16:30:45', scope: '时间' },
  { name: 'datetime_ms', desc: '北京时间带毫秒', example: '2026-09-07 16:30:45.123', scope: '时间' },
  { name: 'unix', desc: 'Unix时间戳（秒）', example: '1757270400', scope: '时间' },
  { name: 'uuid', desc: '随机UUID', example: '550e8400-e29b-41d4-a716-446655440000', scope: '随机' },
  { name: 'uuid_short', desc: '短UUID（8位）', example: 'a3f2b8c1', scope: '随机' },
  { name: 'random', desc: '0~999999 随机数', example: '48291', scope: '随机' },
  { name: 'nonce', desc: '32位随机字符串', example: 'k8j2h5g9d2m7n4p1q6w3e9r5t8y2x7c4', scope: '随机' }
])

const form = reactive({
  name: '',
  value: '',
  description: '',
  isSecret: false
})

const rules = {
  name: [{ required: true, message: '请输入变量名', trigger: 'blur' }],
  value: []
}

const dialogTitle = computed(() => {
  const prefix = currentTab.value === 'global' ? '全局' : '任务'
  return editingId.value ? `编辑${prefix}变量` : `添加${prefix}变量`
})

onMounted(() => {
  loadGlobalVariables()
})

const handleTabChange = (tab) => {
  currentTab.value = tab
  if (tab === 'group') {
    loadGroupVariables()
  }
}

const loadGlobalVariables = async () => {
  loading.value = true
  try {
    const res = await variableApi.listGlobal({
      page: globalPagination.page,
      size: globalPagination.size
    })
    globalVariables.value = res.data?.records || []
    globalPagination.total = res.data?.total || 0
  } catch (error) {
    ElMessage.error('加载失败')
  } finally {
    loading.value = false
  }
}

const loadGroupVariables = async () => {
  loading.value = true
  try {
    const res = await variableApi.listAllGroup()
    groupVariables.value = res.data || []
  } catch (error) {
    ElMessage.error('加载失败')
  } finally {
    loading.value = false
  }
}

const showDialog = (tab, row) => {
  currentTab.value = tab
  if (row) {
    editingId.value = row.id
    Object.assign(form, {
      name: row.name,
      value: row.isSecret ? '' : (row.value || ''),
      description: row.description || '',
      isSecret: row.isSecret || false
    })
  } else {
    editingId.value = null
    Object.assign(form, { name: '', value: '', description: '', isSecret: false })
  }
  dialogVisible.value = true
}

const submitForm = async () => {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    if (currentTab.value === 'global') {
      if (editingId.value) {
        const data = { ...form }
        if (form.isSecret && !data.value) data.value = null
        await variableApi.updateGlobal(editingId.value, data)
        ElMessage.success('更新成功')
      } else {
        await variableApi.createGlobal(form)
        ElMessage.success('创建成功')
      }
      loadGlobalVariables()
    } else {
      const data = { name: form.name }
      if (editingId.value) {
        await variableApi.updateGroup(editingId.value, data)
        ElMessage.success('更新成功')
      } else {
        await variableApi.createGroup(data)
        ElMessage.success('创建成功')
      }
      loadGroupVariables()
    }
    dialogVisible.value = false
  } catch (error) {
    ElMessage.error(error.message || '操作失败')
  } finally {
    submitting.value = false
  }
}

const deleteVariable = async (tab, id) => {
  try {
    if (tab === 'global') {
      await variableApi.deleteGlobal(id)
      loadGlobalVariables()
    } else {
      await variableApi.deleteGroup(id)
      loadGroupVariables()
    }
    ElMessage.success('删除成功')
  } catch (error) {
    ElMessage.error('删除失败')
  }
}
</script>

<style lang="scss" scoped>
.variable-list {
  .tab-header {
    margin-bottom: 16px;
    display: flex;
    align-items: center;
  }

  .el-pagination {
    margin-top: 20px;
    justify-content: flex-end;
  }

  code {
    background: #f5f7fa;
    padding: 2px 6px;
    border-radius: 4px;
    font-size: 12px;
    color: #e6a23c;
  }
}
</style>
