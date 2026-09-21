<template>
  <div class="domain-list">
    <div class="page-header">
      <h2>域名证书</h2>
    </div>

    <el-alert type="info" :closable="false" show-icon style="margin-bottom: 16px">
      <template #title>
        <div>域名证书信息由监控项执行测试时自动汇集，无需手动添加。当监控项的 URL 涉及新域名时，系统会自动记录该域名、解析 IP 及 SSL 证书信息。</div>
        <div style="margin-top: 4px"><b>检测机制</b>：HTTPS 监控执行时自动检测证书；<b>同一域名两次检测间隔不小于 6 小时</b>（证书信息不会每次执行都刷新，可能短暂滞后）；域名/IP 记录更新间隔不小于 10 分钟。每天 9:00 AM 定时检查所有已启用告警的域名，评估是否需要发送告警通知。</div>
        <div style="margin-top: 4px; color: #909399">上述间隔可通过后端配置 <code>monitor.ssl-check-interval-minutes</code>、<code>monitor.domain-asset-update-interval-minutes</code> 调整。</div>
      </template>
    </el-alert>

    <!-- Search -->
    <el-card class="search-card">
      <el-form :inline="true" :model="searchForm">
        <el-form-item label="关键词">
          <el-input v-model="searchForm.keyword" placeholder="域名或IP" clearable />
        </el-form-item>
        <el-form-item label="SSL状态">
          <el-select v-model="searchForm.sslStatus" placeholder="全部" clearable style="width: 140px">
            <el-option v-for="item in SSL_STATUS" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="剩余天数">
          <el-select v-model="searchForm.maxRemainingDays" placeholder="全部" clearable style="width: 150px">
            <el-option label="15天内过期" :value="15" />
            <el-option label="30天内过期" :value="30" />
            <el-option label="已过期" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadDomains">搜索</el-button>
          <el-button @click="resetSearch">重置</el-button>
        </el-form-item>
        <el-form-item v-if="canEditDomain">
          <el-checkbox v-model="searchForm.starredOnly" @change="loadDomains">只看关注</el-checkbox>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- Table -->
    <el-card>
      <el-table :data="domains" stripe v-loading="loading">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="domain" label="域名" min-width="200" />
        <el-table-column prop="ipAddress" label="IP地址" width="150">
          <template #default="{ row }">
            <el-button type="primary" link @click="viewIpHistory(row)">{{ row.ipAddress || '-' }}</el-button>
          </template>
        </el-table-column>
        <el-table-column prop="port" label="端口" width="80" />
        <el-table-column prop="sslRemainingDays" label="SSL剩余天数" width="110">
          <template #default="{ row }">
            <span v-if="row.sslRemainingDays != null">
              <el-tag :type="row.sslRemainingDays <= 0 ? 'danger' : row.sslRemainingDays <= 30 ? 'warning' : 'success'" size="small">
                {{ row.sslRemainingDays }}天
              </el-tag>
            </span>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="sslNotAfter" label="过期时间" width="170">
          <template #default="{ row }">
            <span v-if="row.sslNotAfter">{{ formatTime(row.sslNotAfter) }}</span>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="sslStatus" label="证书状态" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.sslStatus" :type="getCertStatusTag(row.sslStatus)" size="small">
              {{ getCertStatusLabel(row.sslStatus) }}
            </el-tag>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="isAlive" label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.isAlive ? 'success' : 'danger'" size="small">
              {{ row.isAlive ? '存活' : '离线' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="sslAlertEnabled" label="SSL告警" width="80">
          <template #default="{ row }">
            <el-tag :type="row.sslAlertEnabled ? 'success' : 'info'" size="small">
              {{ row.sslAlertEnabled ? '已开启' : '未开启' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="firstSeenAt" label="首次发现" width="180">
          <template #default="{ row }">{{ formatTime(row.firstSeenAt) }}</template>
        </el-table-column>
        <el-table-column prop="lastSeenAt" label="最后发现" width="180">
          <template #default="{ row }">{{ formatTime(row.lastSeenAt) }}</template>
        </el-table-column>
        <el-table-column label="关注" width="60" v-if="canEditDomain">
          <template #default="{ row }">
            <el-button type="warning" link @click="toggleStar(row)">
              <el-icon :size="18">
                <StarFilled v-if="row.starred" />
                <Star v-else />
              </el-icon>
            </el-button>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="viewSsl(row)">查看证书</el-button>
            <el-button type="warning" link @click="openSslAlertConfig(row)" v-permission="'domain:edit'">SSL告警</el-button>
            <el-popconfirm title="确认删除?" @confirm="deleteDomain(row.id)">
              <template #reference>
                <el-button type="danger" link v-permission="'domain:delete'">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- IP History Dialog -->
    <el-dialog v-model="ipHistoryDialogVisible" title="历史IP记录" width="500px">
      <div v-if="ipHistoryList.length > 0">
        <el-table :data="ipHistoryList" stripe>
          <el-table-column type="index" label="序号" width="60" />
          <el-table-column prop="ipAddress" label="IP地址" />
        </el-table>
      </div>
      <div v-else class="empty-tip">暂无历史IP记录</div>
    </el-dialog>

    <!-- SSL Certificate Dialog -->
    <el-dialog v-model="sslDialogVisible" title="SSL证书信息" width="600px">
      <div v-for="(cert, index) in sslCertificates" :key="cert.id" class="ssl-cert-card">
        <h4 v-if="sslCertificates.length > 1" class="ssl-cert-title">证书 #{{ index + 1 }}</h4>
        <el-descriptions :column="1" border label-width="100px">
          <el-descriptions-item label="域名">{{ cert.domain }}</el-descriptions-item>
          <el-descriptions-item label="端口">{{ cert.port }}</el-descriptions-item>
          <el-descriptions-item label="颁发者">{{ cert.issuer || '-' }}</el-descriptions-item>
          <el-descriptions-item label="主体">{{ cert.subject || '-' }}</el-descriptions-item>
          <el-descriptions-item label="生效时间">{{ cert.notBefore || '-' }}</el-descriptions-item>
          <el-descriptions-item label="过期时间">{{ cert.notAfter || '-' }}</el-descriptions-item>
          <el-descriptions-item label="剩余天数">
            <el-tag :type="getSslStatusType(cert.remainingDays)" size="small">
              {{ cert.remainingDays }}天
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag v-if="cert.status" :type="getCertStatusTag(cert.status)" size="small">
              {{ getCertStatusLabel(cert.status) }}
            </el-tag>
            <el-tag v-else :type="cert.isValid ? 'success' : 'danger'" size="small">
              {{ cert.isValid ? '有效' : '已过期' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="指纹">
            <code class="fingerprint">{{ cert.fingerprint || '-' }}</code>
          </el-descriptions-item>
          <el-descriptions-item label="检查时间">{{ cert.checkedAt || '-' }}</el-descriptions-item>
        </el-descriptions>
        <el-divider v-if="index < sslCertificates.length - 1" />
      </div>
      <div v-if="sslCertificates.length === 0" class="empty-tip">暂无证书信息</div>
    </el-dialog>

    <!-- SSL Alert Config Dialog -->
    <el-dialog v-model="sslAlertDialogVisible" title="SSL证书告警配置" width="500px">
      <el-form :model="sslAlertForm" label-width="120px">
        <el-form-item label="启用告警">
          <el-switch v-model="sslAlertForm.sslAlertEnabled" />
        </el-form-item>
        <el-form-item label="告警渠道" v-if="sslAlertForm.sslAlertEnabled">
          <el-checkbox-group v-model="sslAlertForm.selectedAlertConfigIds">
            <el-checkbox v-for="config in alertConfigs" :key="config.id" :value="config.id">
              {{ config.name }}
              <el-tag :type="config.enabled ? 'success' : 'info'" size="small" style="margin-left: 4px">
                {{ config.enabled ? '启用' : '禁用' }}
              </el-tag>
            </el-checkbox>
          </el-checkbox-group>
        </el-form-item>
        <el-form-item label="提前告警天数" v-if="sslAlertForm.sslAlertEnabled">
          <el-input-number v-model="sslAlertForm.sslAlertDaysBefore" :min="1" :max="365" />
          <span style="margin-left: 8px">天</span>
        </el-form-item>
        <el-form-item label="到期前1天告警" v-if="sslAlertForm.sslAlertEnabled">
          <el-switch v-model="sslAlertForm.sslAlertOneDayBefore" />
          <span style="margin-left: 8px; color: #909399">证书到期前1天再次告警</span>
        </el-form-item>
        <el-form-item label="执行时告警" v-if="sslAlertForm.sslAlertEnabled">
          <el-switch v-model="sslAlertForm.sslAlertOnExecute" />
          <el-tooltip placement="right" :width="300">
            <template #content>
              开启后，监控项、监控任务执行时若发现该域名证书已过期或证书与域名不匹配（MISMATCH），将立即触发告警并写入告警记录，无需等待每日定时检查。
            </template>
            <el-icon style="margin-left: 8px; cursor: pointer; color: #909399; vertical-align: middle;"><QuestionFilled /></el-icon>
          </el-tooltip>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="sslAlertDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveSslAlertConfig" :loading="saving" v-permission="'domain:edit'">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Star, StarFilled } from '@element-plus/icons-vue'
import { domainApi, alertChannelApi } from '@/api'
import { formatTime } from '@/utils/format'
import { SSL_STATUS } from '@/constants/enums'
import { usePermission } from '@/composables/usePermission'

const route = useRoute()
const { hasButton } = usePermission()
const canEditDomain = computed(() => hasButton('domain:edit'))

const loading = ref(false)
const saving = ref(false)
const sslDialogVisible = ref(false)
const sslAlertDialogVisible = ref(false)
const ipHistoryDialogVisible = ref(false)
const domains = ref([])
const sslCertificates = ref([])
const alertConfigs = ref([])
const currentDomain = ref(null)
const ipHistoryList = ref([])

const searchForm = reactive({ keyword: '', sslStatus: null, maxRemainingDays: null, starredOnly: false })

const sslAlertForm = reactive({
  sslAlertEnabled: false,
  selectedAlertConfigIds: [],
  sslAlertDaysBefore: 30,
  sslAlertOneDayBefore: true,
  sslAlertOnExecute: false
})

onMounted(async () => {
  const q = route?.query || {}
  if (q.sslStatus) {
    searchForm.sslStatus = q.sslStatus
  }
  if (q.maxRemainingDays) {
    searchForm.maxRemainingDays = Number(q.maxRemainingDays)
  }
  await loadDomains()
  if (hasButton('alert-template:edit')) {
    await loadAlertConfigs()
  }
})

const loadDomains = async () => {
  loading.value = true
  try {
    const res = await domainApi.list({
      keyword: searchForm.keyword,
      sslStatus: searchForm.sslStatus,
      maxRemainingDays: searchForm.maxRemainingDays,
      starred: searchForm.starredOnly ? true : undefined
    })
    domains.value = res.data || []
  } catch (error) {
    ElMessage.error('加载失败')
  } finally {
    loading.value = false
  }
}

const loadAlertConfigs = async () => {
  try {
    const res = await alertChannelApi.list()
    alertConfigs.value = res.data || []
  } catch (error) {
    console.error('Failed to load alert configs:', error)
  }
}

const resetSearch = () => {
  searchForm.keyword = ''
  searchForm.sslStatus = null
  searchForm.maxRemainingDays = null
  searchForm.starredOnly = false
  loadDomains()
}

const toggleStar = async (row) => {
  try {
    const res = await domainApi.toggleStar(row.id)
    row.starred = res.data.starred
  } catch (error) {
    ElMessage.error('操作失败')
  }
}

const deleteDomain = async (id) => {
  try {
    await domainApi.delete(id)
    ElMessage.success('删除成功')
    loadDomains()
  } catch (error) {
    ElMessage.error('删除失败')
  }
}

const viewIpHistory = async (row) => {
  try {
    const res = await domainApi.getIpHistory(row.id)
    ipHistoryList.value = (res.data || []).map(ip => ({ ipAddress: ip }))
    ipHistoryDialogVisible.value = true
  } catch (error) {
    ElMessage.error('加载历史IP失败')
  }
}

const viewSsl = async (row) => {
  try {
    const res = await domainApi.getSsl(row.id)
    sslCertificates.value = res.data || []
    sslDialogVisible.value = true
  } catch (error) {
    ElMessage.error('加载证书信息失败')
  }
}

const openSslAlertConfig = (row) => {
  currentDomain.value = row
  sslAlertForm.sslAlertEnabled = row.sslAlertEnabled || false
  sslAlertForm.selectedAlertConfigIds = row.sslAlertConfigIds
    ? row.sslAlertConfigIds.split(',').map(Number)
    : []
  sslAlertForm.sslAlertDaysBefore = row.sslAlertDaysBefore || 30
  sslAlertForm.sslAlertOneDayBefore = row.sslAlertOneDayBefore !== false
  sslAlertForm.sslAlertOnExecute = row.sslAlertOnExecute || false
  sslAlertDialogVisible.value = true
}

const saveSslAlertConfig = async () => {
  if (!currentDomain.value) return

  saving.value = true
  try {
    await domainApi.update(currentDomain.value.id, {
      sslAlertEnabled: sslAlertForm.sslAlertEnabled,
      sslAlertConfigIds: sslAlertForm.selectedAlertConfigIds.join(','),
      sslAlertDaysBefore: sslAlertForm.sslAlertDaysBefore,
      sslAlertOneDayBefore: sslAlertForm.sslAlertOneDayBefore,
      sslAlertOnExecute: sslAlertForm.sslAlertOnExecute
    })
    ElMessage.success('保存成功')
    sslAlertDialogVisible.value = false
    loadDomains()
  } catch (error) {
    ElMessage.error('保存失败')
  } finally {
    saving.value = false
  }
}

const getSslStatusType = (days) => {
  if (days <= 0) return 'danger'
  if (days <= 30) return 'warning'
  return 'success'
}

const getCertStatusTag = (status) => SSL_STATUS.find(s => s.value === status)?.tagType || 'info'
const getCertStatusLabel = (status) => SSL_STATUS.find(s => s.value === status)?.label || status
</script>

<style lang="scss" scoped>
.domain-list {
  .search-card {
    margin-bottom: 20px;
  }
}

.ssl-cert-card {
  .ssl-cert-title {
    margin: 0 0 12px 0;
    color: #303133;
  }

  .fingerprint {
    font-size: 11px;
    word-break: break-all;
    background: #f5f7fa;
    padding: 2px 6px;
    border-radius: 4px;
  }
}

.empty-tip {
  text-align: center;
  color: #909399;
  padding: 20px;
}
</style>
