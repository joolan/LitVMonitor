<template>
  <div class="alert-log-list">
    <div class="page-header">
      <h2>告警记录</h2>
    </div>

    <!-- Filters -->
    <el-card class="filter-card">
      <el-form :inline="true" :model="filters">
        <el-form-item label="触发类型">
          <el-select v-model="filters.triggerType" clearable placeholder="全部" style="width: 140px">
            <el-option label="执行失败" value="FAIL" />
            <el-option label="响应超时" value="RESPONSE_TIME" />
            <el-option label="任务失败" value="GROUP_FAIL" />
            <el-option label="SSL证书" value="SSL_CERT" />
            <el-option label="Schema变更" value="SCHEMA_CHANGE" />
            <el-option label="测试" value="TEST" />
          </el-select>
        </el-form-item>
        <el-form-item label="告警渠道">
          <el-select v-model="filters.alertConfigId" clearable placeholder="全部" style="width: 160px">
            <el-option v-for="c in alertConfigs" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="发送状态">
          <el-select v-model="filters.status" clearable placeholder="全部" style="width: 140px">
            <el-option label="已发送" value="SENT" />
            <el-option label="发送失败" value="FAILED" />
            <el-option label="冷却抑制" value="SUPPRESSED" />
            <el-option label="已静默" value="SILENCED" />
            <el-option label="无告警渠道" value="NO_CHANNEL" />
            <el-option label="渠道不可用" value="CONFIG_UNAVAILABLE" />
          </el-select>
        </el-form-item>
        <el-form-item label="告警类型">
          <el-select v-model="filters.alertCategory" clearable placeholder="全部" style="width: 120px">
            <el-option label="普通告警" value="ALERT" />
            <el-option label="恢复通知" value="RECOVERY" />
          </el-select>
        </el-form-item>
        <el-form-item label="告警时间">
          <el-date-picker
            v-model="filters.timeRange"
            type="datetimerange"
            range-separator="至"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            value-format="YYYY-MM-DD HH:mm:ss"
            style="width: 380px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadLogs">搜索</el-button>
          <el-button @click="resetFilters">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- Table -->
    <el-card>
      <el-table :data="logs" stripe v-loading="loading">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="monitorName" label="监控名称" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">
            <router-link v-if="row.monitorId" :to="{ path: '/monitor', query: { filterId: row.monitorId } }" class="id-link">{{ row.monitorName || row.monitorId }}</router-link>
            <span v-else>{{ row.monitorName || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="groupName" label="任务名称" min-width="120" show-overflow-tooltip>
          <template #default="{ row }">
            <router-link v-if="row.groupId" :to="{ path: '/group', query: { filterId: row.groupId } }" class="id-link">{{ row.groupName || row.groupId }}</router-link>
            <span v-else>{{ row.groupName || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="triggerType" label="触发类型" width="110">
          <template #default="{ row }">
            <el-tag :type="getTriggerTypeTag(row.triggerType)" size="small">
              {{ getTriggerTypeLabel(row.triggerType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="alertConfigName" label="告警渠道" min-width="120" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.alertConfigName || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="告警类型" width="100">
          <template #default="{ row }">
            <el-tag v-if="isRecovery(row)" type="success" size="small">恢复通知</el-tag>
            <el-tag v-else type="warning" size="small">普通告警</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="alertType" label="渠道类型" width="90">
          <template #default="{ row }">
            <el-tag :type="row.alertType === 'EMAIL' ? 'primary' : 'success'" size="small">
              {{ row.alertType === 'EMAIL' ? '邮件' : 'Webhook' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="statusCode" label="状态码" width="80" />
        <el-table-column prop="responseTime" label="响应时间" width="100">
          <template #default="{ row }">
            {{ row.responseTime != null ? row.responseTime + 'ms' : '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="status" label="发送状态" width="110">
          <template #default="{ row }">
            <el-tag :type="getStatusTag(row.status)" size="small">
              {{ getStatusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="executionId" label="执行ID" width="120" show-overflow-tooltip>
          <template #default="{ row }">
            <el-text v-if="row.executionId" size="small" truncated>{{ row.executionId }}</el-text>
            <el-text v-else type="info" size="small">-</el-text>
          </template>
        </el-table-column>
        <el-table-column prop="sentAt" label="告警时间" width="180">
          <template #default="{ row }">{{ formatTime(row.sentAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="80" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="viewDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="pagination.page"
        v-model:page-size="pagination.size"
        :total="pagination.total"
        layout="total, sizes, prev, pager, next"
        @change="loadLogs"
      />
    </el-card>

    <!-- Detail Dialog -->
    <el-dialog v-model="detailDialogVisible" title="告警详情" width="700px">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="ID">{{ detail.id }}</el-descriptions-item>
        <el-descriptions-item label="触发类型">
          <el-tag :type="getTriggerTypeTag(detail.triggerType)" size="small">
            {{ getTriggerTypeLabel(detail.triggerType) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="监控名称">{{ detail.monitorName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="监控ID">{{ detail.monitorId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="任务名称">{{ detail.groupName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="任务ID">{{ detail.groupId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="告警渠道">{{ detail.alertConfigName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="执行ID">
          <el-text v-if="detail.executionId" size="small">{{ detail.executionId }}</el-text>
          <el-text v-else type="info" size="small">-</el-text>
        </el-descriptions-item>
        <el-descriptions-item label="渠道类型">
          <el-tag :type="detail.alertType === 'EMAIL' ? 'primary' : 'success'" size="small">
            {{ detail.alertType === 'EMAIL' ? '邮件' : 'Webhook' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="HTTP状态码">{{ detail.statusCode || '-' }}</el-descriptions-item>
        <el-descriptions-item label="响应时间">{{ detail.responseTime != null ? detail.responseTime + 'ms' : '-' }}</el-descriptions-item>
        <el-descriptions-item label="发送状态">
          <el-tag :type="getStatusTag(detail.status)" size="small">
            {{ getStatusLabel(detail.status) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="发送时间">{{ formatTime(detail.sentAt) }}</el-descriptions-item>
      </el-descriptions>

      <el-divider content-position="left">错误信息</el-divider>
      <pre class="code-block">{{ detail.errorMessage || '(无)' }}</pre>

      <el-divider content-position="left">告警内容</el-divider>
      <pre class="code-block">{{ detail.alertContent || '(无)' }}</pre>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { alertApi } from '@/api'
import { formatTime } from '@/utils/format'
import { TRIGGER_TYPES, ALERT_STATUS, CHANNEL_TYPES } from '@/constants/enums'

const route = useRoute()

const loading = ref(false)
const logs = ref([])
const alertConfigs = ref([])
const detailDialogVisible = ref(false)
const detail = ref({})

const filters = reactive({
  triggerType: '',
  alertConfigId: null,
  status: '',
  alertCategory: '',
  timeRange: null
})

const pagination = reactive({ page: 1, size: 10, total: 0 })

onMounted(() => {
  const q = route?.query || {}
  if (q.triggerType) {
    filters.triggerType = q.triggerType
  }
  if (q.startTime && q.endTime) {
    filters.timeRange = [q.startTime, q.endTime]
  }
  loadAlertConfigs()
  loadLogs()
})

const loadAlertConfigs = async () => {
  try {
    const res = await alertApi.listConfigs()
    alertConfigs.value = res.data || []
  } catch (error) {
    console.error('Failed to load alert configs:', error)
  }
}

const loadLogs = async () => {
  loading.value = true
  try {
    const params = {
      page: pagination.page,
      size: pagination.size
    }
    if (filters.triggerType) params.triggerType = filters.triggerType
    if (filters.alertConfigId) params.alertConfigId = filters.alertConfigId
    if (filters.status) params.status = filters.status
    if (filters.alertCategory) params.alertCategory = filters.alertCategory
    if (filters.timeRange && filters.timeRange.length === 2) {
      params.startTime = filters.timeRange[0]
      params.endTime = filters.timeRange[1]
    }

    const res = await alertApi.listLogs(params)
    logs.value = res.data?.records || []
    pagination.total = res.data?.total || 0
  } catch (error) {
    ElMessage.error('加载失败')
  } finally {
    loading.value = false
  }
}

const resetFilters = () => {
  filters.triggerType = ''
  filters.alertConfigId = null
  filters.status = ''
  filters.alertCategory = ''
  filters.timeRange = null
  pagination.page = 1
  loadLogs()
}

const viewDetail = async (row) => {
  try {
    const res = await alertApi.getLog(row.id)
    detail.value = res.data || row
    detailDialogVisible.value = true
  } catch (error) {
    detail.value = row
    detailDialogVisible.value = true
  }
}

const getTriggerTypeTag = (type) => TRIGGER_TYPES[type]?.tagType || 'info'
const getTriggerTypeLabel = (type) => TRIGGER_TYPES[type]?.label || type || '-'
const isRecovery = (row) => row.alertContent && row.alertContent.includes('[恢复通知]')
const getStatusTag = (status) => ALERT_STATUS[status]?.tagType || 'info'
const getStatusLabel = (status) => ALERT_STATUS[status]?.label || status || '-'
</script>

<style lang="scss" scoped>
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;

  h2 {
    margin: 0;
  }
}

.filter-card {
  margin-bottom: 20px;
}

.el-pagination {
  margin-top: 20px;
  justify-content: flex-end;
}

.id-link {
  color: #409eff;
  text-decoration: none;
  &:hover { text-decoration: underline; }
}

.code-block {
  background: #f5f7fa;
  padding: 12px;
  border-radius: 4px;
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 13px;
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-all;
  margin: 0;
  max-height: 300px;
  overflow-y: auto;
}
</style>
