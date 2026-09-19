<template>
  <div class="inspection-page">
    <div class="page-header">
      <h2>巡检模式</h2>
      <div class="header-actions">
        <el-button type="primary" @click="showBatchDialog" v-permission="'inspection:run'">
          <el-icon><VideoPlay /></el-icon> 执行巡检
        </el-button>
      </div>
    </div>

    <el-alert type="info" :closable="false" show-icon style="margin-bottom: 16px">
      <template #title>
        巡检模式用于批量执行一组监控项并生成巡检报告。创建巡检配置后，可立即执行或按Cron计划定时执行。执行结果包含每个监控项的状态、响应时间和错误信息，便于快速定位问题。
      </template>
    </el-alert>

    <el-card class="config-card">
      <template #header>
        <div class="card-header">
          <span>巡检配置</span>
          <el-button type="primary" @click="showAddDialog" v-permission="'inspection:create'">
            <el-icon><Plus /></el-icon> 添加配置
          </el-button>
        </div>
      </template>

      <el-table :data="configs" stripe>
        <el-table-column prop="name" label="名称" />
        <el-table-column prop="description" label="描述" show-overflow-tooltip />
        <el-table-column prop="lastRunAt" label="最后执行" width="180">
          <template #default="{ row }">
            {{ row.lastRunAt ? formatTime(row.lastRunAt) : '-' }}
          </template>
        </el-table-column>
        <el-table-column label="定时" width="140">
          <template #default="{ row }">
            {{ row.scheduleCron ? getCronLabel(row.scheduleCron) : '仅手动' }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="280">
          <template #default="{ row }">
            <el-button size="small" type="success" @click="runInspection(row.id)" :loading="runningConfigId === row.id" v-permission="'inspection:run'">
              执行
            </el-button>
            <el-button size="small" @click="viewHistory(row)">历史</el-button>
            <el-button size="small" @click="editConfig(row)" v-permission="'inspection:edit'">编辑</el-button>
            <el-popconfirm title="确定删除?" @confirm="deleteConfig(row.id)">
              <template #reference>
                <el-button size="small" type="danger" v-permission="'inspection:delete'">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="editingConfig ? '编辑配置' : '添加配置'" width="650px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="名称">
          <el-input v-model="form.name" placeholder="巡检配置名称" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="监控项">
          <div style="width: 100%">
            <el-checkbox v-model="selectAllMonitors" @change="onSelectAllMonitorsChange" style="margin-bottom: 8px">全部（不限监控项）</el-checkbox>
            <el-select v-model="form.monitorIds" multiple collapse-tags collapse-tags-tooltip :disabled="selectAllMonitors" placeholder="选择要巡检的监控项" style="width: 100%">
              <el-option
                v-for="m in monitorOptions"
                :key="m.id"
                :label="m.name"
                :value="String(m.id)"
              />
            </el-select>
          </div>
        </el-form-item>
        <el-form-item label="定时执行">
          <el-select v-model="form.scheduleCron" placeholder="选择定时策略" style="width: 100%">
            <el-option
              v-for="p in cronPresets"
              :key="p.value"
              :label="p.label"
              :value="p.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="form.enabled" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveConfig">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="historyVisible" title="巡检历史" width="900px">
      <el-table :data="historyList" stripe @row-click="viewReportDetail">
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)" size="small">
              {{ getStatusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="totalMonitors" label="总数" width="80" />
        <el-table-column prop="successCount" label="成功" width="80">
          <template #default="{ row }">
            <span style="color: #67c23a">{{ row.successCount }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="failCount" label="失败" width="80">
          <template #default="{ row }">
            <span style="color: #f56c6c">{{ row.failCount }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="durationMs" label="耗时" width="100">
          <template #default="{ row }">
            {{ row.durationMs }}ms
          </template>
        </el-table-column>
        <el-table-column prop="schemaChangeCount" label="Schema" width="90">
          <template #default="{ row }">
            <el-tag v-if="row.schemaChangeCount > 0" type="warning" size="small">{{ row.schemaChangeCount }}</el-tag>
            <span v-else style="color: #c0c4cc">0</span>
          </template>
        </el-table-column>
        <el-table-column prop="startedAt" label="开始时间" width="180">
          <template #default="{ row }">
            {{ formatTime(row.startedAt) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80">
          <template #default="{ row }">
            <el-button size="small" @click.stop="viewReportDetail(row)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <el-dialog v-model="reportDetailVisible" title="巡检报告" width="900px">
      <div v-if="currentReport" class="report-detail">
        <div class="report-header">
          <el-tag :type="getStatusType(currentReport.status)" size="large">
            {{ getStatusText(currentReport.status) }}
          </el-tag>
          <span class="report-time">{{ formatTime(currentReport.startedAt) }}</span>
        </div>

        <div class="report-stats">
          <div class="stat-item">
            <div class="stat-value">{{ currentReport.totalMonitors }}</div>
            <div class="stat-label">总监控项</div>
          </div>
          <div class="stat-item success">
            <div class="stat-value">{{ currentReport.successCount }}</div>
            <div class="stat-label">成功</div>
          </div>
          <div class="stat-item fail">
            <div class="stat-value">{{ currentReport.failCount }}</div>
            <div class="stat-label">失败</div>
          </div>
          <div class="stat-item">
            <div class="stat-value">{{ currentReport.durationMs }}ms</div>
            <div class="stat-label">耗时</div>
          </div>
          <div class="stat-item" :class="{ warning: (currentReport.schemaChangeCount || 0) > 0 }">
            <div class="stat-value">{{ currentReport.schemaChangeCount || 0 }}</div>
            <div class="stat-label">Schema变化</div>
          </div>
        </div>

        <el-table :data="reportDetails" stripe style="margin-top: 20px">
          <el-table-column prop="monitorName" label="监控项" />
          <el-table-column prop="status" label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="row.status === 'SUCCESS' ? 'success' : 'danger'" size="small">
                {{ row.status === 'SUCCESS' ? '成功' : '失败' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="responseTime" label="响应时间" width="120">
            <template #default="{ row }">
              {{ row.responseTime ? row.responseTime + 'ms' : '-' }}
            </template>
          </el-table-column>
          <el-table-column prop="statusCode" label="状态码" width="80">
            <template #default="{ row }">
              {{ row.statusCode || '-' }}
            </template>
          </el-table-column>
          <el-table-column prop="schemaChangeInfo" label="Schema" width="120">
            <template #default="{ row }">
              <el-tag v-if="row.schemaChangeInfo" :type="getSchemaTagType(row.schemaChangeInfo)" size="small">
                {{ row.schemaChangeInfo }}
              </el-tag>
              <span v-else style="color: #c0c4cc">-</span>
            </template>
          </el-table-column>
          <el-table-column prop="errorMessage" label="错误信息" show-overflow-tooltip />
        </el-table>
      </div>
    </el-dialog>

    <el-dialog v-model="batchDialogVisible" title="执行巡检" width="500px">
      <el-form label-width="80px">
        <el-form-item label="执行范围">
          <el-checkbox v-model="selectAllConfigs" @change="onSelectAllConfigsChange" style="margin-bottom: 8px">全部配置</el-checkbox>
          <el-select v-model="selectedConfigIds" multiple :disabled="selectAllConfigs" placeholder="选择要执行的巡检配置" style="width: 100%">
            <el-option
              v-for="c in configs"
              :key="c.id"
              :label="c.name"
              :value="c.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="batchDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="batchRunInspection" :loading="batchRunning">
          {{ selectAllConfigs ? '执行全部' : `执行选中 (${selectedConfigIds.length})` }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { inspectionApi, monitorApi } from '@/api'
import { ElMessage } from 'element-plus'
import { formatTime } from '@/utils/format'

const configs = ref([])
const historyList = ref([])
const reportDetails = ref([])
const dialogVisible = ref(false)
const historyVisible = ref(false)
const reportDetailVisible = ref(false)
const editingConfig = ref(null)
const currentReport = ref(null)
const runningConfigId = ref(null)
const batchDialogVisible = ref(false)
const selectedConfigIds = ref([])
const selectAllConfigs = ref(false)
const batchRunning = ref(false)
const monitorOptions = ref([])
const selectAllMonitors = ref(false)
const form = ref({
  name: '',
  description: '',
  monitorIds: [],
  scheduleCron: '',
  enabled: true
})

const cronPresets = [
  { label: '不定时（仅手动执行）', value: '' },
  { label: '每 5 分钟', value: '0 */5 * * * *' },
  { label: '每 10 分钟', value: '0 */10 * * * *' },
  { label: '每 30 分钟', value: '0 */30 * * * *' },
  { label: '每 1 小时', value: '0 0 */1 * * *' },
  { label: '每天 09:00', value: '0 0 9 * * *' },
  { label: '每天 18:00', value: '0 0 18 * * *' },
  { label: '每周一 09:00', value: '0 0 9 * * 1' },
  { label: '每周五 17:00', value: '0 0 17 * * 5' }
]

const getCronLabel = (cron) => {
  const found = cronPresets.find(p => p.value === cron)
  return found ? found.label : cron
}

const onSelectAllMonitorsChange = (val) => {
  if (val) form.value.monitorIds = []
}

const onSelectAllConfigsChange = (val) => {
  if (val) selectedConfigIds.value = []
}

const getStatusType = (status) => {
  if (status === 'COMPLETED') return 'success'
  if (status === 'RUNNING') return 'warning'
  if (status === 'FAILED') return 'danger'
  return 'info'
}

const getSchemaTagType = (status) => {
  if (!status) return 'info'
  if (status === '无变更') return 'success'
  if (status === '未启用' || status === '未配置Schema' || status === '非JSON响应' || status === '无响应体') return 'info'
  if (status.includes('BREAKING') || status.includes('REMOVED')) return 'danger'
  if (status.includes('ADDED') || status.includes('MODIFIED')) return 'warning'
  return 'info'
}

const getStatusText = (status) => {
  if (status === 'COMPLETED') return '完成'
  if (status === 'RUNNING') return '执行中'
  if (status === 'FAILED') return '失败'
  return status
}

onMounted(() => {
  loadConfigs()
  loadMonitors()
})

const loadMonitors = async () => {
  try {
    const res = await monitorApi.list({ page: 1, size: 1000 })
    monitorOptions.value = res.data?.records || []
  } catch (e) {
    console.error(e)
  }
}

const loadConfigs = async () => {
  try {
    const res = await inspectionApi.listConfigs()
    configs.value = res.data || []
  } catch (e) {
    console.error(e)
  }
}

const showAddDialog = () => {
  editingConfig.value = null
  form.value = { name: '', description: '', monitorIds: [], scheduleCron: '', enabled: true }
  selectAllMonitors.value = true
  dialogVisible.value = true
}

const editConfig = (config) => {
  editingConfig.value = config
  let monitorIds = []
  if (config.monitorIds) {
    monitorIds = config.monitorIds.split(',').filter(Boolean)
  }
  selectAllMonitors.value = monitorIds.length === 0
  form.value = {
    name: config.name,
    description: config.description || '',
    monitorIds: monitorIds,
    scheduleCron: config.scheduleCron || '',
    enabled: config.enabled !== false
  }
  dialogVisible.value = true
}

const saveConfig = async () => {
  try {
    const monitorIds = selectAllMonitors.value ? '' : (Array.isArray(form.value.monitorIds) ? form.value.monitorIds.join(',') : form.value.monitorIds)
    const payload = {
      ...form.value,
      monitorIds: monitorIds
    }
    if (editingConfig.value) {
      await inspectionApi.updateConfig(editingConfig.value.id, payload)
    } else {
      await inspectionApi.createConfig(payload)
    }
    ElMessage.success('保存成功')
    dialogVisible.value = false
    loadConfigs()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

const deleteConfig = async (id) => {
  try {
    await inspectionApi.deleteConfig(id)
    ElMessage.success('删除成功')
    loadConfigs()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

const runInspection = async (configId) => {
  try {
    runningConfigId.value = configId
    await inspectionApi.runInspection(configId)
    ElMessage.success('巡检已启动')
    setTimeout(() => {
      loadConfigs()
      runningConfigId.value = null
    }, 2000)
  } catch (e) {
    ElMessage.error(e.message)
    runningConfigId.value = null
  }
}

const showBatchDialog = () => {
  if (configs.value.length === 0) {
    ElMessage.warning('请先添加巡检配置')
    return
  }
  selectedConfigIds.value = []
  selectAllConfigs.value = false
  batchDialogVisible.value = true
}

const batchRunInspection = async () => {
  const ids = selectAllConfigs.value ? configs.value.map(c => c.id) : selectedConfigIds.value
  if (ids.length === 0) {
    ElMessage.warning('请选择至少一个巡检配置')
    return
  }
  batchRunning.value = true
  try {
    for (const id of ids) {
      await inspectionApi.runInspection(id)
    }
    ElMessage.success(`已启动 ${ids.length} 个巡检`)
    batchDialogVisible.value = false
    setTimeout(() => loadConfigs(), 2000)
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    batchRunning.value = false
  }
}

const viewHistory = async (config) => {
  try {
    const res = await inspectionApi.listHistory(config.id)
    historyList.value = res.data || []
    historyVisible.value = true
  } catch (e) {
    ElMessage.error(e.message)
  }
}

const viewReportDetail = async (row) => {
  try {
    currentReport.value = row
    const res = await inspectionApi.getHistoryDetails(row.id)
    reportDetails.value = res.data || []
    reportDetailVisible.value = true
  } catch (e) {
    ElMessage.error(e.message)
  }
}
</script>

<style lang="scss" scoped>
.inspection-page {
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

.config-card {
  margin-bottom: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.report-detail {
  .report-header {
    display: flex;
    align-items: center;
    gap: 16px;
    margin-bottom: 20px;
  }

  .report-time {
    color: #909399;
  }
}

.report-stats {
  display: flex;
  gap: 24px;

  .stat-item {
    text-align: center;
    padding: 16px 24px;
    background: #f5f7fa;
    border-radius: 8px;
    min-width: 100px;

    .stat-value {
      font-size: 24px;
      font-weight: 600;
      color: #303133;
      margin-bottom: 8px;
    }

    .stat-label {
      font-size: 13px;
      color: #909399;
    }

    &.success .stat-value {
      color: #67c23a;
    }

    &.fail .stat-value {
      color: #f56c6c;
    }

    &.warning .stat-value {
      color: #e6a23c;
    }
  }
}
</style>
