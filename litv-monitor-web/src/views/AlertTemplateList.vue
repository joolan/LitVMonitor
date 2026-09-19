<template>
  <div class="alert-template-list">
    <div class="page-header">
      <h2>告警模板</h2>
      <el-button type="primary" @click="showDialog()" v-permission="'alert-template:create'">
        <el-icon><Plus /></el-icon>
        添加模板
      </el-button>
    </div>

    <el-alert type="info" :closable="false" show-icon style="margin-bottom: 16px">
      <template #title>
        同一触发类型只能有一个启用的模板。启用新模板时，同类型的其他模板会自动禁用。系统预置模板不可删除，可查看、启用或停用。用户手动添加的模板为自定义类型。
      </template>
    </el-alert>

    <el-card>
      <el-table :data="templates" stripe v-loading="loading">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="name" label="模板名称" min-width="140" />
        <el-table-column prop="templateType" label="模板类型" width="100">
          <template #default="{ row }">
            <el-tag :type="row.templateType === 'SYSTEM' ? 'info' : ''" size="small">
              {{ row.templateType === 'SYSTEM' ? '系统预置' : '自定义' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="triggerType" label="触发类型" width="120">
          <template #default="{ row }">
            <el-tag :type="getTriggerTypeTag(row.triggerType)" size="small">
              {{ getTriggerTypeLabel(row.triggerType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="content" label="模板内容" min-width="250">
          <template #default="{ row }">
            <el-text v-if="row.content" truncated size="small">{{ row.content }}</el-text>
            <el-text v-else type="info" size="small">使用默认模板</el-text>
          </template>
        </el-table-column>
        <el-table-column prop="cooldownMinutes" label="冷却时间" width="100">
          <template #default="{ row }">
            {{ row.cooldownMinutes != null ? row.cooldownMinutes + ' 分钟' : '30 分钟' }}
          </template>
        </el-table-column>
        <el-table-column label="限频" width="140">
          <template #default="{ row }">
            <span v-if="row.rateLimitEnabled">
              <el-tag size="small" type="warning">{{ row.rateLimitCount }}次</el-tag>
              <el-tag v-if="row.recoveryNotify" size="small" type="success" style="margin-left: 4px">恢复通知</el-tag>
            </span>
            <span v-else style="color: #909399">未启用</span>
          </template>
        </el-table-column>
        <el-table-column prop="enabled" label="状态" width="80">
          <template #default="{ row }">
            <el-switch v-model="row.enabled" size="small"
              @change="toggleEnabled(row)"
              v-permission="row.enabled ? 'alert-template:disable' : 'alert-template:enable'" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="showDialog(row)" v-permission="'alert-template:edit'">编辑</el-button>
            <el-popconfirm v-if="row.templateType !== 'SYSTEM'" title="确认删除?" @confirm="deleteTemplate(row.id)">
              <template #reference>
                <el-button type="danger" link v-permission="'alert-template:delete'">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- Dialog -->
    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑模板' : '添加模板'" width="700px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="模板名称" prop="name">
          <el-input v-model="form.name" placeholder="例如: 执行失败告警、SSL证书告警" />
        </el-form-item>
        <el-form-item label="触发类型" prop="triggerType">
          <el-select v-model="form.triggerType" style="width: 100%">
            <el-option label="执行失败 (FAIL)" value="FAIL" />
            <el-option label="响应超时 (RESPONSE_TIME)" value="RESPONSE_TIME" />
            <el-option label="任务失败 (GROUP_FAIL)" value="GROUP_FAIL" />
            <el-option label="SSL证书 (SSL_CERT)" value="SSL_CERT" />
            <el-option label="API Schema变更 (SCHEMA_CHANGE)" value="SCHEMA_CHANGE" />
            <el-option label="通用 (ALL)" value="ALL" />
          </el-select>
        </el-form-item>
        <el-form-item label="冷却时间">
          <el-input-number v-model="form.cooldownMinutes" :min="0" :max="1440" :step="5" style="width: 200px" />
          <span style="margin-left: 8px; color: #909399; font-size: 12px">分钟，同类型告警在此时间内只发送一次</span>
        </el-form-item>

        <el-divider content-position="left">告警限频</el-divider>
        <el-form-item label="启用限频">
          <el-switch v-model="form.rateLimitEnabled" />
          <span style="margin-left: 8px; color: #909399; font-size: 12px">相同告警达到次数上限后抑制通知（日志仍记录）</span>
        </el-form-item>
        <el-form-item v-if="form.rateLimitEnabled" label="最大告警次数">
          <el-input-number v-model="form.rateLimitCount" :min="1" :max="9999" style="width: 200px" />
          <span style="margin-left: 8px; color: #909399; font-size: 12px">相同监控项/任务/域名的告警累计</span>
        </el-form-item>
        <el-form-item v-if="form.rateLimitEnabled" label="恢复通知">
          <el-switch v-model="form.recoveryNotify" />
          <span style="margin-left: 8px; color: #909399; font-size: 12px">告警恢复后发送已恢复通知</span>
        </el-form-item>
        <el-form-item v-if="form.rateLimitEnabled && form.recoveryNotify" label="连续正常次数">
          <el-input-number v-model="form.recoveryConsecutiveCount" :min="1" :max="100" style="width: 200px" />
          <span style="margin-left: 8px; color: #909399; font-size: 12px">连续成功N次后才发送恢复通知，避免偶然恢复</span>
        </el-form-item>
        <el-form-item label="兜底告警渠道">
          <el-select v-model="form.fallbackChannelIds" multiple placeholder="选择兜底渠道" style="width: 100%">
            <el-option v-for="c in channels" :key="c.id" :label="c.name" :value="String(c.id)">
              <span>{{ c.name }}</span>
              <el-tag :type="c.enabled ? 'success' : 'info'" size="small" style="margin-left: 8px; float: right">
                {{ c.enabled ? '启用' : '禁用' }}
              </el-tag>
            </el-option>
          </el-select>
          <div style="margin-top: 4px; color: #909399; font-size: 12px">
            <el-icon><InfoFilled /></el-icon>
            仅当触发告警且未配置告警渠道时使用兜底渠道；若因自定义模板被删除/停用而降级使用默认模板，则不触发兜底告警
          </div>
        </el-form-item>
        <el-form-item label="模板内容">
          <el-input v-model="form.content" type="textarea" :rows="8"
            placeholder="留空使用默认模板，自定义时可使用以下变量:" />
        </el-form-item>
        <el-form-item>
          <div class="template-variables">
            <div class="form-tip">可用预制变量:</div>
            <div class="variable-tags">
              <el-tag size="small" @click="insertVariable('monitorId')">monitorId</el-tag>
              <el-tag size="small" @click="insertVariable('monitorName')">monitorName</el-tag>
              <el-tag size="small" @click="insertVariable('groupId')">groupId</el-tag>
              <el-tag size="small" @click="insertVariable('status')">status</el-tag>
              <el-tag size="small" @click="insertVariable('statusCode')">statusCode</el-tag>
              <el-tag size="small" @click="insertVariable('errorMessage')">errorMessage</el-tag>
              <el-tag size="small" @click="insertVariable('executedAt')">executedAt</el-tag>
              <el-tag size="small" @click="insertVariable('responseTime')">responseTime</el-tag>
              <el-tag size="small" @click="insertVariable('domain')">domain</el-tag>
              <el-tag size="small" @click="insertVariable('url')">url</el-tag>
            </div>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitForm" :loading="submitting" v-permission="editingId ? 'alert-template:edit' : 'alert-template:create'">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { InfoFilled } from '@element-plus/icons-vue'
import { alertTemplateApi, alertChannelApi } from '@/api'
import { TRIGGER_TYPES } from '@/constants/enums'

const loading = ref(false)
const submitting = ref(false)
const dialogVisible = ref(false)
const editingId = ref(null)
const templates = ref([])
const channels = ref([])
const formRef = ref(null)

const form = reactive({
  name: '',
  triggerType: 'FAIL',
  content: '',
  cooldownMinutes: 30,
  enabled: false,
  rateLimitEnabled: false,
  rateLimitCount: 5,
  recoveryNotify: false,
  recoveryConsecutiveCount: 3,
  fallbackChannelIds: []
})

const rules = {
  name: [{ required: true, message: '请输入模板名称', trigger: 'blur' }],
  triggerType: [{ required: true, message: '请选择触发类型', trigger: 'change' }]
}

onMounted(() => { loadTemplates(); loadChannels() })

const loadChannels = async () => {
  try {
    const res = await alertChannelApi.list()
    channels.value = res.data || []
  } catch (error) {
    console.error('Failed to load channels', error)
  }
}

const loadTemplates = async () => {
  loading.value = true
  try {
    const res = await alertTemplateApi.list()
    templates.value = res.data || []
  } catch (error) {
    ElMessage.error('加载失败')
  } finally {
    loading.value = false
  }
}

const getTriggerTypeTag = (type) => TRIGGER_TYPES[type]?.tagType || 'info'
const getTriggerTypeLabel = (type) => TRIGGER_TYPES[type]?.label || type

const showDialog = (row) => {
  if (row) {
    editingId.value = row.id
    form.name = row.name
    form.triggerType = row.triggerType
    form.content = row.content || ''
    form.cooldownMinutes = row.cooldownMinutes != null ? row.cooldownMinutes : 30
    form.enabled = row.enabled
    form.rateLimitEnabled = row.rateLimitEnabled || false
    form.rateLimitCount = row.rateLimitCount || 5
    form.recoveryNotify = row.recoveryNotify || false
    form.recoveryConsecutiveCount = row.recoveryConsecutiveCount || 3
    form.fallbackChannelIds = row.fallbackChannelIds ? row.fallbackChannelIds.split(',').filter(Boolean) : []
  } else {
    editingId.value = null
    Object.assign(form, { name: '', triggerType: 'FAIL', content: '', cooldownMinutes: 30, enabled: false, rateLimitEnabled: false, rateLimitCount: 5, recoveryNotify: false, recoveryConsecutiveCount: 3, fallbackChannelIds: [] })
  }
  dialogVisible.value = true
}

const insertVariable = (varName) => {
  form.content = (form.content || '') + '{{' + varName + '}}'
}

const toggleEnabled = async (row) => {
  if (row.enabled) {
    // Enabling: check if another template of same type is already enabled
    const sameType = templates.value.filter(t => t.triggerType === row.triggerType && t.id !== row.id && t.enabled)
    if (sameType.length > 0) {
      try {
        await ElMessageBox.confirm(
          `已存在启用的「${getTriggerTypeLabel(row.triggerType)}」类型模板，启用此模板将自动禁用其他同类型模板，是否继续？`,
          '提示',
          { type: 'warning' }
        )
      } catch {
        row.enabled = false
        return
      }
      try {
        await alertTemplateApi.enable(row.id)
        ElMessage.success('启用成功，同类型其他模板已自动禁用')
        loadTemplates()
      } catch (error) {
        ElMessage.error('操作失败')
        row.enabled = false
      }
    } else {
      try {
        await alertTemplateApi.enable(row.id)
        ElMessage.success('启用成功')
      } catch (error) {
        ElMessage.error('操作失败')
        row.enabled = false
      }
    }
  } else {
    try {
      await alertTemplateApi.disable(row.id)
      ElMessage.success('已禁用')
    } catch (error) {
      ElMessage.error('操作失败')
      row.enabled = true
    }
  }
}

const submitForm = async () => {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  const payload = { ...form }
  if (Array.isArray(payload.fallbackChannelIds)) {
    payload.fallbackChannelIds = payload.fallbackChannelIds.join(',')
  }

  submitting.value = true
  try {
    if (editingId.value) {
      await alertTemplateApi.update(editingId.value, payload)
      ElMessage.success('更新成功')
    } else {
      await alertTemplateApi.create(payload)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    loadTemplates()
  } catch (error) {
    ElMessage.error(error.message || '操作失败')
  } finally {
    submitting.value = false
  }
}

const deleteTemplate = async (id) => {
  try {
    await alertTemplateApi.delete(id)
    ElMessage.success('删除成功')
    loadTemplates()
  } catch (error) {
    ElMessage.error('删除失败')
  }
}
</script>

<style lang="scss" scoped>
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  h2 { margin: 0; }
}

.form-tip {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}

.template-variables {
  .variable-tags {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    margin-top: 8px;
    .el-tag {
      cursor: pointer;
      &:hover { opacity: 0.8; }
    }
  }
}
</style>
