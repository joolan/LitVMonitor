<template>
  <div class="reminder-list">
    <div class="page-header">
      <h2>周期提醒</h2>
      <div class="header-actions">
        <el-button type="primary" @click="showDialog()" v-permission="'reminder:create'">
          <el-icon><Plus /></el-icon>
          新建提醒
        </el-button>
      </div>
    </div>

    <el-alert type="info" :closable="false" show-icon style="margin-bottom: 16px;">
      <template #title>
        仪表盘「周期提醒」角标显示的是<b>已过期</b> + <b>今天到期</b> + <b>近7天到期</b>的提醒合计数量。
        到期后系统<b>只发送通知、不会自动完成</b>：未处理的任务会一直保持「已过期」，直到手动点击「完成」；
        周期性任务点击「完成」后会推进到下一次到期时间。
      </template>
    </el-alert>

    <!-- Filters -->
    <el-card class="filter-card" shadow="never">
      <el-form :inline="true" :model="filters" @submit.prevent="loadReminders">
        <el-form-item label="分类">
          <el-select v-model="filters.category" clearable placeholder="全部" style="width: 140px" @change="handleFilterChange">
            <el-option label="服务器续费" value="SERVER_RENEWAL" />
            <el-option label="SSL证书续费" value="SSL_RENEWAL" />
            <el-option label="机房费用" value="UTILITY" />
            <el-option label="定期巡检" value="ON_SITE_INSPECTION" />
            <el-option label="事务提醒" value="TASK_REMINDER" />
            <el-option label="其他" value="OTHER" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filters.status" clearable placeholder="全部" style="width: 140px" @change="handleFilterChange">
            <el-option label="已过期" value="overdue" />
            <el-option label="今天到期" value="today" />
            <el-option label="即将到期" value="upcoming" />
            <el-option label="正常" value="normal" />
            <el-option label="已完成" value="completed" />
          </el-select>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- Table -->
    <el-card>
      <el-table :data="reminders" stripe v-loading="loading">
        <el-table-column prop="title" label="标题" min-width="180" />
        <el-table-column label="分类" width="130">
          <template #default="{ row }">
            <el-tag size="small" :type="getCategoryTagType(row.category)">{{ getCategoryLabel(row.category) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="dueDate" label="首次提醒时间" width="170" />
        <el-table-column label="重复类型" width="100">
          <template #default="{ row }">{{ getRecurrenceLabel(row.recurrenceType) }}</template>
        </el-table-column>
        <el-table-column label="剩余/状态" width="120">
          <template #default="{ row }">
            <el-tag v-if="row.completed" size="small" type="info">已完成</el-tag>
            <el-tag v-else-if="isOverdue(row)" size="small" type="danger">已过期</el-tag>
            <el-tag v-else-if="isToday(row)" size="small" type="warning">今天</el-tag>
            <el-tag v-else-if="isUpcoming(row)" size="small" type="warning">{{ getRemainingDays(row) }}天</el-tag>
            <el-tag v-else size="small" type="success">{{ getRemainingDays(row) }}天</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-switch :model-value="row.enabled" size="small"
              @change="(val) => toggleEnabled(row, val)" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <el-button v-if="!row.completed && (isOverdue(row) || isToday(row))" type="success" link size="small"
              @click="completeTask(row)" v-permission="'reminder:complete'">完成</el-button>
            <el-button v-if="!row.completed && isToday(row)" type="warning" link size="small"
              @click="showSnoozeDialog(row)" v-permission="'reminder:snooze'">延迟</el-button>
            <el-button type="primary" link size="small"
              @click="showDialog(row)" v-permission="'reminder:edit'">编辑</el-button>
            <el-popconfirm title="确认删除此提醒?" @confirm="deleteTask(row)">
              <template #reference>
                <el-button type="danger" link size="small" v-permission="'reminder:delete'">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && reminders.length === 0" description="暂无提醒任务" />

      <div class="pagination-wrapper" v-if="total > 0">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :page-sizes="[20, 50, 100]"
          :total="total"
          layout="total, sizes, prev, pager, next"
          @size-change="loadReminders"
          @current-change="loadReminders"
        />
      </div>
    </el-card>

    <!-- Create/Edit Dialog -->
    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑提醒' : '新建提醒'" width="560px" destroy-on-close>
      <el-form :model="form" label-width="110px" :rules="rules" ref="formRef">
        <el-form-item label="标题" prop="title">
          <el-input v-model="form.title" placeholder="例如：阿里云ECS续费" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="2" placeholder="详细说明" />
        </el-form-item>
        <el-form-item label="分类" prop="category">
          <el-select v-model="form.category" style="width: 100%">
            <el-option label="服务器续费" value="SERVER_RENEWAL" />
            <el-option label="SSL证书续费" value="SSL_RENEWAL" />
            <el-option label="机房费用" value="UTILITY" />
            <el-option label="定期巡检" value="ON_SITE_INSPECTION" />
            <el-option label="事务提醒" value="TASK_REMINDER" />
            <el-option label="其他" value="OTHER" />
          </el-select>
        </el-form-item>
        <el-form-item label="首次提醒时间" prop="dueDate">
          <el-date-picker v-model="dueDateModel" type="datetime" placeholder="选择首次提醒时间"
            value-format="YYYY-MM-DD HH:mm:ss" style="width: 100%" />
        </el-form-item>
        <el-form-item label="重复类型" prop="recurrenceType">
          <el-select v-model="form.recurrenceType" style="width: 100%">
            <el-option label="不重复" value="once" />
            <el-option label="每天" value="daily" />
            <el-option label="每周" value="weekly" />
            <el-option label="每月" value="monthly" />
            <el-option label="每年" value="yearly" />
          </el-select>
        </el-form-item>

        <!-- Weekly config -->
        <el-form-item v-if="form.recurrenceType === 'weekly'" label="每周几">
          <el-checkbox-group v-model="weeklyDays">
            <el-checkbox :label="1">周一</el-checkbox>
            <el-checkbox :label="2">周二</el-checkbox>
            <el-checkbox :label="3">周三</el-checkbox>
            <el-checkbox :label="4">周四</el-checkbox>
            <el-checkbox :label="5">周五</el-checkbox>
            <el-checkbox :label="6">周六</el-checkbox>
            <el-checkbox :label="7">周日</el-checkbox>
          </el-checkbox-group>
        </el-form-item>

        <!-- Monthly config -->
        <el-form-item v-if="form.recurrenceType === 'monthly'" label="每月几号">
          <el-input-number v-model="monthlyDay" :min="1" :max="28" />
        </el-form-item>

        <!-- Yearly config -->
        <el-form-item v-if="form.recurrenceType === 'yearly'" label="日期">
          <el-date-picker v-model="yearlyDate" type="date" placeholder="选择日期"
            value-format="MM-DD" style="width: 100%" />
        </el-form-item>

        <!-- Preview button -->
        <el-form-item v-if="form.recurrenceType !== 'once'">
          <el-button type="info" link @click="previewReminders" :loading="previewLoading">
            <el-icon><View /></el-icon> 预览提醒时间
          </el-button>
        </el-form-item>

        <!-- Preview results -->
        <el-form-item v-if="previewResults.length > 0">
          <div class="preview-results">
            <div class="preview-title">接下来的提醒时间线</div>
            <el-timeline>
              <el-timeline-item
                v-for="(time, idx) in previewResults"
                :key="idx"
                :timestamp="time"
                :type="idx === 0 ? 'primary' : ''"
                placement="top"
              >
                {{ idx === 0 ? '首次提醒' : `第${idx + 1} 次提醒` }}
              </el-timeline-item>
            </el-timeline>
          </div>
        </el-form-item>

        <el-divider content-position="left">提前提醒</el-divider>

        <el-form-item label="启用">
          <el-switch v-model="form.advanceEnabled" />
        </el-form-item>
        <el-form-item v-if="form.advanceEnabled" label="提前天数">
          <el-input-number v-model="form.advanceDays" :min="0" :max="365" />
        </el-form-item>
        <el-form-item v-if="form.advanceEnabled" label="提前分钟">
          <el-input-number v-model="form.advanceMinutes" :min="0" :max="1440" :step="30" />
        </el-form-item>

        <el-divider content-position="left">通知渠道</el-divider>

        <el-form-item label="渠道">
          <el-select v-model="selectedChannelIds" multiple placeholder="选择通知渠道" style="width: 100%">
            <el-option v-for="ch in channels" :key="ch.id" :label="ch.name" :value="String(ch.id)" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitForm" :loading="submitting">保存</el-button>
      </template>
    </el-dialog>

    <!-- Snooze Dialog -->
    <el-dialog v-model="snoozeDialogVisible" title="延迟提醒" width="400px" destroy-on-close>
      <p style="margin-bottom: 16px;">选择延迟时长：</p>
      <div class="snooze-options">
        <el-button @click="snooze(5)">5分钟</el-button>
        <el-button @click="snooze(15)">15分钟</el-button>
        <el-button @click="snooze(30)">30分钟</el-button>
        <el-button @click="snooze(60)">1小时</el-button>
        <el-button @click="snooze(1440)">明天</el-button>
      </div>
      <el-divider>自定义</el-divider>
      <el-form :inline="true">
        <el-form-item label="分钟">
          <el-input-number v-model="customSnoozeMinutes" :min="1" :max="43200" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="snooze(customSnoozeMinutes)">确定</el-button>
        </el-form-item>
      </el-form>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { reminderApi, alertChannelApi } from '@/api'

const loading = ref(false)
const submitting = ref(false)
const reminders = ref([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(20)

const filters = reactive({
  category: '',
  status: ''
})

// Dialog state
const dialogVisible = ref(false)
const editingId = ref(null)
const formRef = ref(null)
const form = reactive({
  title: '',
  description: '',
  category: 'OTHER',
  dueDate: '',
  recurrenceType: 'once',
  recurrenceConfig: '',
  advanceEnabled: true,
  advanceMinutes: 60,
  advanceDays: 0,
  alertChannelIds: '',
  enabled: true
})

const dueDateModel = ref('')
const weeklyDays = ref([])
const monthlyDay = ref(1)
const yearlyDate = ref('')
const selectedChannelIds = ref([])

// Preview state
const previewLoading = ref(false)
const previewResults = ref([])

const rules = {
  title: [{ required: true, message: '请输入标题', trigger: 'blur' }],
  category: [{ required: true, message: '请选择分类', trigger: 'change' }],
  dueDate: [{ required: true, message: '请选择首次提醒时间', trigger: 'change' }],
  recurrenceType: [{ required: true, message: '请选择重复类型', trigger: 'change' }]
}

// Snooze state
const snoozeDialogVisible = ref(false)
const snoozingTask = ref(null)
const customSnoozeMinutes = ref(30)

// Channels
const channels = ref([])

onMounted(() => {
  loadReminders()
  loadChannels()
})

const handleFilterChange = () => {
  currentPage.value = 1
  loadReminders()
}

const loadReminders = async () => {
  loading.value = true
  try {
    const params = {
      page: currentPage.value,
      size: pageSize.value,
      category: filters.category || undefined,
      status: filters.status || undefined
    }
    const res = await reminderApi.list(params)
    reminders.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch (error) {
    ElMessage.error('加载提醒列表失败')
  } finally {
    loading.value = false
  }
}

const loadChannels = async () => {
  try {
    const res = await alertChannelApi.list()
    channels.value = (res.data || []).filter(ch => ch.enabled)
  } catch (e) {
    // ignore
  }
}

const showDialog = (task = null) => {
  editingId.value = task ? task.id : null
  previewResults.value = []
  if (task) {
    form.title = task.title
    form.description = task.description || ''
    form.category = task.category
    form.dueDate = task.dueDate
    form.recurrenceType = task.recurrenceType
    form.advanceEnabled = task.advanceEnabled !== false
    form.advanceMinutes = task.advanceMinutes || 60
    form.advanceDays = task.advanceDays || 0
    form.alertChannelIds = task.alertChannelIds || ''
    form.enabled = task.enabled !== false
    dueDateModel.value = task.dueDate
    selectedChannelIds.value = task.alertChannelIds ? task.alertChannelIds.split(',').filter(Boolean) : []
    parseRecurrenceConfig(task.recurrenceConfig)
  } else {
    form.title = ''
    form.description = ''
    form.category = 'OTHER'
    form.dueDate = ''
    form.recurrenceType = 'once'
    form.advanceEnabled = true
    form.advanceMinutes = 60
    form.advanceDays = 0
    form.alertChannelIds = ''
    form.enabled = true
    dueDateModel.value = ''
    weeklyDays.value = []
    monthlyDay.value = 1
    yearlyDate.value = ''
    selectedChannelIds.value = []
  }
  dialogVisible.value = true
}

const parseRecurrenceConfig = (configJson) => {
  if (!configJson) {
    weeklyDays.value = []
    monthlyDay.value = 1
    yearlyDate.value = ''
    return
  }
  try {
    const config = JSON.parse(configJson)
    if (form.recurrenceType === 'weekly' && config.daysOfWeek) {
      weeklyDays.value = config.daysOfWeek
    } else if (form.recurrenceType === 'monthly' && config.dayOfMonth) {
      monthlyDay.value = config.dayOfMonth
    } else if (form.recurrenceType === 'yearly') {
      if (config.month && config.dayOfMonth) {
        yearlyDate.value = String(config.month).padStart(2, '0') + '-' + String(config.dayOfMonth).padStart(2, '0')
      }
    }
  } catch (e) {
    // ignore
  }
}

const buildRecurrenceConfig = () => {
  if (form.recurrenceType === 'weekly' && weeklyDays.value.length > 0) {
    return JSON.stringify({ daysOfWeek: weeklyDays.value })
  } else if (form.recurrenceType === 'monthly') {
    return JSON.stringify({ dayOfMonth: monthlyDay.value })
  } else if (form.recurrenceType === 'yearly' && yearlyDate.value) {
    const [month, day] = yearlyDate.value.split('-')
    return JSON.stringify({ month: parseInt(month), dayOfMonth: parseInt(day) })
  }
  return null
}

const previewReminders = async () => {
  if (!dueDateModel.value) {
    ElMessage.warning('请先选择首次提醒时间')
    return
  }
  previewLoading.value = true
  try {
    const res = await reminderApi.preview({
      dueDate: dueDateModel.value,
      recurrenceType: form.recurrenceType,
      recurrenceConfig: buildRecurrenceConfig(),
      count: 5
    })
    previewResults.value = res.data || []
  } catch (error) {
    ElMessage.error('预览失败')
  } finally {
    previewLoading.value = false
  }
}

const submitForm = async () => {
  // Sync dueDateModel to form before validation
  form.dueDate = dueDateModel.value
  form.recurrenceConfig = buildRecurrenceConfig()
  form.alertChannelIds = selectedChannelIds.value.join(',')

  try {
    await formRef.value.validate()
  } catch {
    return
  }

  submitting.value = true
  try {
    if (editingId.value) {
      await reminderApi.update(editingId.value, form)
      ElMessage.success('更新成功')
    } else {
      await reminderApi.create(form)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    loadReminders()
  } catch (error) {
    ElMessage.error(error.message || '操作失败')
  } finally {
    submitting.value = false
  }
}

const deleteTask = async (task) => {
  try {
    await reminderApi.delete(task.id)
    ElMessage.success('删除成功')
    loadReminders()
  } catch (error) {
    ElMessage.error('删除失败')
  }
}

const completeTask = async (task) => {
  try {
    await reminderApi.complete(task.id)
    ElMessage.success('已标记完成')
    loadReminders()
  } catch (error) {
    ElMessage.error('操作失败')
  }
}

const toggleEnabled = async (task, enabled) => {
  try {
    if (enabled) {
      await reminderApi.enable(task.id)
    } else {
      await reminderApi.disable(task.id)
    }
    task.enabled = enabled
  } catch (error) {
    ElMessage.error('操作失败')
  }
}

const showSnoozeDialog = (task) => {
  snoozingTask.value = task
  customSnoozeMinutes.value = 30
  snoozeDialogVisible.value = true
}

const snooze = async (minutes) => {
  if (!snoozingTask.value) return
  try {
    await reminderApi.snooze(snoozingTask.value.id, minutes)
    ElMessage.success('已延迟')
    snoozeDialogVisible.value = false
    loadReminders()
  } catch (error) {
    ElMessage.error('操作失败')
  }
}

const getCategoryLabel = (category) => {
  const map = {
    SERVER_RENEWAL: '服务器续费',
    SSL_RENEWAL: 'SSL证书续费',
    UTILITY: '机房费用',
    ON_SITE_INSPECTION: '定期巡检',
    TASK_REMINDER: '事务提醒',
    OTHER: '其他'
  }
  return map[category] || category
}

const getCategoryTagType = (category) => {
  const map = {
    SERVER_RENEWAL: 'danger',
    SSL_RENEWAL: 'warning',
    UTILITY: '',
    ON_SITE_INSPECTION: 'success',
    TASK_REMINDER: 'primary',
    OTHER: 'info'
  }
  return map[category] || 'info'
}

const getRecurrenceLabel = (type) => {
  const map = { once: '不重复', daily: '每天', weekly: '每周', monthly: '每月', yearly: '每年' }
  return map[type] || type
}

const isOverdue = (row) => {
  if (!row.nextDueAt) return false
  return new Date(row.nextDueAt) < new Date()
}

const isToday = (row) => {
  if (!row.nextDueAt) return false
  const due = new Date(row.nextDueAt)
  const now = new Date()
  return due.toDateString() === now.toDateString()
}

const isUpcoming = (row) => {
  if (!row.nextDueAt) return false
  const due = new Date(row.nextDueAt)
  const now = new Date()
  const diff = due - now
  return diff > 0 && diff < 7 * 24 * 60 * 60 * 1000
}

const getRemainingDays = (row) => {
  if (!row.nextDueAt) return '-'
  const due = new Date(row.nextDueAt)
  const now = new Date()
  const diff = Math.ceil((due - now) / (1000 * 60 * 60 * 24))
  return diff >= 0 ? diff : 0
}
</script>

<style lang="scss" scoped>
.reminder-list {
  .page-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 20px;
    h2 { margin: 0; }
    .header-actions { display: flex; gap: 8px; }
  }
  .filter-card {
    margin-bottom: 16px;
    .el-form-item { margin-bottom: 0; }
  }
  .pagination-wrapper {
    display: flex;
    justify-content: flex-end;
    margin-top: 16px;
  }
  .snooze-options {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    margin-bottom: 16px;
  }
  .preview-results {
    background: #f5f7fa;
    border-radius: 8px;
    padding: 12px 16px;
    width: 100%;
    .preview-title {
      font-size: 13px;
      color: #606266;
      margin-bottom: 8px;
      font-weight: 500;
    }
  }
}
</style>
