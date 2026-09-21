<template>
  <div class="group-list">
    <div class="page-header">
      <h2>监控任务</h2>
      <el-button type="primary" @click="showDialog()" v-permission="'group:create'">
        <el-icon><Plus /></el-icon>
        添加任务
      </el-button>
    </div>

    <!-- Search -->
    <el-card class="search-card">
      <el-form :inline="true" :model="searchForm">
        <el-form-item label="ID">
          <el-input-number v-model="searchForm.id" :min="1" controls-position="right" placeholder="任务ID" style="width: 130px" />
        </el-form-item>
        <el-form-item label="名称">
          <el-input v-model="searchForm.keyword" placeholder="任务名称" clearable />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.enabled" placeholder="全部" clearable style="width: 120px">
            <el-option label="启用" :value="true" />
            <el-option label="禁用" :value="false" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="resetSearch">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- Table -->
    <el-card>
      <el-table :data="groups" stripe v-loading="loading">
        <el-table-column prop="id" label="ID" width="60">
          <template #default="{ row }">
            <router-link :to="{ path: '/log', query: { groupId: row.id } }" class="id-link">{{ row.id }}</router-link>
          </template>
        </el-table-column>
        <el-table-column prop="name" label="名称" min-width="150" />
        <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
        <el-table-column prop="cronExpression" label="Cron表达式" width="150" />
        <el-table-column prop="enabled" label="状态" width="80">
          <template #default="{ row }">
            <el-tag v-if="row.running" type="warning" size="small">执行中</el-tag>
            <el-tag v-else :type="row.enabled ? 'success' : 'info'" size="small">
              {{ row.enabled ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="180">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="关联监控项" width="110" align="center">
          <template #default="{ row }">
            <el-button type="primary" link @click="viewMonitors(row)">
              {{ row.monitorCount || 0 }}
            </el-button>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.running" type="info" link disabled>执行中...</el-button>
            <el-button v-else type="primary" link @click="runGroup(row.id)" v-permission="'group:run'">执行</el-button>
            <el-button type="primary" link @click="showDialog(row)" v-permission="'group:edit'">编辑</el-button>
            <el-popconfirm title="确认删除?" @confirm="deleteGroup(row.id)">
              <template #reference>
                <el-button type="danger" link v-permission="'group:delete'">删除</el-button>
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
        @change="loadGroups"
      />
    </el-card>

    <!-- Create/Edit Dialog -->
    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑任务' : '添加任务'" width="700px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="120px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="2" />
        </el-form-item>

        <!-- 执行计划配置 -->
        <el-form-item label="执行计划">
          <el-radio-group v-model="scheduleType" @change="onScheduleTypeChange">
            <el-radio value="interval">固定间隔</el-radio>
            <el-radio value="daily">每天</el-radio>
            <el-radio value="weekly">每周</el-radio>
          </el-radio-group>
        </el-form-item>

        <!-- 固定间隔配置 -->
        <el-form-item v-if="scheduleType === 'interval'" label="执行间隔">
          <el-input-number v-model="intervalMinutes" :min="1" :max="1440" />
          <span style="margin-left: 8px">分钟</span>
        </el-form-item>

        <!-- 每天执行配置 -->
        <el-form-item v-if="scheduleType === 'daily'" label="执行时间">
          <el-time-picker v-model="dailyTime" format="HH:mm:ss" value-format="HH:mm:ss" placeholder="选择时间" />
        </el-form-item>

        <!-- 每周执行配置 -->
        <el-form-item v-if="scheduleType === 'weekly'" label="执行设置">
          <div style="display: flex; gap: 12px; align-items: center">
            <el-select v-model="weeklyDay" placeholder="选择星期" style="width: 120px">
              <el-option label="周日" :value="0" />
              <el-option label="周一" :value="1" />
              <el-option label="周二" :value="2" />
              <el-option label="周三" :value="3" />
              <el-option label="周四" :value="4" />
              <el-option label="周五" :value="5" />
              <el-option label="周六" :value="6" />
            </el-select>
            <el-time-picker v-model="weeklyTime" format="HH:mm:ss" value-format="HH:mm:ss" placeholder="选择时间" />
          </div>
        </el-form-item>

        <el-form-item label="启用任务">
          <el-switch v-model="form.enabled" />
        </el-form-item>

        <!-- 告警配置 -->
        <el-divider content-position="left">
          告警配置
          <el-tooltip placement="right" :width="400">
            <template #content>
              <div style="line-height: 1.8">
                <div>失败判定方式：</div>
                <div>• <b>任意失败</b> — 任一监控项失败即判定任务失败</div>
                <div>• <b>失败数量</b> — 失败数 ≥ 阈值时判定任务失败</div>
                <div>• <b>失败比例</b> — 失败率 ≥ 阈值时判定任务失败</div>
                <div style="margin-top: 4px">连续N次达到失败条件后触发告警，计数在任务成功执行后重置。</div>
              </div>
            </template>
            <el-icon style="margin-left: 6px; cursor: pointer; color: #909399; vertical-align: middle;"><QuestionFilled /></el-icon>
          </el-tooltip>
        </el-divider>
        <el-form-item label="失败告警">
          <el-switch v-model="form.alertOnFail" />
        </el-form-item>
        <template v-if="form.alertOnFail">
          <el-form-item label="失败判定">
            <el-radio-group v-model="form.failCriteriaType">
              <el-radio value="ANY">任意失败</el-radio>
              <el-radio value="COUNT">失败数量</el-radio>
              <el-radio value="PERCENT">失败比例</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item v-if="form.failCriteriaType === 'COUNT'" label="数量阈值">
            <el-input-number v-model="form.failCountThreshold" :min="1" :max="100" />
            <span style="margin-left: 8px">个监控项失败时判定为任务失败</span>
          </el-form-item>
          <el-form-item v-if="form.failCriteriaType === 'PERCENT'" label="比例阈值">
            <el-input-number v-model="form.failPercentThreshold" :min="1" :max="100" />
            <span style="margin-left: 8px">% 失败时判定为任务失败</span>
          </el-form-item>
          <el-form-item label="连续失败">
            <el-input-number v-model="form.failThreshold" :min="1" :max="100" />
            <span style="margin-left: 8px">次连续达到失败条件后告警</span>
          </el-form-item>
          <el-form-item label="告警方式">
            <el-checkbox-group v-model="selectedAlertConfigs">
              <el-checkbox v-for="config in allAlertConfigs" :key="config.id" :value="config.id">
                {{ config.name }}
                <el-tag :type="config.enabled ? 'success' : 'info'" size="small" style="margin-left: 4px">
                  {{ config.enabled ? '启用' : '禁用' }}
                </el-tag>
              </el-checkbox>
            </el-checkbox-group>
          </el-form-item>
        </template>

        <!-- Monitor Items -->
        <el-divider content-position="left">
          监控项列表
          <el-tooltip placement="right" :width="360">
            <template #content>
              <div style="line-height: 1.8">
                <div>任务按排序值从小到大依次执行监控项。</div>
                <div>禁用状态的监控项会被跳过。</div>
                <div>"失败继续"表示某项失败后是否继续执行后续项。</div>
              </div>
            </template>
            <el-icon style="margin-left: 6px; cursor: pointer; color: #909399; vertical-align: middle;"><QuestionFilled /></el-icon>
          </el-tooltip>
        </el-divider>
        <div class="monitor-items">
          <div v-for="(item, index) in form.monitors" :key="index" class="monitor-item">
            <el-select v-model="item.monitorId" placeholder="选择监控项" style="width: 200px">
              <el-option
                v-for="m in allMonitors"
                :key="m.id"
                :label="m.name"
                :value="m.id"
              >
                <span>{{ m.name }}</span>
                <el-tag :type="m.enabled ? 'success' : 'info'" size="small" style="margin-left: 8px; float: right">
                  {{ m.enabled ? '启用' : '禁用' }}
                </el-tag>
              </el-option>
            </el-select>
            <el-input-number v-model="item.sortOrder" :min="0" placeholder="排序" style="width: 100px" />
            <el-checkbox v-model="item.continueOnFail">失败继续</el-checkbox>
            <el-button type="danger" link @click="removeMonitorItem(index)">
              <el-icon><Delete /></el-icon>
            </el-button>
          </div>
          <el-button type="primary" link @click="addMonitorItem">
            <el-icon><Plus /></el-icon>
            添加监控项
          </el-button>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitForm" :loading="submitting" v-permission="editingId ? 'group:edit' : 'group:create'">确定</el-button>
      </template>
    </el-dialog>

    <!-- View Monitors Dialog -->
    <el-dialog v-model="monitorsDialogVisible" title="任务监控项" width="600px">
      <el-table :data="groupMonitors" stripe>
        <el-table-column prop="monitorId" label="ID" width="60" />
        <el-table-column prop="monitorName" label="监控项名称" min-width="200" show-overflow-tooltip />
        <el-table-column prop="sortOrder" label="排序" width="70" />
        <el-table-column prop="continueOnFail" label="失败继续" width="90">
          <template #default="{ row }">
            {{ row.continueOnFail ? '是' : '否' }}
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { groupApi, monitorApi, alertChannelApi } from '@/api'
import { formatTime } from '@/utils/format'
import { usePermission } from '@/composables/usePermission'

const route = useRoute()
const { hasButton } = usePermission()

const loading = ref(false)
const submitting = ref(false)
const dialogVisible = ref(false)
const monitorsDialogVisible = ref(false)
const editingId = ref(null)
const groups = ref([])
const allMonitors = ref([])
const allAlertConfigs = ref([])
const groupMonitors = ref([])
const formRef = ref(null)

const searchForm = reactive({ keyword: '', enabled: null, id: null })
const pagination = reactive({ page: 1, size: 10, total: 0 })

// Schedule configuration
const scheduleType = ref('interval')
const intervalMinutes = ref(5)
const dailyTime = ref('00:00:00')
const weeklyDay = ref(1)
const weeklyTime = ref('00:00:00')
const selectedAlertConfigs = ref([])

const form = reactive({
  name: '',
  description: '',
  cronExpression: '',
  scheduleType: 'interval',
  scheduleConfig: '',
  retryInterval: 60,
  enabled: true,
  alertOnFail: false,
  failThreshold: 1,
  failCriteriaType: 'ANY',
  failCountThreshold: 1,
  failPercentThreshold: 50,
  alertConfigIds: '',
  monitors: []
})

const rules = {
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }]
}

