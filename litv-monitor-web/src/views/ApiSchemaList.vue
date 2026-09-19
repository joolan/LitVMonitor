<template>
  <div class="schema-page">
    <div class="page-header">
      <h2>API Schema 管理</h2>
      <div class="header-actions">
        <el-button type="primary" @click="showAddDialog" v-permission="'api-schema:create'">
          <el-icon><Plus /></el-icon> 添加 Schema
        </el-button>
      </div>
    </div>

    <el-alert type="info" :closable="false" show-icon style="margin-bottom: 16px">
      <template #title>
        管理独立的 JSON Schema 定义。在监控项配置中可以选择 Schema 作为期望响应结构，系统在执行时自动校验并检测变更，触发告警。
      </template>
    </el-alert>

    <el-card class="schema-card">
      <el-table :data="schemas" stripe>
        <el-table-column prop="name" label="名称" />
        <el-table-column prop="description" label="描述" show-overflow-tooltip />
        <el-table-column prop="lastCheckStatus" label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.lastCheckStatus)" size="small">
              {{ getStatusText(row.lastCheckStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="lastCheckedAt" label="最后检查" width="180">
          <template #default="{ row }">
            {{ row.lastCheckedAt ? formatTime(row.lastCheckedAt) : '-' }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200">
          <template #default="{ row }">
            <el-button size="small" @click="editSchema(row)" v-permission="'api-schema:edit'">编辑</el-button>
            <el-button size="small" @click="viewHistory(row)">历史</el-button>
            <el-popconfirm title="确定删除?" @confirm="deleteSchema(row.id)">
              <template #reference>
                <el-button size="small" type="danger" v-permission="'api-schema:delete'">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="editingSchema ? '编辑 Schema' : '添加 Schema'" width="800px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="名称">
          <el-input v-model="form.name" placeholder="Schema 名称" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="JSON Schema">
          <JsonSchemaEditor ref="schemaEditorRef" v-model="form.schemaJson" />
          <div v-if="schemaValidationError" class="schema-validation-error">
            <el-icon><WarningFilled /></el-icon> {{ schemaValidationError }}
          </div>
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="form.enabled" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveSchema">保存</el-button>
      </template>
    </el-dialog>

    <!-- 历史版本弹窗 -->
    <el-dialog v-model="historyVisible" :title="historyFilterLabel ? 'Schema 变更历史 — ' + historyFilterLabel : 'Schema 历史版本'" width="90%" :close-on-click-modal="false">
      <div v-if="!compareVisible" style="margin-bottom: 12px; color: #909399; font-size: 13px">
        选择一条历史记录，点击「对比」查看该版本与当前版本的差异
      </div>

      <!-- 版本列表 -->
      <div v-if="!compareVisible">
        <el-empty v-if="historyList.length === 0" description="暂无历史记录" />
        <el-table v-else :data="historyList" stripe highlight-current-row @current-change="handleHistorySelect">
          <el-table-column v-if="historyFilterLabel" prop="schemaName" label="Schema" width="140" show-overflow-tooltip>
            <template #default="{ row }">
              {{ row.schemaName || '-' }}
            </template>
          </el-table-column>
          <el-table-column prop="changeType" label="类型" width="120">
            <template #default="{ row }">
              <el-tag :type="getHistoryTagType(row.changeType)" size="small">{{ row.changeType }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="changeDescription" label="变更描述" show-overflow-tooltip />
          <el-table-column prop="createdAt" label="时间" width="180">
            <template #default="{ row }">
              {{ formatTime(row.createdAt) }}
            </template>
          </el-table-column>
          <el-table-column label="操作" width="100">
            <template #default="{ row }">
              <el-button size="small" type="primary" @click="compareWithCurrent(row)">对比</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <!-- 左右对比视图 -->
      <div v-if="compareVisible">
        <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px">
          <div style="font-size: 14px">
            <el-tag :type="getHistoryTagType(compareData.changeType)" size="small" style="margin-right: 8px">{{ compareData.changeType }}</el-tag>
            <span style="color: #606266">{{ compareData.changeDescription || '无描述' }}</span>
            <span style="color: #909399; margin-left: 12px">{{ formatTime(compareData.createdAt) }}</span>
          </div>
          <el-button size="small" @click="compareVisible = false">← 返回版本列表</el-button>
        </div>

        <el-row :gutter="16">
          <el-col :span="12">
            <div class="compare-header compare-header-old">
              <el-icon><Warning /></el-icon> 旧版本（修改前）
            </div>
            <div class="schema-editor-container">
              <div v-if="compareData.oldSchema" class="schema-diff-content">
                <pre v-html="highlightJson(compareData.oldSchema, 'old')"></pre>
              </div>
              <el-empty v-else description="空 Schema" :image-size="60" />
            </div>
          </el-col>
          <el-col :span="12">
            <div class="compare-header compare-header-new">
              <el-icon><SuccessFilled /></el-icon> 新版本（修改后）
            </div>
            <div class="schema-editor-container">
              <div v-if="compareData.newSchema" class="schema-diff-content">
                <pre v-html="highlightJson(compareData.newSchema, 'new')"></pre>
              </div>
              <el-empty v-else description="空 Schema" :image-size="60" />
            </div>
          </el-col>
        </el-row>

        <!-- 变更摘要 -->
        <div v-if="compareData.changes && compareData.changes.length > 0" style="margin-top: 16px">
          <div class="compare-section-title">变更摘要（{{ compareData.changes.length }} 项）</div>
          <el-table :data="compareData.changes" stripe size="small">
            <el-table-column prop="changeType" label="类型" width="110">
              <template #default="{ row }">
                <el-tag :type="getHistoryTagType(row.changeType)" size="small">{{ row.changeType }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="fieldPath" label="字段路径" width="200" show-overflow-tooltip />
            <el-table-column prop="description" label="描述" show-overflow-tooltip />
          </el-table>
        </div>
      </div>

      <template #footer>
        <el-button @click="historyVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { schemaApi } from '@/api'
import { ElMessage } from 'element-plus'
import { WarningFilled } from '@element-plus/icons-vue'
import { formatTime } from '@/utils/format'
import JsonSchemaEditor from '@/components/JsonSchemaEditor.vue'

const route = useRoute()
const schemas = ref([])
const historyList = ref([])
const dialogVisible = ref(false)
const historyVisible = ref(false)
const compareVisible = ref(false)
const compareData = ref({})
const editingSchema = ref(null)
const selectedHistory = ref(null)
const schemaEditorRef = ref(null)
const schemaValidationError = ref('')
const historyFilterLabel = ref('')
const form = ref({
  name: '',
  description: '',
  schemaJson: '',
  enabled: true
})

const getStatusType = (status) => {
  if (status === 'OK') return 'success'
  if (status === 'BREAKING') return 'danger'
  return 'info'
}

const getStatusText = (status) => {
  if (status === 'OK') return '正常'
  if (status === 'BREAKING') return '有变更'
  return '未检查'
}

const getHistoryTagType = (type) => {
  if (type === 'BREAKING') return 'danger'
  if (type === 'REMOVED') return 'warning'
  if (type === 'ADDED') return 'success'
  if (type === 'MODIFIED') return 'info'
  return 'info'
}

onMounted(() => {
  loadSchemas()
  const { hours, changeType } = route.query
  if (hours || changeType) {
    loadFilteredHistory(hours ? Number(hours) : null, changeType || null)
  }
})

const loadFilteredHistory = async (hours, changeType) => {
  try {
    const params = {}
    if (hours) params.hours = hours
    if (changeType) params.changeType = changeType
    const res = await schemaApi.getAllHistory(params)
    historyList.value = res.data || []
    const hourLabel = hours ? `近${hours >= 168 ? (hours / 24) + '天' : hours + '小时'}` : '全部'
    const typeLabel = changeType ? { ADDED: '新增', REMOVED: '删除', MODIFIED: '修改', BREAKING: '破坏性' }[changeType] : '所有类型'
    historyFilterLabel.value = `${hourLabel} · ${typeLabel}`
    historyVisible.value = true
  } catch (e) {
    ElMessage.error(e.message)
  }
}

const loadSchemas = async () => {
  try {
    const res = await schemaApi.listAll()
    schemas.value = res.data || []
  } catch (e) {
    console.error(e)
  }
}

const showAddDialog = () => {
  editingSchema.value = null
  form.value = { name: '', description: '', schemaJson: '', enabled: true }
  schemaValidationError.value = ''
  dialogVisible.value = true
}

const editSchema = (schema) => {
  editingSchema.value = schema
  form.value = {
    name: schema.name,
    description: schema.description || '',
    schemaJson: schema.schemaJson,
    enabled: !!schema.enabled
  }
  schemaValidationError.value = ''
  dialogVisible.value = true
}

const saveSchema = async () => {
  // 刷新 Schema 可视化编辑器，避免 300ms 防抖导致最后一次编辑丢失
  schemaEditorRef.value?.flush?.()
  if (!form.value.name) { ElMessage.warning('请填写 Schema 名称'); return }
  if (!form.value.schemaJson || !form.value.schemaJson.trim()) {
    ElMessage.warning('请填写 JSON Schema'); return
  }
  // Validate JSON Schema format
  try {
    const obj = JSON.parse(form.value.schemaJson)
    const errors = validateSchemaStructure(obj)
    if (errors.length > 0) {
      schemaValidationError.value = errors[0]
      ElMessage.error('JSON Schema 格式错误: ' + errors[0])
      return
    }
  } catch (e) {
    schemaValidationError.value = 'JSON 格式错误: ' + e.message
    ElMessage.error('JSON 格式错误: ' + e.message)
    return
  }
  schemaValidationError.value = ''
  try {
    if (editingSchema.value) {
      await schemaApi.update(editingSchema.value.id, form.value)
    } else {
      await schemaApi.create(form.value)
    }
    ElMessage.success('保存成功')
    dialogVisible.value = false
    loadSchemas()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

function validateSchemaStructure(obj) {
  const errors = []
  if (!obj || typeof obj !== 'object' || Array.isArray(obj)) {
    errors.push('根节点必须是对象'); return errors
  }
  const validTypes = ['object', 'array', 'string', 'integer', 'number', 'boolean', 'null']
  if (!obj.type) errors.push('必须定义 "type" 字段')
  else if (!validTypes.includes(obj.type)) errors.push('"type" 值无效，允许: ' + validTypes.join(', '))
  if (obj.required && !Array.isArray(obj.required)) errors.push('"required" 必须是数组')
  if (obj.properties && (typeof obj.properties !== 'object' || Array.isArray(obj.properties)))
    errors.push('"properties" 必须是对象')
  if (obj.type === 'object' && obj.properties) {
    Object.entries(obj.properties).forEach(([name, schema]) => {
      if (!name.trim()) errors.push('字段名不能为空')
      if (typeof schema !== 'object' || schema === null)
        errors.push('字段 "' + name + '" 的 schema 必须是对象')
      else validateSchemaStructure(schema).forEach(e => errors.push(name + '.' + e))
    })
  }
  if (obj.type === 'array' && obj.items) {
    if (typeof obj.items !== 'object') errors.push('items 必须是对象')
    else validateSchemaStructure(obj.items).forEach(e => errors.push('items.' + e))
  }
  return errors
}

const deleteSchema = async (id) => {
  try {
    await schemaApi.delete(id)
    ElMessage.success('删除成功')
    loadSchemas()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

const viewHistory = async (schema) => {
  compareVisible.value = false
  compareData.value = {}
  selectedHistory.value = null
  try {
    const res = await schemaApi.getHistory(schema.id)
    historyList.value = res.data || []
    historyVisible.value = true
  } catch (e) {
    ElMessage.error(e.message)
  }
}

const handleHistorySelect = (row) => {
  selectedHistory.value = row
}

const compareWithCurrent = async (row) => {
  try {
    const res = await schemaApi.compareVersions(row.id)
    compareData.value = res.data || {}
    compareVisible.value = true
  } catch (e) {
    ElMessage.error(e.message)
  }
}

const highlightJson = (jsonStr, side) => {
  if (!jsonStr) return ''
  let formatted
  try {
    formatted = JSON.stringify(JSON.parse(jsonStr), null, 2)
  } catch {
    formatted = jsonStr
  }
  const lines = formatted.split('\n')
  const changeMap = {}
  if (compareData.value.changes) {
    compareData.value.changes.forEach(c => {
      const key = c.fieldPath
      changeMap[key] = c.changeType
    })
  }

  return lines.map(line => {
    let escaped = line.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')

    const fieldMatch = escaped.match(/^\s*"([^"]+)"/)
    if (fieldMatch) {
      const fieldName = fieldMatch[1]
      const changeType = changeMap[fieldName] || changeMap['.' + fieldName]
      if (changeType === 'ADDED' && side === 'new') {
        escaped = `<span class="diff-added">${escaped}</span>`
      } else if (changeType === 'REMOVED' && side === 'old') {
        escaped = `<span class="diff-removed">${escaped}</span>`
      } else if (changeType === 'MODIFIED') {
        escaped = `<span class="diff-modified">${escaped}</span>`
      }
    }
    return escaped
  }).join('\n')
}
</script>

<style lang="scss" scoped>
.schema-page {
  padding: 20px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;

  h2 {
    margin: 0;
  }
}

.schema-card {
  margin-bottom: 20px;
}

.compare-header {
  padding: 10px 16px;
  font-size: 14px;
  font-weight: 600;
  border-radius: 6px 6px 0 0;
  display: flex;
  align-items: center;
  gap: 6px;

  &-old {
    background: #fef0f0;
    color: #f56c6c;
    border: 1px solid #fbc4c4;
    border-bottom: none;
  }

  &-new {
    background: #f0f9eb;
    color: #67c23a;
    border: 1px solid #c2e7b0;
    border-bottom: none;
  }
}

.schema-editor-container {
  border: 1px solid #e4e7ed;
  border-radius: 0 0 6px 6px;
  max-height: 500px;
  overflow: auto;
  background: #fafafa;
}

.schema-diff-content {
  padding: 16px;

  pre {
    margin: 0;
    font-size: 12px;
    line-height: 1.6;
    font-family: 'Courier New', monospace;
    white-space: pre-wrap;
    word-break: break-all;
    color: #303133;

    :deep(.diff-added) {
      background: #e1f3d8;
      color: #529b2e;
      display: inline;
      padding: 1px 2px;
      border-radius: 2px;
    }

    :deep(.diff-removed) {
      background: #fde2e2;
      color: #c45656;
      display: inline;
      padding: 1px 2px;
      border-radius: 2px;
      text-decoration: line-through;
    }

    :deep(.diff-modified) {
      background: #fdf6ec;
      color: #e6a23c;
      display: inline;
      padding: 1px 2px;
      border-radius: 2px;
    }
  }
}

.compare-section-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 8px;
}

.schema-validation-error {
  margin-top: 8px;
  padding: 6px 10px;
  font-size: 12px;
  color: #f56c6c;
  background: #fef0f0;
  border-radius: 4px;
  display: flex;
  align-items: center;
  gap: 4px;
}
</style>
