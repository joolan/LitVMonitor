<template>
  <div class="monitor-list">
    <div class="page-header">
      <h2>监控项管理</h2>
      <div class="header-actions">
        <el-button type="warning" @click="exportMonitors" v-permission="'monitor:create'">
          <el-icon><Download /></el-icon>
          导出Excel
        </el-button>
        <el-button type="info" @click="showImportDialog" v-permission="'monitor:create'">
          <el-icon><Upload /></el-icon>
          导入Excel
        </el-button>
        <el-button type="primary" @click="showDialog()" v-permission="'monitor:create'">
          <el-icon><Plus /></el-icon>
          添加HTTP监控
        </el-button>
        <el-button type="success" @click="showOtherDialog()" v-permission="'monitor:create'">
          <el-icon><Plus /></el-icon>
          添加其他监控
        </el-button>
      </div>
    </div>

    <!-- Search -->
    <el-card class="search-card">
      <el-form :inline="true" :model="searchForm">
        <el-form-item label="ID">
          <el-input-number v-model="searchForm.id" :min="1" controls-position="right" placeholder="监控ID" style="width: 130px" />
        </el-form-item>
        <el-form-item label="关键词">
          <el-input v-model="searchForm.keyword" placeholder="名称或URL" clearable />
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
      <div v-if="selectedIds.length > 0" class="batch-bar">
        <span>已选择 {{ selectedIds.length }} 项</span>
        <el-button type="success" size="small" @click="batchUpdateStatus(true)" v-permission="'monitor:batch-status'">批量启用</el-button>
        <el-button type="warning" size="small" @click="batchUpdateStatus(false)" v-permission="'monitor:batch-status'">批量禁用</el-button>
        <el-button type="primary" size="small" @click="showBatchGroupDialog('add')" v-permission="'group:create'">批量添加到任务</el-button>
        <el-button type="danger" size="small" @click="showBatchGroupDialog('remove')" v-permission="'group:edit'">批量从任务移除</el-button>
        <el-button size="small" @click="clearSelection">取消选择</el-button>
      </div>
      <el-table ref="tableRef" :data="monitors" stripe v-loading="loading" @selection-change="onSelectionChange">
        <el-table-column type="selection" width="45" />
        <el-table-column prop="id" label="ID" width="60">
          <template #default="{ row }">
            <router-link :to="{ path: '/log', query: { monitorId: row.id } }" class="id-link">{{ row.id }}</router-link>
          </template>
        </el-table-column>
        <el-table-column prop="name" label="名称" min-width="150" />
        <el-table-column prop="url" label="URL / 地址" min-width="250" show-overflow-tooltip />
        <el-table-column prop="monitorType" label="类型" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="monitorTypeTag(row.monitorType)">{{ row.monitorType || 'HTTP' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="method" label="方法" width="80">
          <template #default="{ row }">
            <span v-if="!row.monitorType || row.monitorType === 'HTTP'">{{ row.method }}</span>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="enabled" label="状态" width="80">
          <template #default="{ row }">
            <el-switch
              v-model="row.enabled"
              :loading="togglingId === row.id"
              @change="toggleEnabled(row)"
              v-permission="'monitor:edit'"
            />
          </template>
        </el-table-column>
        <el-table-column label="关联任务" width="100" align="center">
          <template #default="{ row }">
            <el-button v-if="row.groupCount > 0" type="primary" link @click="showGroupDialog(row)">
              {{ row.groupCount }}
            </el-button>
            <span v-else class="text-muted">0</span>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="180">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="testMonitor(row.id)" :loading="testingId === row.id" :disabled="testingId != null" v-permission="'monitor:test'">
              {{ testingId === row.id ? '测试中...' : '测试' }}
            </el-button>
            <el-button type="primary" link @click="copyMonitor(row.id)" v-permission="'monitor:copy'">复制</el-button>
            <el-button type="primary" link @click="editMonitor(row)" v-permission="'monitor:edit'">编辑</el-button>
            <el-popconfirm title="确认删除?" @confirm="deleteMonitor(row.id)">
              <template #reference>
                <el-button type="danger" link v-permission="'monitor:delete'">删除</el-button>
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
        @change="loadMonitors"
      />
    </el-card>

    <!-- HTTP Monitor Dialog -->
    <MonitorHttpDialog
      v-model="httpDialogVisible"
      :editing-row="httpEditingRow"
      @saved="loadMonitors"
    />

    <!-- Non-HTTP Monitor Dialog -->
    <MonitorOtherDialog
      v-model="otherDialogVisible"
      :editing-row="otherEditingRow"
      :alert-configs="allAlertConfigs"
      @saved="loadMonitors"
    />

    <!-- Group Management Dialog -->
    <el-dialog v-model="groupDialogVisible" :title="`关联任务 - ${currentMonitor?.name || ''}`" width="650px">
      <div style="margin-bottom: 12px">
        <el-button type="primary" size="small" @click="showAddGroupForm" v-permission="'group:create'">
          <el-icon><Plus /></el-icon> 添加到任务
        </el-button>
      </div>
      <div v-if="showAddForm" class="add-group-form">
        <el-select v-model="newGroupForm.groupId" placeholder="选择监控任务" style="width: 200px">
          <el-option v-for="g in availableGroups" :key="g.id" :label="g.name" :value="g.id" />
        </el-select>
        <el-input-number v-model="newGroupForm.sortOrder" :min="0" placeholder="排序" style="width: 100px; margin-left: 8px" />
        <el-checkbox v-model="newGroupForm.continueOnFail" style="margin-left: 8px">失败继续</el-checkbox>
        <el-button type="primary" size="small" style="margin-left: 8px" @click="confirmAddGroup" :loading="groupSaving">确定</el-button>
        <el-button size="small" @click="showAddForm = false">取消</el-button>
      </div>
      <el-table :data="monitorGroups" stripe v-loading="groupLoading" empty-text="未关联任何任务">
        <el-table-column prop="groupName" label="任务名称" min-width="150" />
        <el-table-column label="排序" width="120">
          <template #default="{ row }">
            <el-input-number v-model="row.sortOrder" :min="0" size="small" style="width: 80px"
              @change="saveGroupConfig(row)" />
          </template>
        </el-table-column>
        <el-table-column label="失败继续" width="100" align="center">
          <template #default="{ row }">
            <el-switch v-model="row.continueOnFail" size="small" @change="saveGroupConfig(row)" v-permission="'group:edit'" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80" align="center">
          <template #default="{ row }">
            <el-popconfirm title="确认从该任务中移除?" @confirm="removeFromGroup(row)">
              <template #reference>
                <el-button type="danger" link size="small" v-permission="'group:edit'">移除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <!-- Batch Add/Remove Group Dialog -->
    <el-dialog v-model="batchGroupDialogVisible" :title="batchGroupMode === 'add' ? '批量添加到任务' : '批量从任务移除'" width="700px">
      <el-form label-width="80px">
        <el-form-item label="选择任务">
          <el-select v-model="batchGroupTargetId" placeholder="选择监控任务" style="width: 100%">
            <el-option v-for="g in allGroups" :key="g.id" :label="g.name" :value="g.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template v-if="batchGroupMode === 'add' && batchGroupTargetId">
        <el-divider content-position="left">监控项配置</el-divider>
        <el-table :data="batchMonitorList" stripe size="small" max-height="350">
          <el-table-column prop="name" label="监控项名称" min-width="180" show-overflow-tooltip />
          <el-table-column label="排序" width="120" align="center">
            <template #default="{ row }">
              <el-input-number v-model="row.sortOrder" :min="0" size="small" style="width: 80px" />
            </template>
          </el-table-column>
          <el-table-column label="失败继续" width="90" align="center">
            <template #default="{ row }">
              <el-switch v-model="row.continueOnFail" size="small" />
            </template>
          </el-table-column>
        </el-table>
      </template>
      <template #footer>
        <el-button @click="batchGroupDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmBatchGroup" :loading="groupSaving" :disabled="!batchGroupTargetId" v-permission="batchGroupMode === 'add' ? 'group:create' : 'group:edit'">
          {{ batchGroupMode === 'add' ? '添加' : '移除' }} ({{ selectedIds.length }}项)
        </el-button>
      </template>
    </el-dialog>

    <!-- 导入监控项弹窗 -->
    <el-dialog v-model="importDialogVisible" title="导入监控项" width="560px" :close-on-click-modal="false">
      <el-alert type="info" :closable="false" style="margin-bottom: 16px;">
        <template #title>
          <div style="line-height: 1.8;">
            <b>导入规则：</b><br>
            1. 仅支持 .xlsx / .xls 文件，从「导出Excel」下载的模板填写后导入<br>
            2. 导入为<b>追加模式</b>，不会覆盖已有监控项<br>
            3. <b>名称</b>和 <b>URL/地址</b>为必填项，缺少则校验失败整表回滚<br>
            4. 告警渠道 ID 不存在时自动置空，不会导致导入失败<br>
            5. 监控项中使用的变量（如 <code v-text="'{{global.xxx}}'"></code>）原样保留，后续手动配置即可<br>
            6. 导入过程为<b>事务整体提交</b>，任意行校验失败则全部回滚
          </div>
        </template>
      </el-alert>

      <el-form label-width="140px">
        <el-form-item label="选择文件">
          <el-upload
            ref="importUploadRef"
            :auto-upload="false"
            :limit="1"
            accept=".xlsx,.xls"
            :on-change="onImportFileChange"
            :on-exceed="() => ElMessage.warning('只能选择一个文件')"
          >
            <el-button type="primary">选择 Excel 文件</el-button>
            <template #tip>
              <div style="color: #909399; font-size: 12px; margin-top: 4px;">支持 .xlsx / .xls 格式</div>
            </template>
          </el-upload>
        </el-form-item>

        <el-form-item label="告警渠道处理">
          <el-radio-group v-model="importChannelMode">
            <el-radio value="keep">保持 Excel 原值</el-radio>
            <el-radio value="override">手动选择渠道替换</el-radio>
          </el-radio-group>
          <div style="color: #909399; font-size: 12px; margin-top: 4px;">
            <span v-if="importChannelMode === 'keep'">Excel 中的告警渠道 ID 原样导入，不存在的 ID 自动置空</span>
            <span v-else>所有导入项的告警渠道统一替换为下方选择的渠道</span>
          </div>
        </el-form-item>

        <el-form-item v-if="importChannelMode === 'override'" label="选择告警渠道">
          <el-select v-model="importChannelIds" multiple placeholder="选择告警渠道" style="width: 100%">
            <el-option v-for="c in allAlertConfigs" :key="c.id" :label="c.name" :value="String(c.id)">
              <span>{{ c.name }}</span>
              <el-tag :type="c.enabled ? 'success' : 'info'" size="small" style="margin-left: 8px; float: right">
                {{ c.enabled ? '启用' : '禁用' }}
              </el-tag>
            </el-option>
          </el-select>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="closeImportDialog">取消</el-button>
        <el-button type="primary" @click="executeImport" :loading="importLoading" :disabled="!importFile">
          确认导入
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { monitorApi, alertChannelApi, groupApi } from '@/api'
import { formatTime } from '@/utils/format'
import MonitorHttpDialog from '@/components/MonitorHttpDialog.vue'
import MonitorOtherDialog from '@/components/MonitorOtherDialog.vue'

const route = useRoute()

const loading = ref(false)
const testingId = ref(null)
const monitors = ref([])
const tableRef = ref(null)
const allAlertConfigs = ref([])
const selectedIds = ref([])
const togglingId = ref(null)

// HTTP monitor dialog (实现见 components/MonitorHttpDialog.vue)
const httpDialogVisible = ref(false)
const httpEditingRow = ref(null)

// Non-HTTP monitor dialog (实现见 components/MonitorOtherDialog.vue)
const otherDialogVisible = ref(false)
const otherEditingRow = ref(null)

// Group management
const groupDialogVisible = ref(false)
const groupLoading = ref(false)
const groupSaving = ref(false)
const currentMonitor = ref(null)
const monitorGroups = ref([])
const availableGroups = ref([])
const allGroups = ref([])
const showAddForm = ref(false)
const newGroupForm = reactive({ groupId: null, sortOrder: 0, continueOnFail: true })

// Batch group
const batchGroupDialogVisible = ref(false)
const batchGroupMode = ref('add')
const batchGroupTargetId = ref(null)
const batchMonitorList = ref([])

const searchForm = reactive({ keyword: '', enabled: null, id: null })
const pagination = reactive({ page: 1, size: 10, total: 0 })

// Import/Export
const importDialogVisible = ref(false)
const importLoading = ref(false)
const importFile = ref(null)
const importChannelMode = ref('keep')
const importChannelIds = ref([])
const importUploadRef = ref(null)

const exportMonitors = async () => {
  try {
    const blob = await monitorApi.export()
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = '监控项列表.xlsx'
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(url)
    ElMessage.success('导出成功')
  } catch (error) {
    ElMessage.error('导出失败')
  }
}

const onImportFileChange = (file) => {
  importFile.value = file.raw
}

const closeImportDialog = () => {
  importDialogVisible.value = false
  importFile.value = null
  importChannelMode.value = 'keep'
  importChannelIds.value = []
  if (importUploadRef.value) importUploadRef.value.clearFiles()
}

const executeImport = async () => {
  if (!importFile.value) {
    ElMessage.warning('请先选择文件')
    return
  }
  importLoading.value = true
  try {
    const formData = new FormData()
    formData.append('file', importFile.value)
    formData.append('alertChannelOverride', importChannelMode.value === 'override')
    if (importChannelMode.value === 'override') {
      formData.append('alertChannelIds', importChannelIds.value.join(','))
    }
    const res = await monitorApi.import(formData)
    if (res.code === 200) {
      ElMessage.success(`成功导入 ${res.data.count} 个监控项`)
      closeImportDialog()
      loadMonitors()
    } else {
      ElMessage.error(res.message || '导入失败')
    }
  } catch (error) {
    ElMessage.error(error.message || '导入失败')
  } finally {
    importLoading.value = false
  }
}

const loadAlertConfigs = async () => {
  try {
    const res = await alertChannelApi.list()
    allAlertConfigs.value = res.data || []
  } catch (error) {
    console.error('Failed to load alert channels:', error)
  }
}

const applyQuery = () => {
  if (route.query.enabled !== undefined) {
    searchForm.enabled = route.query.enabled === 'true'
  }
  if (route.query.filterId) {
    searchForm.id = Number(route.query.filterId)
  }
  pagination.page = 1
  loadMonitors()
}

onMounted(applyQuery)
watch(() => route.query, () => applyQuery())

const loadMonitors = async () => {
  loading.value = true
  try {
    const res = await monitorApi.list({
      page: pagination.page,
      size: pagination.size,
      keyword: searchForm.keyword,
      enabled: searchForm.enabled,
      id: searchForm.id || undefined
    })
    monitors.value = res.data?.records || []
    pagination.total = res.data?.total || 0
  } catch (error) {
    ElMessage.error('加载失败')
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  pagination.page = 1
  loadMonitors()
}

const resetSearch = () => {
  searchForm.keyword = ''
  searchForm.enabled = null
  searchForm.id = null
  pagination.page = 1
  loadMonitors()
}

const onSelectionChange = (rows) => {
  selectedIds.value = rows.map(r => r.id)
}

const clearSelection = () => {
  selectedIds.value = []
  tableRef.value?.clearSelection()
}

const toggleEnabled = async (row) => {
  togglingId.value = row.id
  try {
    await monitorApi.batchUpdateStatus([row.id], row.enabled)
    ElMessage.success(`已${row.enabled ? '启用' : '禁用'} ${row.name}`)
  } catch (error) {
    row.enabled = !row.enabled
    ElMessage.error('操作失败')
  } finally {
    togglingId.value = null
  }
}

const batchUpdateStatus = async (enabled) => {
  if (selectedIds.value.length === 0) return
  try {
    await monitorApi.batchUpdateStatus(selectedIds.value, enabled)
    ElMessage.success(`已${enabled ? '启用' : '禁用'} ${selectedIds.value.length} 个监控项`)
    clearSelection()
    loadMonitors()
  } catch (error) {
    ElMessage.error('操作失败')
  }
}

const monitorTypeTag = (type) => {
  const map = {
    HTTP: 'primary', PING: 'success', TCP: 'warning',
    SSH: 'info', TELNET: 'info', FTP: 'info', VNC: 'info',
    MYSQL: 'danger', POSTGRESQL: 'danger', REDIS: 'danger',
    MEMCACHED: 'danger', MONGODB: 'danger', ZOOKEEPER: 'danger',
    AMQP: 'success', MQTT: 'success'
  }
  return map[type] || 'primary'
}

const editMonitor = (row) => {
  const type = row.monitorType || 'HTTP'
  if (type === 'HTTP') {
    showDialog(row)
  } else {
    showOtherDialog(row)
  }
}

const showDialog = (row) => {
  httpEditingRow.value = row || null
  httpDialogVisible.value = true
}

const showOtherDialog = async (row) => {
  await loadAlertConfigs()
  otherEditingRow.value = row || null
  otherDialogVisible.value = true
}

const showImportDialog = async () => {
  await loadAlertConfigs()
  importDialogVisible.value = true
}

const deleteMonitor = async (id) => {
  try {
    await monitorApi.delete(id)
    ElMessage.success('删除成功')
    loadMonitors()
  } catch (error) {
    ElMessage.error('删除失败')
  }
}

const testMonitor = async (id) => {
  testingId.value = id
  try {
    const res = await monitorApi.test(id)
    if (res.data.status === 'SUCCESS') {
      ElMessage.success(`测试成功 (状态码: ${res.data.statusCode}, 响应时间: ${res.data.responseTime}ms)`)
    } else if (res.data.status === 'ERROR') {
      ElMessage.error(`请求异常: ${res.data.errorMessage || '未知错误'}`)
    } else {
      ElMessage.error(`测试失败: ${res.data.errorMessage || '状态码不符合预期'}`)
    }
  } catch (error) {
    ElMessage.error('测试请求失败')
  } finally {
    testingId.value = null
  }
}

const copyMonitor = async (id) => {
  try {
    await monitorApi.copy(id)
    ElMessage.success('复制成功')
    loadMonitors()
  } catch (error) {
    ElMessage.error('复制失败')
  }
}

const loadAllGroups = async () => {
  try {
    const res = await groupApi.list({ page: 1, size: 1000 })
    allGroups.value = res.data?.records || []
  } catch (e) {
    console.error('Failed to load groups:', e)
  }
}

const showGroupDialog = async (row) => {
  currentMonitor.value = row
  groupLoading.value = true
  showAddForm.value = false
  groupDialogVisible.value = true
  try {
    await loadAllGroups()
    const groupsRes = await monitorApi.getGroups(row.id)
    monitorGroups.value = groupsRes.data || []
    availableGroups.value = allGroups.value.filter(g =>
      !monitorGroups.value.some(mg => mg.groupId === g.id)
    )
  } catch (e) {
    ElMessage.error('加载关联任务失败')
  } finally {
    groupLoading.value = false
  }
}

const showAddGroupForm = () => {
  newGroupForm.groupId = null
  newGroupForm.sortOrder = 0
  newGroupForm.continueOnFail = true
  availableGroups.value = allGroups.value.filter(g =>
    !monitorGroups.value.some(mg => mg.groupId === g.id)
  )
  showAddForm.value = true
}

const confirmAddGroup = async () => {
  if (!newGroupForm.groupId) {
    ElMessage.warning('请选择监控任务')
    return
  }
  groupSaving.value = true
  try {
    await monitorApi.addToGroup(currentMonitor.value.id, {
      groupId: newGroupForm.groupId,
      sortOrder: newGroupForm.sortOrder,
      continueOnFail: newGroupForm.continueOnFail
    })
    ElMessage.success('添加成功')
    showAddForm.value = false
    const res = await monitorApi.getGroups(currentMonitor.value.id)
    monitorGroups.value = res.data || []
    availableGroups.value = allGroups.value.filter(g =>
      !monitorGroups.value.some(mg => mg.groupId === g.id)
    )
  } catch (error) {
    ElMessage.error('添加失败')
  } finally {
    groupSaving.value = false
  }
}

const saveGroupConfig = async (row) => {
  try {
    await monitorApi.updateInGroup(currentMonitor.value.id, row.groupId, {
      sortOrder: row.sortOrder,
      continueOnFail: row.continueOnFail
    })
  } catch (e) {
    ElMessage.error('更新失败')
  }
}

const removeFromGroup = async (row) => {
  try {
    await monitorApi.removeFromGroup(currentMonitor.value.id, row.groupId)
    ElMessage.success('移除成功')
    monitorGroups.value = monitorGroups.value.filter(mg => mg.groupId !== row.groupId)
    availableGroups.value = allGroups.value.filter(g =>
      !monitorGroups.value.some(mg => mg.groupId === g.id)
    )
  } catch (error) {
    ElMessage.error('移除失败')
  }
}

const showBatchGroupDialog = (mode) => {
  if (selectedIds.value.length === 0) return
  batchGroupMode.value = mode
  batchGroupTargetId.value = null
  if (mode === 'add') {
    batchMonitorList.value = selectedIds.value.map(id => {
      const m = monitors.value.find(m => m.id === id)
      return { id, name: m?.name || `#${id}`, sortOrder: 0, continueOnFail: true }
    })
  }
  loadAllGroups()
  batchGroupDialogVisible.value = true
}

const confirmBatchGroup = async () => {
  if (!batchGroupTargetId.value) {
    ElMessage.warning('请选择监控任务')
    return
  }
  groupSaving.value = true
  try {
    if (batchGroupMode.value === 'add') {
      for (const item of batchMonitorList.value) {
        await monitorApi.addToGroup(item.id, {
          groupId: batchGroupTargetId.value,
          sortOrder: item.sortOrder,
          continueOnFail: item.continueOnFail
        })
      }
      ElMessage.success(`已添加 ${batchMonitorList.value.length} 个监控项到任务`)
    } else {
      for (const id of selectedIds.value) {
        await monitorApi.removeFromGroup(id, batchGroupTargetId.value)
      }
      ElMessage.success(`已从任务移除 ${selectedIds.value.length} 个监控项`)
    }
    batchGroupDialogVisible.value = false
    clearSelection()
  } catch (error) {
    ElMessage.error('操作失败')
  } finally {
    groupSaving.value = false
  }
}
</script>

<style lang="scss" scoped>
.monitor-list {
  .header-actions { display: flex; gap: 8px; }

  .search-card {
    margin-bottom: 20px;
  }

  .batch-bar {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 12px;
    padding: 8px 12px;
    background: #f0f9eb;
    border-radius: 4px;
    font-size: 13px;
    color: #606266;
  }

  .add-group-form {
    display: flex;
    align-items: center;
    gap: 4px;
    margin-bottom: 12px;
    padding: 10px;
    background: #f5f7fa;
    border-radius: 4px;
  }

  .text-muted {
    color: #c0c4cc;
  }

  .id-link {
    color: #409eff;
    text-decoration: none;
    &:hover { text-decoration: underline; }
  }

  .el-pagination {
    margin-top: 20px;
    justify-content: flex-end;
  }
}
</style>