onMounted(async () => {
  if (route.query.enabled !== undefined) {
    searchForm.enabled = route.query.enabled === 'true'
  }
  if (route.query.filterId) {
    searchForm.id = Number(route.query.filterId)
  }
  await loadGroups()
  await loadAllMonitors()
  await loadAllAlertConfigs()
})

const loadGroups = async () => {
  loading.value = true
  try {
    const res = await groupApi.list({
      page: pagination.page,
      size: pagination.size,
      keyword: searchForm.keyword,
      enabled: searchForm.enabled,
      id: searchForm.id || undefined
    })
    groups.value = res.data?.records || []
    pagination.total = res.data?.total || 0
  } catch (error) {
    ElMessage.error('加载失败')
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  pagination.page = 1
  loadGroups()
}

const resetSearch = () => {
  searchForm.keyword = ''
  searchForm.enabled = null
  searchForm.id = null
  pagination.page = 1
  loadGroups()
}

const loadAllMonitors = async () => {
  try {
    const res = await monitorApi.list({ page: 1, size: 1000 })
    allMonitors.value = res.data?.records || []
  } catch (error) {
    console.error('Failed to load monitors:', error)
  }
}

const loadAllAlertConfigs = async () => {
  if (!hasButton('alert-template:edit')) return
  try {
    const res = await alertChannelApi.list()
    allAlertConfigs.value = res.data || []
  } catch (error) {
    console.error('Failed to load alert configs:', error)
  }
}

const generateCronExpression = () => {
  if (scheduleType.value === 'interval') {
    return `0 */${intervalMinutes.value} * * * ?`
  } else if (scheduleType.value === 'daily') {
    const time = dailyTime.value || '00:00:00'
    const parts = time.split(':')
    return `0 ${parts[1]} ${parts[0]} * * ?`
  } else if (scheduleType.value === 'weekly') {
    const time = weeklyTime.value || '00:00:00'
    const parts = time.split(':')
    return `0 ${parts[1]} ${parts[0]} * * ${weeklyDay.value}`
  }
  return '0 */5 * * * ?'
}

const parseCronExpression = (cron) => {
  if (!cron) return
  const parts = cron.split(' ')
  if (parts.length < 6) return

  if (parts[2] === '*' && parts[3] === '*' && parts[4] === '*') {
    // Interval pattern: 0 */N * * * ?
    const match = parts[1].match(/\*\/(\d+)/)
    if (match) {
      scheduleType.value = 'interval'
      intervalMinutes.value = parseInt(match[1])
    }
  } else if (parts[3] === '*' && parts[4] === '*') {
    // Daily pattern: 0 MM HH * * ?
    scheduleType.value = 'daily'
    dailyTime.value = `${parts[2].padStart(2, '0')}:${parts[1].padStart(2, '0')}:00`
  } else if (parts[3] === '*') {
    // Weekly pattern: 0 MM HH * * D
    scheduleType.value = 'weekly'
    weeklyDay.value = parseInt(parts[5])
    weeklyTime.value = `${parts[2].padStart(2, '0')}:${parts[1].padStart(2, '0')}:00`
  }
}

const onScheduleTypeChange = () => {
  // No-op, just for future use
}

const showDialog = async (row) => {
  if (row) {
    editingId.value = row.id
    Object.assign(form, row)
    // Parse cron expression
    if (row.cronExpression) {
      parseCronExpression(row.cronExpression)
    }
    // Parse alert config ids
    if (row.alertConfigIds) {
      selectedAlertConfigs.value = row.alertConfigIds.split(',').map(id => parseInt(id))
    } else {
      selectedAlertConfigs.value = []
    }
    // Load group monitors
    try {
      const res = await groupApi.getMonitors(row.id)
      form.monitors = res.data || []
    } catch (error) {
      console.error('Failed to load group monitors:', error)
    }
  } else {
    editingId.value = null
    Object.assign(form, {
      name: '', description: '', cronExpression: '', scheduleType: 'interval',
      scheduleConfig: '', retryInterval: 60, enabled: true,
  alertOnFail: false,
  failThreshold: 1,
  failCriteriaType: 'ANY',
  failCountThreshold: 1,
  failPercentThreshold: 50,
  alertConfigIds: '', monitors: []
    })
    delete form.id
    scheduleType.value = 'interval'
    intervalMinutes.value = 5
    dailyTime.value = '00:00:00'
    weeklyDay.value = 1
    weeklyTime.value = '00:00:00'
    selectedAlertConfigs.value = []
  }
  dialogVisible.value = true
}

const addMonitorItem = () => {
  form.monitors.push({
    monitorId: null,
    sortOrder: form.monitors.length,
    continueOnFail: true,
    variableScope: 'GROUP'
  })
}

const removeMonitorItem = (index) => {
  form.monitors.splice(index, 1)
}

const submitForm = async () => {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  // Generate cron expression from schedule config
  form.cronExpression = generateCronExpression()
  form.scheduleType = scheduleType.value
  form.alertConfigIds = selectedAlertConfigs.value.join(',')

  submitting.value = true
  try {
    if (editingId.value) {
      await groupApi.update(editingId.value, form)
      ElMessage.success('更新成功')
    } else {
      await groupApi.create(form)
      ElMessage.success('创建成功')
    }
    editingId.value = null
    delete form.id
    dialogVisible.value = false
    loadGroups()
  } catch (error) {
    ElMessage.error(error.message || '操作失败')
  } finally {
    submitting.value = false
  }
}

const deleteGroup = async (id) => {
  try {
    await groupApi.delete(id)
    ElMessage.success('删除成功')
    loadGroups()
  } catch (error) {
    ElMessage.error('删除失败')
  }
}

const runGroup = async (id) => {
  try {
    await groupApi.run(id)
    ElMessage.success('任务执行已启动')
  } catch (error) {
    ElMessage.error('执行失败')
  }
}

const viewMonitors = async (row) => {
  try {
    const res = await groupApi.getMonitors(row.id)
    groupMonitors.value = res.data || []
    monitorsDialogVisible.value = true
  } catch (error) {
    ElMessage.error('加载失败')
  }
}
</script>

<style lang="scss" scoped>
.group-list {
  .search-card {
    margin-bottom: 20px;
  }

  .el-pagination {
    margin-top: 20px;
    justify-content: flex-end;
  }

  .monitor-items {
    padding-left: 120px;

    .monitor-item {
      display: flex;
      align-items: center;
      gap: 12px;
      margin-bottom: 12px;
    }
  }

  .id-link {
    color: #409eff;
    text-decoration: none;
    &:hover { text-decoration: underline; }
  }
}
</style>
