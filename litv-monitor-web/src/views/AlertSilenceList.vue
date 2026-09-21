<template>
  <div class="alert-silence-list">
    <div class="page-header">
      <h2>告警静默</h2>
      <el-button type="primary" @click="showDialog()" v-permission="'alert-silence:create'">
        <el-icon><Plus /></el-icon>
        添加静默规则
      </el-button>
    </div>

    <el-card>
      <el-alert type="info" :closable="false" show-icon style="margin-bottom: 16px">
        <template #title>
          告警静默用于在指定时间段内抑制告警通知。支持一次性静默（生效日期内每天的静默时段都生效）和周期性静默（每日/每周/每月），可针对特定监控项、任务或全局生效。静默期间告警仍会记录，仅不发送通知，且不消耗限频配额。优先级：恢复通知 > 告警静默 > 冷却时间 > 告警限频。
        </template>
      </el-alert>

      <el-table :data="list" stripe v-loading="loading">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="name" label="名称" min-width="150" />
        <el-table-column prop="silenceType" label="类型" width="140">
          <template #default="{ row }">
            <el-tag size="small">{{ row.silenceType === 'ONE_TIME' ? '一次性(每天时段)' : '周期性' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="applyTo" label="适用范围" width="100">
          <template #default="{ row }">
            <el-tag size="small" type="info">{{ applyToLabel(row.applyTo) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="静默时间" min-width="280">
          <template #default="{ row }">
            <div>{{ formatTimeRange(row) }}</div>
            <div style="font-size: 12px; color: #909399; margin-top: 2px">{{ formatScheduleSummary(row) }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="enabled" label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'info'" size="small">
              {{ row.enabled ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button v-if="!row.enabled" type="success" link @click="toggleSilence(row.id, true)" v-permission="'alert-silence:enable'">启用</el-button>
            <el-button v-else type="warning" link @click="toggleSilence(row.id, false)" v-permission="'alert-silence:disable'">禁用</el-button>
            <el-button type="primary" link @click="showDialog(row)" v-permission="'alert-silence:edit'">编辑</el-button>
            <el-popconfirm title="确认删除?" @confirm="deleteSilence(row.id)">
              <template #reference>
                <el-button type="danger" link v-permission="'alert-silence:delete'">删除</el-button>
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
        @change="loadList"
      />
    </el-card>

    <!-- Dialog -->
    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑静默规则' : '添加静默规则'" width="700px" top="5vh">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="静默类型">
          <el-radio-group v-model="form.silenceType" @change="onSilenceTypeChange">
            <el-radio value="ONE_TIME">一次性(每天时段)</el-radio>
            <el-radio value="RECURRING">周期性</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="生效日期">
          <el-date-picker v-model="dateRange" type="daterange" value-format="YYYY-MM-DD" start-placeholder="开始日期" end-placeholder="结束日期(可选)" style="width: 100%" />
        </el-form-item>

        <!-- RECURRING: show recurrence type selector -->
        <el-form-item v-if="form.silenceType === 'RECURRING'" label="重复方式">
          <el-radio-group v-model="form.recurrenceType">
            <el-radio value="DAILY">每天</el-radio>
            <el-radio value="WEEKLY">每周</el-radio>
            <el-radio value="MONTHLY">每月</el-radio>
          </el-radio-group>
        </el-form-item>

        <!-- WEEKLY: shared weekday selection -->
        <el-form-item v-if="form.silenceType === 'RECURRING' && form.recurrenceType === 'WEEKLY'" label="重复星期">
          <el-checkbox-group v-model="form.selectedDays">
            <el-checkbox v-for="d in weekDays" :key="d.value" :value="d.value">{{ d.label }}</el-checkbox>
          </el-checkbox-group>
        </el-form-item>

        <!-- MONTHLY: shared date selection -->
        <el-form-item v-if="form.silenceType === 'RECURRING' && form.recurrenceType === 'MONTHLY'" label="重复日期">
          <el-checkbox-group v-model="form.selectedDates">
            <el-checkbox v-for="d in 31" :key="d" :value="d">{{ d }}日</el-checkbox>
          </el-checkbox-group>
        </el-form-item>

        <!-- Time windows -->
        <el-form-item>
          <template #label>
            <span style="display: inline-flex; align-items: center;">
              <span>静默时段</span>
              <el-tooltip placement="right" :width="360">
                <template #content>
                  <div style="line-height: 1.8">
                    <p><b>一次性静默</b>：指定起止日期，期间每天的静默时段都生效。如 9:00~18:00 表示生效日期内每天 9:00-18:00 静默。</p>
                    <p><b>周期性静默</b>：按每日/每周/每月重复，匹配日期 + 时段。如"每周一 10:00~08:00"表示仅在每周一 10:00 到周二 08:00 之间静默。</p>
                    <p style="color: #e6a23c">注意：跨夜时段（开始 > 结束）在周期性规则中仅对匹配日期生效。如"每周一 10:00~08:00"，周二 0:00-8:00 不会静默（因为周二不是周一）。</p>
                  </div>
                </template>
                <el-icon style="margin-left: 2px; cursor: pointer; color: #909399;"><QuestionFilled /></el-icon>
              </el-tooltip>
            </span>
          </template>
          <div v-for="(w, i) in form.timeWindows" :key="i" class="time-window-row" :style="{ marginTop: i > 0 ? '8px' : '0' }">
            <el-time-picker v-model="w.start" format="HH:mm" value-format="HH:mm" placeholder="开始" style="width: 120px" />
            <span style="margin: 0 4px">~</span>
            <el-time-picker v-model="w.end" format="HH:mm" value-format="HH:mm" placeholder="结束" style="width: 120px" />
            <el-button v-if="form.timeWindows.length > 1" type="danger" link @click="removeTimeWindow(i)" style="margin-left: 8px">
              <el-icon><Delete /></el-icon>
            </el-button>
          </div>
          <el-button type="primary" link @click="addTimeWindow" style="margin-top: 8px">
            <el-icon><Plus /></el-icon> 添加时段
          </el-button>
        </el-form-item>

        <el-form-item label="适用范围">
          <el-radio-group v-model="form.applyTo">
            <el-radio value="ALL">全部</el-radio>
            <el-radio value="MONITOR">指定监控项</el-radio>
            <el-radio value="GROUP">指定任务</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="form.applyTo !== 'ALL'" label="选择对象">
          <el-input v-model="form.applyIds" placeholder="ID用逗号分隔，如: 1,2,3" />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="form.enabled" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitForm" :loading="submitting" v-permission="editingId ? 'alert-silence:edit' : 'alert-silence:create'">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { QuestionFilled } from '@element-plus/icons-vue'
import { alertSilenceApi } from '@/api'
import { formatTime } from '@/utils/format'
import { SILENCE_TYPE, RECURRENCE_TYPE, APPLY_TO } from '@/constants/enums'
import dayjs from 'dayjs'
import utc from 'dayjs/plugin/utc'
import timezone from 'dayjs/plugin/timezone'
dayjs.extend(utc)
dayjs.extend(timezone)

const loading = ref(false)
const submitting = ref(false)
const dialogVisible = ref(false)
const editingId = ref(null)
const list = ref([])
const formRef = ref(null)
const pagination = reactive({ page: 1, size: 10, total: 0 })

const dateRange = ref(null)
const weekDays = [
  { value: 1, label: '周一' }, { value: 2, label: '周二' }, { value: 3, label: '周三' },
  { value: 4, label: '周四' }, { value: 5, label: '周五' }, { value: 6, label: '周六' }, { value: 7, label: '周日' }
]

const createTimeWindow = () => ({
  start: '09:00', end: '18:00'
})

const form = reactive({
  name: '',
  description: '',
  silenceType: 'ONE_TIME',
  recurrenceType: 'DAILY',
  timeWindows: [createTimeWindow()],
  selectedDays: [],
  selectedDates: [],
  applyTo: 'ALL',
  applyIds: '',
  enabled: true
})

const rules = {
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }]
}

const onSilenceTypeChange = () => {
  form.timeWindows = [createTimeWindow()]
}

const addTimeWindow = () => {
  form.timeWindows.push(createTimeWindow())
}

const removeTimeWindow = (index) => {
  form.timeWindows.splice(index, 1)
}

const buildScheduleConfig = () => {
  const windows = form.timeWindows.map(w => ({
    start: w.start,
    end: w.end
  }))

  if (form.silenceType === 'ONE_TIME') {
    return JSON.stringify({ timeWindows: windows })
  }

  const config = { recurrenceType: form.recurrenceType, timeWindows: windows }
  if (form.recurrenceType === 'WEEKLY') {
    config.days = form.selectedDays || []
  } else if (form.recurrenceType === 'MONTHLY') {
    config.dates = form.selectedDates || []
  }
  return JSON.stringify(config)
}

const buildSubmitForm = () => {
  return {
    name: form.name,
    description: form.description,
    silenceType: form.silenceType,
    startTime: dateRange.value ? dateRange.value[0] + ' 00:00:00' : null,
    endTime: dateRange.value ? dateRange.value[1] + ' 23:59:59' : null,
    scheduleConfig: buildScheduleConfig(),
    applyTo: form.applyTo,
    applyIds: form.applyIds,
    enabled: form.enabled
  }
}

const parseScheduleConfig = (configJson) => {
  if (!configJson) return
  try {
    const config = JSON.parse(configJson)
    if (config.recurrenceType) form.recurrenceType = config.recurrenceType
    form.timeWindows = (config.timeWindows || []).map(w => ({
      start: w.start || '09:00',
      end: w.end || '18:00'
    }))
    if (form.timeWindows.length === 0) form.timeWindows.push(createTimeWindow())
    form.selectedDays = config.days || []
    form.selectedDates = config.dates || []
  } catch (e) {
    console.error('Failed to parse scheduleConfig', e)
  }
}

onMounted(() => { loadList() })

const loadList = async () => {
  loading.value = true
  try {
    const res = await alertSilenceApi.list({ page: pagination.page, size: pagination.size })
    list.value = res.data?.records || []
    pagination.total = res.data?.total || 0
  } catch (error) {
    ElMessage.error('加载失败')
  } finally {
    loading.value = false
  }
}

const applyToLabel = (val) => APPLY_TO[val]?.label || val

const formatTimeRange = (row) => {
  const formatDate = (v) => {
    if (!v) return null
    return dayjs(v).tz('Asia/Shanghai').format('YYYY-MM-DD')
  }
  const start = formatDate(row.startTime) || '-'
  const end = formatDate(row.endTime) || '无限期'
  return `${start} ~ ${end}`
}

const formatScheduleSummary = (row) => {
  if (!row.scheduleConfig) return ''
  try {
    const config = JSON.parse(row.scheduleConfig)
    const windows = config.timeWindows || []
    if (windows.length === 0) return ''

    const timeStr = windows.map(w => `${w.start}~${w.end}`).join(', ')

    if (row.silenceType === 'ONE_TIME') {
      return `每天 ${timeStr}`
    }

    const typeLabel = RECURRENCE_TYPE[config.recurrenceType]?.label || config.recurrenceType

    if (config.recurrenceType === 'WEEKLY') {
      const dayNames = ['', '周一', '周二', '周三', '周四', '周五', '周六', '周日']
      const days = config.days || []
      const daysStr = days.map(d => dayNames[d]).join('/')
      return `${typeLabel} ${daysStr} ${timeStr}`
    }

    if (config.recurrenceType === 'MONTHLY') {
      const dates = config.dates || []
      const datesStr = dates.join(',')
      return `${typeLabel} 每月${datesStr}日 ${timeStr}`
    }

    return `${typeLabel} ${timeStr}`
  } catch (e) {
    return ''
  }
}

const showDialog = (row) => {
  if (row) {
    editingId.value = row.id
    form.name = row.name
    form.description = row.description || ''
    form.silenceType = row.silenceType || 'ONE_TIME'
    form.applyTo = row.applyTo || 'ALL'
    form.applyIds = row.applyIds || ''
    form.enabled = row.enabled
    form.timeWindows = [createTimeWindow()]
    form.recurrenceType = 'DAILY'
    dateRange.value = row.startTime ? [row.startTime.substring(0, 10), row.endTime ? row.endTime.substring(0, 10) : null] : null
    parseScheduleConfig(row.scheduleConfig)
  } else {
    editingId.value = null
    form.name = ''
    form.description = ''
    form.silenceType = 'ONE_TIME'
    form.recurrenceType = 'DAILY'
    form.timeWindows = [createTimeWindow()]
    form.selectedDays = []
    form.selectedDates = []
    form.applyTo = 'ALL'
    form.applyIds = ''
    form.enabled = true
    dateRange.value = null
  }
  dialogVisible.value = true
}

const submitForm = async () => {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    const data = buildSubmitForm()
    if (editingId.value) {
      await alertSilenceApi.update(editingId.value, data)
      ElMessage.success('更新成功')
    } else {
      await alertSilenceApi.create(data)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    loadList()
  } catch (error) {
    ElMessage.error(error.message || '操作失败')
  } finally {
    submitting.value = false
  }
}

const toggleSilence = async (id, enabled) => {
  try {
    if (enabled) {
      await alertSilenceApi.enable(id)
    } else {
      await alertSilenceApi.disable(id)
    }
    ElMessage.success('操作成功')
    loadList()
  } catch (error) {
    ElMessage.error('操作失败')
  }
}

const deleteSilence = async (id) => {
  try {
    await alertSilenceApi.delete(id)
    ElMessage.success('删除成功')
    loadList()
  } catch (error) {
    ElMessage.error('删除失败')
  }
}
</script>

<style lang="scss" scoped>
.alert-silence-list {
  .el-pagination {
    margin-top: 20px;
    justify-content: flex-end;
  }
  .time-window-row {
    display: flex;
    align-items: center;
    gap: 4px;
    margin-bottom: 8px;
    flex-wrap: wrap;
  }
}
</style>
