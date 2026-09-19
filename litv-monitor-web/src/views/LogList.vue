<template>
  <div class="log-list">
    <div class="page-header">
      <h2>执行日志</h2>
      <el-button type="danger" @click="cleanupLogs" v-permission="'log:cleanup'">
        <el-icon><Delete /></el-icon>
        清理30天前日志
      </el-button>
    </div>

    <el-alert type="info" :closable="false" show-icon style="margin-bottom: 16px">
      <template #title>
        <div>系统每日凌晨自动清理超过保留期（默认 30 天）的执行日志与告警日志，无需手动干预。手动清理按钮用于立即释放空间。</div>
        <div style="margin-top: 4px">为控制数据库体积，单条日志的请求/响应体超过阈值（默认 256KB）时会被截断保存，详情中可能显示 <code>...[truncated ...]</code>。</div>
      </template>
    </el-alert>

    <!-- Filters -->
    <el-card class="filter-card">
      <el-form :inline="true" :model="filters">
        <el-form-item label="监控ID">
          <el-input-number v-model="filters.monitorId" :min="1" placeholder="监控ID" controls-position="right" />
        </el-form-item>
          <el-form-item label="任务ID">
            <el-input-number v-model="filters.groupId" :min="1" placeholder="任务ID" controls-position="right" />
        </el-form-item>
        <el-form-item label="域名">
          <el-input v-model="filters.domain" placeholder="域名关键词" clearable />
        </el-form-item>
        <el-form-item label="IP地址">
          <el-input v-model="filters.ipAddress" placeholder="IP关键词" clearable />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filters.status" placeholder="全部" clearable style="width: 120px">
            <el-option v-for="(v, k) in EXECUTION_STATUS" :key="k" :label="v.label" :value="k" />
          </el-select>
        </el-form-item>
        <el-form-item label="执行时间">
          <el-date-picker v-model="filters.dateRange" type="datetimerange" range-separator="至" start-placeholder="开始时间" end-placeholder="结束时间" value-format="YYYY-MM-DD HH:mm:ss" style="width: 340px" />
        </el-form-item>
        <el-form-item label="Schema">
          <el-select v-model="filters.schemaCheckStatus" placeholder="全部" clearable multiple collapse-tags collapse-tags-tooltip="全部" style="width: 220px">
            <el-option label="已检查" value="checked" />
            <el-option label="检出变更" value="hasChange" />
            <el-option label="无变更" value="noChange" />
            <el-option label="BREAKING" value="BREAKING" />
            <el-option label="MODIFIED" value="MODIFIED" />
            <el-option label="ADDED" value="ADDED" />
            <el-option label="REMOVED" value="REMOVED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="resetFilters">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- Table -->
    <el-card>
      <el-table :data="logs" stripe v-loading="loading">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="monitorId" label="监控ID" width="80">
          <template #default="{ row }">
            <router-link v-if="row.monitorId" :to="{ path: '/monitor', query: { filterId: row.monitorId } }" class="id-link">{{ row.monitorId }}</router-link>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="monitorName" label="监控名称" min-width="150" show-overflow-tooltip />
        <el-table-column prop="groupId" label="任务ID" width="80">
          <template #default="{ row }">
            <router-link v-if="row.groupId" :to="{ path: '/group', query: { filterId: row.groupId } }" class="id-link">{{ row.groupId }}</router-link>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="url" label="URL" min-width="220" show-overflow-tooltip />
        <el-table-column prop="domain" label="域名" min-width="140" show-overflow-tooltip />
        <el-table-column prop="ipAddress" label="IP地址" width="130" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)" size="small">
              {{ row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="statusCode" label="状态码" width="80" />
        <el-table-column prop="responseTime" label="响应时间(ms)" width="120" />
        <el-table-column prop="schemaCheckStatus" label="Schema" width="120">
          <template #default="{ row }">
            <el-tag v-if="row.schemaCheckStatus" :type="getSchemaTagType(row.schemaCheckStatus)" size="small">
              {{ row.schemaCheckStatus }}
            </el-tag>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="executedAt" label="执行时间" width="180">
          <template #default="{ row }">{{ formatTime(row.executedAt) }}</template>
        </el-table-column>
        <el-table-column prop="errorMessage" label="错误信息" min-width="200" show-overflow-tooltip />
        <el-table-column label="操作" width="100" fixed="right">
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
    <el-dialog v-model="detailDialogVisible" title="执行详情" width="800px">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="ID">{{ detail.id }}</el-descriptions-item>
        <el-descriptions-item label="监控ID">{{ detail.monitorId }}</el-descriptions-item>
        <el-descriptions-item label="监控名称">{{ detail.monitorName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="任务ID">{{ detail.groupId }}</el-descriptions-item>
        <el-descriptions-item label="执行ID">{{ detail.executionId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="域名">{{ detail.domain || '-' }}</el-descriptions-item>
        <el-descriptions-item label="URL" :span="2">
          <span style="word-break: break-all; line-height: 1.4">{{ detail.url || '-' }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="getStatusType(detail.status)">{{ detail.status }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="状态码">{{ detail.statusCode }}</el-descriptions-item>
        <el-descriptions-item label="响应时间">{{ detail.responseTime }}ms</el-descriptions-item>
        <el-descriptions-item label="执行时间">{{ formatTime(detail.executedAt) }}</el-descriptions-item>
        <el-descriptions-item label="IP地址">{{ detail.ipAddress || '-' }}</el-descriptions-item>
        <el-descriptions-item label="Schema检查">
          <el-tag v-if="detail.schemaCheckStatus" :type="getSchemaTagType(detail.schemaCheckStatus)" size="small">
            {{ detail.schemaCheckStatus }}
          </el-tag>
          <span v-else>-</span>
        </el-descriptions-item>
      </el-descriptions>

      <el-divider content-position="left">请求头</el-divider>
      <pre class="code-block">{{ formatJson(detail.requestHeaders) }}</pre>

      <el-divider content-position="left">请求体</el-divider>
      <pre class="code-block">{{ detail.requestBody || '(无)' }}</pre>

      <el-divider content-position="left">响应体</el-divider>
      <pre class="code-block">{{ detail.responseBody || '(无)' }}</pre>

      <el-divider content-position="left">错误信息</el-divider>
      <pre class="code-block error">{{ detail.errorMessage || '(无)' }}</pre>

      <template v-if="detail.variableReferences">
        <el-divider content-position="left">变量引用 (替换前 → 替换后)</el-divider>
        <div class="variable-section">
          <div v-for="(value, key) in parseJson(detail.variableReferences)" :key="key" class="variable-item">
            <code class="var-key" v-text="'{{' + key + '}}'"></code>
            <span class="var-arrow">=</span>
            <code class="var-value">{{ value }}</code>
          </div>
        </div>
      </template>

      <template v-if="detail.variableSettings">
        <el-divider content-position="left">变量设置 (提取 → 写入)</el-divider>
        <div class="variable-section">
          <div v-for="(desc, key) in parseJson(detail.variableSettings)" :key="key" class="variable-item">
            <code class="var-key">{{ key }}</code>
            <span class="var-arrow">=</span>
            <code class="var-value">{{ desc }}</code>
          </div>
        </div>
      </template>

      <template v-if="detail.executionId && linkedAlertLogs.length > 0">
        <el-divider content-position="left">关联告警记录</el-divider>
        <el-table :data="linkedAlertLogs" stripe size="small" style="width: 100%">
          <el-table-column prop="triggerType" label="触发类型" width="110">
            <template #default="{ row }">
              <el-tag size="small">{{ getTriggerTypeLabel(row.triggerType) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="alertConfigName" label="告警渠道" min-width="120" />
          <el-table-column prop="status" label="发送状态" width="100">
            <template #default="{ row }">
              <el-tag :type="row.status === 'SENT' ? 'success' : row.status === 'FAILED' ? 'danger' : 'info'" size="small">
                {{ row.status }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="sentAt" label="告警时间" width="170">
            <template #default="{ row }">{{ formatTime(row.sentAt) }}</template>
          </el-table-column>
        </el-table>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { logApi, alertApi } from '@/api'
import { formatTime } from '@/utils/format'
import { TRIGGER_TYPES, EXECUTION_STATUS } from '@/constants/enums'

const route = useRoute()
const loading = ref(false)
const detailDialogVisible = ref(false)
const logs = ref([])
const detail = ref({})
const linkedAlertLogs = ref([])

const filters = reactive({ monitorId: null, groupId: null, domain: '', ipAddress: '', status: '', schemaCheckStatus: [], dateRange: [] })
const pagination = reactive({ page: 1, size: 10, total: 0 })

const applyQuery = () => {
  const { hours, schemaFilter, status, monitorId, groupId } = route.query
  if (monitorId) {
    filters.monitorId = Number(monitorId)
  }
  if (groupId) {
    filters.groupId = Number(groupId)
  }
  if (hours) {
    const end = new Date()
    const start = new Date(end.getTime() - Number(hours) * 3600 * 1000)
    filters.dateRange = [formatTime(start), formatTime(end)]
  }
  if (schemaFilter) {
    filters.schemaCheckStatus = [schemaFilter]
  }
  if (status) {
    filters.status = status
  }
  pagination.page = 1
  loadLogs()
}

onMounted(applyQuery)
// 组件已挂载时再次通过 query 跳转（如仪表盘深链）也要重新加载
watch(() => route.query, () => applyQuery())

const loadLogs = async () => {
  loading.value = true
  try {
    const res = await logApi.list({
      page: pagination.page,
      size: pagination.size,
      monitorId: filters.monitorId || undefined,
      groupId: filters.groupId || undefined,
      domain: filters.domain || undefined,
      ipAddress: filters.ipAddress || undefined,
      status: filters.status || undefined,
      schemaCheckStatus: filters.schemaCheckStatus.length > 0 ? filters.schemaCheckStatus.join(',') : undefined,
      startTime: filters.dateRange && filters.dateRange[0] ? (typeof filters.dateRange[0] === 'string' ? filters.dateRange[0] : formatTime(filters.dateRange[0])) : undefined,
      endTime: filters.dateRange && filters.dateRange[1] ? (typeof filters.dateRange[1] === 'string' ? filters.dateRange[1] : formatTime(filters.dateRange[1])) : undefined
    })
    logs.value = res.data?.records || []
    pagination.total = res.data?.total || 0
  } catch (error) {
    ElMessage.error('加载失败')
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  pagination.page = 1
  loadLogs()
}

const resetFilters = () => {
  filters.monitorId = null
  filters.groupId = null
  filters.domain = ''
  filters.ipAddress = ''
  filters.status = ''
  filters.schemaCheckStatus = []
  filters.dateRange = []
  pagination.page = 1
  loadLogs()
}

const viewDetail = async (row) => {
  detail.value = row
  linkedAlertLogs.value = []
  if (row.executionId) {
    try {
      const res = await alertApi.listLogs({ executionId: row.executionId, size: 50 })
      linkedAlertLogs.value = res.data?.records || []
    } catch (e) {
      // ignore
    }
  }
  detailDialogVisible.value = true
}

const cleanupLogs = async () => {
  try {
    await ElMessageBox.confirm('确认清理30天前的日志？此操作不可恢复', '确认', { type: 'warning' })
    const res = await logApi.cleanup(30)
    ElMessage.success(`已清理${res.data}条日志`)
    loadLogs()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('清理失败')
    }
  }
}

const getStatusType = (status) => EXECUTION_STATUS[status]?.tagType || 'info'
const getTriggerTypeLabel = (type) => TRIGGER_TYPES[type]?.label || type || '-'

const getSchemaTagType = (status) => {
  if (!status) return 'info'
  if (status === '无变更') return 'success'
  if (status === '未启用' || status === '未配置Schema' || status === '非JSON响应' || status === '无响应体') return 'info'
  if (status.includes('BREAKING') || status.includes('REMOVED')) return 'danger'
  if (status.includes('ADDED') || status.includes('MODIFIED')) return 'warning'
  return 'info'
}

const formatJson = (str) => {
  if (!str) return '(无)'
  try {
    return JSON.stringify(JSON.parse(str), null, 2)
  } catch {
    return str
  }
}

const parseJson = (str) => {
  if (!str) return {}
  try {
    return JSON.parse(str)
  } catch {
    return {}
  }
}
</script>

<style lang="scss" scoped>
.log-list {
  .filter-card {
    margin-bottom: 20px;
  }

  .el-pagination {
    margin-top: 20px;
    justify-content: flex-end;
  }

  .code-block {
    background: #f5f7fa;
    padding: 12px;
    border-radius: 4px;
    font-size: 12px;
    overflow-x: auto;
    max-height: 200px;
    overflow-y: auto;

    &.error {
      color: #f56c6c;
    }
  }

  .variable-section {
    background: #f5f7fa;
    border-radius: 4px;
    padding: 12px;
    max-height: 200px;
    overflow-y: auto;
  }

  .variable-item {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 4px 0;
    font-size: 12px;
    border-bottom: 1px solid #ebeef5;

    &:last-child {
      border-bottom: none;
    }
  }

  .var-key {
    color: #e6a23c;
    background: #fdf6ec;
    padding: 2px 6px;
    border-radius: 3px;
    font-weight: 500;
  }

  .var-arrow {
    color: #909399;
    font-weight: bold;
  }

  .var-value {
    color: #67c23a;
    background: #f0f9eb;
    padding: 2px 6px;
    border-radius: 3px;
    word-break: break-all;
  }

  .text-muted {
    color: #c0c4cc;
  }

  .id-link {
    color: #409eff;
    text-decoration: none;
    &:hover { text-decoration: underline; }
  }
}
</style>
