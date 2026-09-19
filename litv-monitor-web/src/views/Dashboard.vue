<template>
  <div class="dashboard">
    <div class="page-header">
      <div class="header-left">
        <h2>仪表盘</h2>
        <el-tooltip placement="right" :width="200">
          <template #content>
            <div>自动刷新间隔:</div>
            <div style="margin-top: 4px">当前: {{ autoRefreshInterval ? autoRefreshInterval + '秒' : '关闭' }}</div>
          </template>
          <el-popover placement="bottom-start" :width="160" trigger="click">
            <template #reference>
              <el-icon class="refresh-settings-icon" :class="{ active: autoRefreshInterval > 0 }"><Refresh /></el-icon>
            </template>
            <div style="font-size: 13px; font-weight: 600; margin-bottom: 8px">自动刷新</div>
            <el-radio-group v-model="autoRefreshInterval" @change="onAutoRefreshChange" class="auto-refresh-radios">
              <el-radio :value="0">关闭</el-radio>
              <el-radio :value="10">每 10 秒</el-radio>
              <el-radio :value="30">每 30 秒</el-radio>
              <el-radio :value="180">每 3 分钟</el-radio>
            </el-radio-group>
          </el-popover>
        </el-tooltip>
      </div>
      <div class="header-actions">
        <el-button type="danger" size="small" @click="vulnVisible = true">
          <el-icon><Warning /></el-icon> 漏洞情报
        </el-button>
        <el-button type="warning" size="small" @click="$router.push('/reminder')">
          <el-icon><AlarmClock /></el-icon> 周期提醒
          <el-badge v-if="reminderBadgeCount > 0" :value="reminderBadgeCount"
            type="danger" class="reminder-badge" />
        </el-button>
        <el-button type="default" size="small" @click="openMemo">
          <el-icon><Notebook /></el-icon> 备忘录
        </el-button>
        <a href="/status" target="_blank" class="status-link">
          <el-icon><Link /></el-icon> 状态公示页
        </a>
      </div>
    </div>

    <!-- Overview Cards -->
    <el-row :gutter="20" class="overview-cards">
      <el-col :xs="12" :sm="12" :md="6" :lg="6">
        <el-card shadow="hover">
          <div class="stat-card-header">
            <div class="stat-icon" style="background: rgba(64, 158, 255, 0.1)">
              <el-icon :size="22" style="color: #409eff"><Connection /></el-icon>
            </div>
            <div class="stat-title">监控项</div>
            <span class="stat-sub">共{{ overview.monitorEnabledCount + overview.monitorDisabledCount || 0 }}个</span>
          </div>
          <div class="stat-grid">
            <div class="stat-item clickable" @click="goMonitor(true)">
              <div class="stat-value" style="color: #67c23a">{{ overview.monitorEnabledCount || 0 }}</div>
              <div class="stat-label">启用</div>
            </div>
            <div class="stat-item clickable" @click="goMonitor(false)">
              <div class="stat-value" style="color: #909399">{{ overview.monitorDisabledCount || 0 }}</div>
              <div class="stat-label">禁用</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="12" :md="6" :lg="6">
        <el-card shadow="hover">
          <div class="stat-card-header">
            <div class="stat-icon" style="background: rgba(103, 194, 58, 0.1)">
              <el-icon :size="22" style="color: #67c23a"><Folder /></el-icon>
            </div>
            <div class="stat-title">监控任务</div>
            <span class="stat-sub">共{{ overview.groupEnabledCount + overview.groupDisabledCount || 0 }}个</span>
          </div>
          <div class="stat-grid">
            <div class="stat-item clickable" @click="goGroup(true)">
              <div class="stat-value" style="color: #67c23a">{{ overview.groupEnabledCount || 0 }}</div>
              <div class="stat-label">启用</div>
            </div>
            <div class="stat-item clickable" @click="goGroup(false)">
              <div class="stat-value" style="color: #909399">{{ overview.groupDisabledCount || 0 }}</div>
              <div class="stat-label">禁用</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="12" :md="6" :lg="6">
        <el-card shadow="hover">
          <div class="stat-card-header">
            <div class="stat-icon" style="background: rgba(230, 162, 60, 0.1)">
              <el-icon :size="22" style="color: #e6a23c"><Timer /></el-icon>
            </div>
            <div class="stat-title">响应超时</div>
            <span class="stat-sub">去重监控数</span>
          </div>
          <div class="stat-grid">
            <div class="stat-item clickable" @click="goAlertLog(10)">
              <div class="stat-value" style="color: #f56c6c">{{ overview.responseTimeoutStats?.['10m'] || 0 }}</div>
              <div class="stat-label">近10分钟</div>
            </div>
            <div class="stat-item clickable" @click="goAlertLog(60)">
              <div class="stat-value" style="color: #e6a23c">{{ overview.responseTimeoutStats?.['1h'] || 0 }}</div>
              <div class="stat-label">近1小时</div>
            </div>
            <div class="stat-item clickable" @click="goAlertLog(360)">
              <div class="stat-value" style="color: #909399">{{ overview.responseTimeoutStats?.['6h'] || 0 }}</div>
              <div class="stat-label">近6小时</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="12" :md="6" :lg="6">
        <el-card shadow="hover">
          <div class="stat-card-header">
            <div class="stat-icon" style="background: rgba(245, 108, 108, 0.1)">
              <el-icon :size="22" style="color: #f56c6c"><Warning /></el-icon>
            </div>
            <div class="stat-title">SSL证书异常</div>
            <span class="stat-sub">域名数</span>
          </div>
          <div class="stat-grid">
            <div class="stat-item clickable" @click="goDomain(null, 30)">
              <div class="stat-value" style="color: #e6a23c">{{ overview.sslExpiringCount || 0 }}</div>
              <div class="stat-label">即将过期</div>
            </div>
            <div class="stat-item clickable" @click="goDomain('EXPIRED')">
              <div class="stat-value" style="color: #f56c6c">{{ overview.sslExpiredCount || 0 }}</div>
              <div class="stat-label">已过期</div>
            </div>
            <div class="stat-item clickable" @click="goDomain('MISMATCH')">
              <div class="stat-value" style="color: #f56c6c">{{ overview.sslMismatchCount || 0 }}</div>
              <div class="stat-label">证书错误</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- Schema Check Stats -->
    <el-row :gutter="20" class="overview-cards">
      <el-col :xs="24" :sm="24" :md="12" :lg="12">
        <el-card shadow="hover">
          <div class="stat-card-header">
            <div class="stat-icon" style="background: rgba(103, 194, 58, 0.1)">
              <el-icon :size="22" style="color: #67c23a"><CircleCheck /></el-icon>
            </div>
            <div class="stat-title">可用性统计</div>
            <el-select v-model="uptimeHours" size="small" style="width: 100px" @change="loadUptimeStats">
              <el-option label="近1小时" :value="1" />
              <el-option label="近6小时" :value="6" />
              <el-option label="近24小时" :value="24" />
              <el-option label="近7天" :value="168" />
              <el-option label="近30天" :value="720" />
            </el-select>
          </div>
          <div class="stat-grid">
            <div class="stat-item">
              <div class="stat-value" :style="{ color: uptimeStats.uptimePercent >= 99 ? '#67c23a' : uptimeStats.uptimePercent >= 95 ? '#e6a23c' : '#f56c6c' }">
                {{ uptimeStats.uptimePercent || 0 }}%
              </div>
              <div class="stat-label">可用率</div>
            </div>
            <div class="stat-item clickable" @click="goLogUptime('SUCCESS')">
              <div class="stat-value" style="color: #67c23a">{{ uptimeStats.successCount || 0 }}</div>
              <div class="stat-label">成功</div>
            </div>
            <div class="stat-item clickable" @click="goLogUptime('FAIL')">
              <div class="stat-value" style="color: #f56c6c">{{ uptimeStats.failCount || 0 }}</div>
              <div class="stat-label">失败</div>
            </div>
            <div class="stat-item clickable" @click="goLogUptime('')">
              <div class="stat-value" style="color: #909399">{{ uptimeStats.total || 0 }}</div>
              <div class="stat-label">总次数</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="24" :md="12" :lg="12">
        <el-card shadow="hover">
          <div class="stat-card-header">
            <div class="stat-icon" style="background: rgba(230, 162, 60, 0.1)">
              <el-icon :size="22" style="color: #e6a23c"><Document /></el-icon>
            </div>
            <div class="stat-title">Schema 检测</div>
            <el-select v-model="schemaHours" size="small" style="width: 100px" @change="loadSchemaChanges">
              <el-option label="近1小时" :value="1" />
              <el-option label="近6小时" :value="6" />
              <el-option label="近24小时" :value="24" />
              <el-option label="近7天" :value="168" />
              <el-option label="近30天" :value="720" />
            </el-select>
          </div>
          <div class="stat-grid">
            <div class="stat-item clickable" @click="goLogSchema(schemaHours, 'checked')">
              <div class="stat-value" style="color: #409eff">{{ schemaChanges.checkedCount || 0 }}</div>
              <div class="stat-label">已检查</div>
            </div>
            <div class="stat-item clickable" @click="goLogSchema(schemaHours, 'hasChange')">
              <div class="stat-value" style="color: #e6a23c">{{ schemaChanges.hasChangeCount || 0 }}</div>
              <div class="stat-label">检出变更</div>
            </div>
            <div class="stat-item clickable" @click="goLogSchema(schemaHours, 'noChange')">
              <div class="stat-value" style="color: #67c23a">{{ schemaChanges.noChangeCount || 0 }}</div>
              <div class="stat-label">无变更</div>
            </div>
            <div class="stat-item clickable" @click="goLogSchema(schemaHours, 'BREAKING')">
              <div class="stat-value" style="color: #f56c6c">{{ schemaChanges.breakingCount || 0 }}</div>
              <div class="stat-label">Break</div>
            </div>
          </div>
          <div v-if="schemaChanges.breakingCount > 0" class="breaking-banner clickable" @click="goLogSchema(schemaHours, 'BREAKING')">
            <el-icon style="color: #f56c6c; margin-right: 4px"><WarningFilled /></el-icon>
            <span style="color: #f56c6c; font-weight: 600">{{ schemaChanges.breakingCount }} 次破坏性变更</span>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- Charts Row -->
    <el-row :gutter="20" class="chart-row">
      <el-col :span="16">
        <el-card header="告警趋势 (24小时)">
          <div ref="alertTrendChart" class="chart-container"></div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card header="告警类型分布 (24小时)">
          <div ref="alertTypeChart" class="chart-container"></div>
        </el-card>
      </el-col>
    </el-row>

    <!-- URL Response Time Trend -->
    <el-row :gutter="20" class="chart-row">
      <el-col :span="24">
        <el-card>
          <template #header>
            <div class="card-header">
              <span>URL 响应时间趋势</span>
              <div class="url-chart-controls">
                <el-segmented v-model="urlGranularity" :options="[
                  { label: '分钟', value: 'minute' },
                  { label: '小时', value: 'hour' }
                ]" size="small" style="margin-right: 8px" />
                <el-input
                  v-model="urlSearchKey"
                  placeholder="搜索URL"
                  clearable
                  size="small"
                  style="width: 180px; margin-right: 8px"
                  prefix-icon="Search"
                />
                <el-select v-model="urlShowMode" size="small" style="width: 130px">
                  <el-option label="最慢 Top10" value="slowest" />
                  <el-option label="最快 Top10" value="fastest" />
                  <el-option label="全部" value="all" />
                </el-select>
              </div>
            </div>
          </template>
          <div ref="urlTrendChart" class="chart-container-large"></div>
        </el-card>
      </el-col>
    </el-row>

    <!-- Recent Logs -->
    <el-card header="最近执行记录">
      <el-table :data="recentLogs" stripe style="width: 100%">
        <el-table-column prop="monitorId" label="监控ID" width="80" />
        <el-table-column label="监控名" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">
            <el-tag size="small" :type="row.groupId ? 'warning' : 'success'" style="margin-right: 4px">
              {{ row.groupId ? '任务' : '监控项' }}
            </el-tag>
            {{ row.monitorName }}
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)" size="small">
              {{ row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="statusCode" label="状态码" width="80" />
        <el-table-column prop="responseTime" label="响应时间(ms)" width="120" />
        <el-table-column prop="executedAt" label="执行时间" width="180">
          <template #default="{ row }">{{ formatTime(row.executedAt) }}</template>
        </el-table-column>
        <el-table-column prop="errorMessage" label="错误信息" show-overflow-tooltip />
      </el-table>
    </el-card>

    <!-- Memo Dialog -->
    <el-dialog v-model="memoVisible" title="备忘录" width="600px" :close-on-click-modal="false">
      <el-input v-model="memoContent" type="textarea" :rows="15" placeholder="在此记录你的备忘内容..."
        @keydown.ctrl.s.prevent="saveMemo" />
      <template #footer>
        <span style="color: #909399; font-size: 12px; margin-right: auto;">Ctrl+S 保存</span>
        <el-button @click="memoVisible = false">取消</el-button>
        <el-button type="primary" @click="saveMemo" :loading="memoSaving">保存</el-button>
      </template>
    </el-dialog>

    <!-- Vulnerability Intelligence Dialog -->
    <el-dialog v-model="vulnVisible" title="漏洞情报" width="920px" top="6vh">
      <div class="vuln-hint">主流漏洞情报与威胁情报站点导航，点击卡片或条目将在新标签页打开。</div>

      <div class="vuln-section-title">
        <el-icon><Star /></el-icon> 主流高价值情报站
      </div>
      <el-row :gutter="12">
        <el-col :xs="24" :sm="12" :md="8" v-for="site in vulnTopSites" :key="site.url">
          <a :href="site.url" target="_blank" rel="noopener noreferrer" class="vuln-card">
            <div class="vuln-card-head">
              <span class="vuln-card-name">{{ site.name }}</span>
              <el-tag v-if="site.tag" size="small" :type="site.tag === '国内' ? 'success' : 'info'" effect="plain">{{ site.tag }}</el-tag>
            </div>
            <div class="vuln-card-desc">{{ site.desc }}</div>
            <div class="vuln-card-url">{{ site.url }}</div>
          </a>
        </el-col>
      </el-row>

      <div class="vuln-section-title" style="margin-top: 20px;">
        <el-icon><Link /></el-icon> 其他参考情报站
      </div>
      <div class="vuln-list">
        <a v-for="site in vulnRefSites" :key="site.url" :href="site.url" target="_blank" rel="noopener noreferrer" class="vuln-list-item">
          <span class="vuln-list-name">{{ site.name }}</span>
          <span class="vuln-list-desc">{{ site.desc }}</span>
          <el-icon class="vuln-list-arrow"><TopRight /></el-icon>
        </a>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import * as echarts from 'echarts/core'
import { BarChart, PieChart, LineChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'

echarts.use([BarChart, PieChart, LineChart, GridComponent, TooltipComponent, LegendComponent, CanvasRenderer])
import { dashboardApi, logApi, memoApi, reminderApi } from '@/api'
import { ElMessage } from 'element-plus'
import { formatTime } from '@/utils/format'

const router = useRouter()

const overview = ref({})
const recentLogs = ref([])
const memoVisible = ref(false)
const memoContent = ref('')
const memoSaving = ref(false)
const vulnVisible = ref(false)
const autoRefreshInterval = ref(Number(localStorage.getItem('dashboard_autoRefresh')) || 0)
let autoRefreshTimer = null

const vulnTopSites = [
  { name: '阿里云漏洞库 (AVD)', url: 'https://avd.aliyun.com/', desc: '阿里云官方漏洞库，中文漏洞详情与修复建议', tag: '国内' },
  { name: '国家信息安全漏洞共享平台 (CNVD)', url: 'https://www.cnvd.org.cn/', desc: '国家漏洞共享平台，权威漏洞收录与通报', tag: '国内' },
  { name: '国家信息安全漏洞库 (CNNVD)', url: 'https://www.cnnvd.org.cn/', desc: '中国国家信息安全漏洞库', tag: '国内' },
  { name: 'CVE Program', url: 'https://www.cve.org/', desc: '全球通用漏洞编号 CVE 官方来源', tag: '国际' },
  { name: 'NVD (NIST)', url: 'https://nvd.nist.gov/', desc: '美国国家漏洞数据库，CVSS 评分权威', tag: '国际' },
  { name: '奇安信威胁情报中心', url: 'https://ti.qianxin.com/', desc: '威胁情报与漏洞预警', tag: '国内' }
]

const vulnRefSites = [
  { name: 'Exploit-DB', url: 'https://www.exploit-db.com/', desc: '公开 PoC / EXP 数据库' },
  { name: 'GitHub Advisory Database', url: 'https://github.com/advisories', desc: '开源依赖漏洞公告' },
  { name: 'OSV (Open Source Vulnerabilities)', url: 'https://osv.dev/', desc: '开源组件漏洞聚合' },
  { name: 'CISA Known Exploited Vulnerabilities', url: 'https://www.cisa.gov/known-exploited-vulnerabilities-catalog', desc: '已被在野利用漏洞目录' },
  { name: 'Snyk Vulnerability DB', url: 'https://security.snyk.io/', desc: '开源组件漏洞库' },
  { name: '腾讯安全威胁情报中心', url: 'https://ti.tencent.com/', desc: '腾讯威胁情报与漏洞预警' },
  { name: '微步在线威胁情报', url: 'https://x.threatbook.com/', desc: '威胁情报社区' },
  { name: '知道创宇 Seebug', url: 'https://www.seebug.org/', desc: '漏洞平台与 PoC' },
  { name: '360 漏洞库', url: 'https://vul.360.net/', desc: '360 安全漏洞库' },
  { name: 'OSCS 开源软件供应链安全', url: 'https://www.oscs1024.com/', desc: '开源组件安全情报' },
  { name: 'NVDB 网络安全威胁和漏洞信息共享平台', url: 'https://www.nvdb.org.cn/', desc: '工信部漏洞共享平台' },
  { name: 'FreeBuf', url: 'https://www.freebuf.com/', desc: '安全资讯与漏洞分析' },
  { name: '安全客', url: 'https://www.anquanke.com/', desc: '安全资讯与漏洞分析' },
  { name: 'VulDB', url: 'https://vuldb.com/', desc: '综合漏洞数据库' },
  { name: 'Packet Storm', url: 'https://packetstormsecurity.com/', desc: '漏洞与安全工具' }
]
const reminderSummary = ref({ overdueCount: 0, todayCount: 0, upcomingCount: 0 })
const reminderBadgeCount = computed(() => (reminderSummary.value.overdueCount || 0) + (reminderSummary.value.todayCount || 0) + (reminderSummary.value.upcomingCount || 0))
const alertTrendChart = ref(null)
const alertTypeChart = ref(null)
const urlTrendChart = ref(null)
const uptimeHours = ref(Number(localStorage.getItem('dashboard_uptimeHours')) || 24)
const uptimeStats = ref({})
const schemaHours = ref(Number(localStorage.getItem('dashboard_schemaHours')) || 24)
const schemaChanges = ref({})

const openMemo = async () => {
  memoVisible.value = true
  try {
    const res = await memoApi.get()
    memoContent.value = res.data?.content || ''
  } catch { memoContent.value = '' }
}

const saveMemo = async () => {
  memoSaving.value = true
  try {
    await memoApi.save(memoContent.value)
    ElMessage.success('备忘录已保存')
    memoVisible.value = false
  } catch (e) {
    ElMessage.error(e.message || '保存失败')
  } finally {
    memoSaving.value = false
  }
}

watch(uptimeHours, (v) => localStorage.setItem('dashboard_uptimeHours', String(v)))
watch(schemaHours, (v) => localStorage.setItem('dashboard_schemaHours', String(v)))

let alertTrendChartInstance = null
let alertTypeChartInstance = null
let urlTrendChartInstance = null

onMounted(async () => {
  initCharts()
  await Promise.all([loadOverview(), loadUrlTrend(), loadRecentLogs(), loadUptimeStats(), loadSchemaChanges(), loadReminderSummary()])
  startAutoRefresh()
})

const startAutoRefresh = () => {
  stopAutoRefresh()
  if (autoRefreshInterval.value > 0) {
    autoRefreshTimer = setInterval(async () => {
      await Promise.all([loadOverview(), loadUrlTrend(), loadRecentLogs(), loadUptimeStats(), loadSchemaChanges(), loadReminderSummary()])
    }, autoRefreshInterval.value * 1000)
  }
}

const stopAutoRefresh = () => {
  if (autoRefreshTimer) {
    clearInterval(autoRefreshTimer)
    autoRefreshTimer = null
  }
}

const onAutoRefreshChange = (val) => {
  localStorage.setItem('dashboard_autoRefresh', String(val))
  startAutoRefresh()
}

const loadReminderSummary = async () => {
  try {
    const res = await reminderApi.dashboard()
    reminderSummary.value = res.data || {}
  } catch {
    // ignore
  }
}

const handleResize = () => {
  alertTrendChartInstance?.resize()
  alertTypeChartInstance?.resize()
  urlTrendChartInstance?.resize()
}

onUnmounted(() => {
  stopAutoRefresh()
  window.removeEventListener('resize', handleResize)
  alertTrendChartInstance?.dispose()
  alertTypeChartInstance?.dispose()
  urlTrendChartInstance?.dispose()
})

// Navigation helpers
const goMonitor = (enabled) => {
  router.push({ path: '/monitor', query: { enabled: String(enabled) } })
}
const goGroup = (enabled) => {
  router.push({ path: '/group', query: { enabled: String(enabled) } })
}
const goAlertLog = (minutes) => {
  const end = new Date()
  const start = new Date(end.getTime() - minutes * 60 * 1000)
  router.push({ path: '/alert-log', query: { triggerType: 'RESPONSE_TIME', startTime: formatTime(start), endTime: formatTime(end) } })
}
const goDomain = (sslStatus, maxRemainingDays) => {
  const query = {}
  if (sslStatus) query.sslStatus = sslStatus
  if (maxRemainingDays) query.maxRemainingDays = String(maxRemainingDays)
  router.push({ path: '/domain', query })
}
const goLogSchema = (hours, filter) => {
  const query = {}
  if (hours) query.hours = String(hours)
  if (filter) query.schemaFilter = filter
  router.push({ path: '/log', query })
}
const goLogUptime = (status) => {
  const query = { hours: String(uptimeHours.value) }
  if (status) query.status = status
  router.push({ path: '/log', query })
}

const loadOverview = async () => {
  try {
    const res = await dashboardApi.overview()
    overview.value = res.data
    updateAlertTrendChart()
    updateAlertTypeChart()
  } catch (error) {
    console.error('Failed to load overview:', error)
  }
}

const loadUptimeStats = async () => {
  try {
    const res = await dashboardApi.uptime({ hours: uptimeHours.value })
    uptimeStats.value = res.data || {}
  } catch (error) {
    console.error('Failed to load uptime stats:', error)
  }
}

const loadSchemaChanges = async () => {
  try {
    const res = await dashboardApi.schemaChanges({ hours: schemaHours.value })
    schemaChanges.value = res.data || {}
  } catch (error) {
    console.error('Failed to load schema changes:', error)
  }
}

const loadUrlTrend = async () => {
  try {
    const res = await dashboardApi.urlTrend(urlGranularity.value)
    urlTrendData.value = res.data || []
    updateUrlTrendChart()
  } catch (error) {
    console.error('Failed to load URL trend:', error)
  }
}

const ALERT_TYPE_LABELS = { 'FAIL': '监控失败', 'RESPONSE_TIME': '响应超时', 'GROUP_FAIL': '任务失败', 'SSL_CERT': 'SSL证书', 'SCHEMA_CHANGE': 'Schema变更' }
const ALERT_TYPE_COLORS = { 'FAIL': '#f56c6c', 'RESPONSE_TIME': '#e6a23c', 'GROUP_FAIL': '#409eff', 'SSL_CERT': '#909399', 'SCHEMA_CHANGE': '#e6a23c' }

const updateAlertTrendChart = () => {
  if (!alertTrendChartInstance) return
  const trend = overview.value.alertTrend || []
  if (trend.length === 0) {
    alertTrendChartInstance.setOption({
      tooltip: { trigger: 'axis' },
      xAxis: { type: 'category', data: ['暂无数据'] },
      yAxis: { type: 'value', name: '次' },
      series: [{ data: [0], type: 'bar', itemStyle: { color: '#409eff' } }]
    })
    return
  }
  alertTrendChartInstance.setOption({
    tooltip: {
      trigger: 'axis',
      formatter: (params) => {
        const p = params[0]
        return `${p.name}<br/>告警次数: <b>${p.value} 次</b>`
      }
    },
    xAxis: {
      type: 'category',
      data: trend.map(t => t.time),
      axisLabel: {
        rotate: trend.length > 12 ? 30 : 0,
        fontSize: 11,
        formatter: (val) => {
          const parts = val.split(' ')
          return parts.length === 2 ? `${parts[0]}\n${parts[1]}` : val
        }
      },
      axisTick: { alignWithLabel: true }
    },
    yAxis: { type: 'value', name: '次', min: 0 },
    series: [{
      data: trend.map(t => t.count),
      type: 'bar',
      itemStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: '#f56c6c' },
          { offset: 1, color: '#f89898' }
        ]),
        borderRadius: [4, 4, 0, 0]
      },
      barMaxWidth: 32
    }],
    grid: { left: '3%', right: '4%', bottom: '12%', containLabel: true }
  })
}

const updateAlertTypeChart = () => {
  if (!alertTypeChartInstance) return
  const dist = overview.value.alertTypeDistribution || []
  const colorMap = { 'FAIL': '#f56c6c', 'RESPONSE_TIME': '#e6a23c', 'GROUP_FAIL': '#409eff', 'SSL_CERT': '#909399', 'SCHEMA_CHANGE': '#e6a23c' }
  if (dist.length === 0) {
    alertTypeChartInstance.setOption({
      tooltip: { trigger: 'item' },
      series: [{
        type: 'pie',
        radius: ['40%', '70%'],
        data: [{ value: 1, name: '暂无告警', itemStyle: { color: '#ebeef5' } }],
        label: { show: true, formatter: '{b}' }
      }]
    })
    return
  }
  const data = dist.map(d => ({
    value: d.count,
    name: ALERT_TYPE_LABELS[d.type] || d.type,
    itemStyle: { color: colorMap[d.type] || '#909399' }
  }))
  alertTypeChartInstance.setOption({
    tooltip: {
      trigger: 'item',
      formatter: (p) => `${p.name}<br/>次数: <b>${p.value}</b> (${p.percent}%)`
    },
    legend: {
      orient: 'horizontal',
      bottom: 0,
      textStyle: { fontSize: 12 }
    },
    series: [{
      type: 'pie',
      radius: ['40%', '70%'],
      data,
      emphasis: {
        itemStyle: { shadowBlur: 10, shadowOffsetX: 0, shadowColor: 'rgba(0, 0, 0, 0.5)' }
      },
      label: {
        show: true,
        formatter: '{b}: {c}次'
      }
    }]
  })
}

const CHART_COLORS = ['#409eff', '#67c23a', '#e6a23c', '#f56c6c', '#909399', '#b37feb', '#36cfc9', '#ff85c0', '#ffc53d', '#73d13d']

const urlShowMode = ref('slowest')
const urlSearchKey = ref('')
const urlGranularity = ref('minute')
const urlTrendData = ref([])

watch([urlGranularity], () => {
  loadUrlTrend()
})

watch([urlShowMode, urlSearchKey], () => {
  updateUrlTrendChart()
})

const updateUrlTrendChart = () => {
  if (!urlTrendChartInstance) return
  const raw = urlTrendData.value || []
  if (raw.length === 0) {
    urlTrendChartInstance.setOption({
      tooltip: { trigger: 'axis' },
      xAxis: { type: 'category', data: ['暂无数据'] },
      yAxis: { type: 'value', name: 'ms' },
      series: [],
      legend: { show: false }
    })
    return
  }

  // Group by URL, compute average response time per URL
  const urlMap = {}
  const timeSet = new Set()
  raw.forEach(item => {
    timeSet.add(item.time)
    if (!urlMap[item.url]) urlMap[item.url] = { sum: 0, count: 0, points: {} }
    urlMap[item.url].points[item.time] = item.avgResponseTime
    urlMap[item.url].sum += item.avgResponseTime
    urlMap[item.url].count++
  })

  let urls = Object.keys(urlMap).map(url => ({
    url,
    avg: Math.round(urlMap[url].sum / urlMap[url].count)
  }))

  // Apply search filter
  const search = (urlSearchKey.value || '').trim().toLowerCase()
  if (search) {
    urls = urls.filter(u => u.url.toLowerCase().includes(search))
  }

  // Sort and limit by mode
  if (urlShowMode.value === 'slowest') {
    urls.sort((a, b) => b.avg - a.avg)
    urls = urls.slice(0, 10)
  } else if (urlShowMode.value === 'fastest') {
    urls.sort((a, b) => a.avg - b.avg)
    urls = urls.slice(0, 10)
  }
  // 'all' -> keep all

  const selectedUrls = urls.map(u => u.url)
  const times = Array.from(timeSet).sort()
  const showLegend = selectedUrls.length <= 20

  const truncate = (s, max = 50) => s.length > max ? s.substring(0, max) + '...' : s

  const series = selectedUrls.map((url, i) => ({
    name: truncate(url),
    type: 'line',
    smooth: true,
    connectNulls: true,
    symbol: 'circle',
    symbolSize: 4,
    itemStyle: { color: CHART_COLORS[i % CHART_COLORS.length] },
    data: times.map(t => urlMap[url].points[t] ?? null)
  }))

  urlTrendChartInstance.setOption({
    tooltip: {
      trigger: 'axis',
      confine: true,
      formatter: (params) => {
        if (!params || params.length === 0) return ''
        let tip = `<b>${params[0].name}</b><br/>`
        params.sort((a, b) => (b.value ?? 0) - (a.value ?? 0))
        const show = params.slice(0, 10)
        show.forEach(p => {
          if (p.value != null) {
            tip += `<span style="display:inline-block;width:10px;height:10px;border-radius:50%;background:${p.color};margin-right:6px"></span>${truncate(p.seriesName, 45)}: <b>${p.value}ms</b><br/>`
          }
        })
        if (params.length > 10) tip += `...及其他 ${params.length - 10} 个<br/>`
        return tip
      }
    },
    legend: {
      show: showLegend,
      type: 'scroll',
      bottom: 0,
      textStyle: { fontSize: 11 },
      pageTextStyle: { fontSize: 11 },
      pageIconColor: '#409eff',
      pageIconInactiveColor: '#ccc'
    },
    grid: { left: '3%', right: '4%', bottom: showLegend ? '14%' : '5%', containLabel: true },
    xAxis: {
      type: 'category',
      data: times,
      axisLabel: {
        rotate: times.length > 12 ? 30 : 0,
        fontSize: 11,
        formatter: (val) => {
          const parts = val.split(' ')
          return parts.length === 2 ? `${parts[0]}\n${parts[1]}` : val
        }
      }
    },
    yAxis: { type: 'value', name: 'ms', min: 0 },
    series
  }, true)
}

const loadRecentLogs = async () => {
  try {
    const res = await logApi.list({ page: 1, size: 10 })
    recentLogs.value = res.data?.records || []
  } catch (error) {
    console.error('Failed to load logs:', error)
  }
}

const initCharts = () => {
  if (alertTrendChart.value) {
    alertTrendChartInstance = echarts.init(alertTrendChart.value)
  }

  if (alertTypeChart.value) {
    alertTypeChartInstance = echarts.init(alertTypeChart.value)
  }

  if (urlTrendChart.value) {
    urlTrendChartInstance = echarts.init(urlTrendChart.value)
  }
  window.addEventListener('resize', handleResize)
}

const getStatusType = (status) => {
  const types = {
    'SUCCESS': 'success',
    'FAIL': 'danger',
    'ERROR': 'danger',
    'TIMEOUT': 'warning'
  }
  return types[status] || 'info'
}
</script>

<style lang="scss" scoped>
.dashboard {
  .page-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 20px;

    .header-left {
      display: flex;
      align-items: center;
      gap: 8px;
    }

    h2 {
      margin: 0;
    }

    .refresh-settings-icon {
      font-size: 18px;
      color: #909399;
      cursor: pointer;
      transition: color 0.2s;
      &:hover { color: #409eff; }
      &.active { color: #409eff; }
    }

    .auto-refresh-radios {
      display: flex;
      flex-direction: column;
      gap: 8px;
      .el-radio { margin-right: 0; }
    }

    .status-link {
      font-size: 13px;
      color: #67c23a;
      text-decoration: none;
      display: inline-flex;
      align-items: center;
      gap: 4px;
      padding: 4px 10px;
      border: 1px solid #67c23a;
      border-radius: 4px;
      transition: all 0.2s;

      &:hover {
        background: #67c23a;
        color: #fff;
      }
    }

    .header-actions {
      display: flex;
      align-items: center;
      gap: 10px;
    }
  }

  .overview-cards {
    margin-bottom: 20px;
  }

  .card-content {
    display: flex;
    align-items: center;
    gap: 16px;
  }

  .card-icon {
    width: 56px;
    height: 56px;
    border-radius: 12px;
    display: flex;
    align-items: center;
    justify-content: center;
  }

  .card-info {
    .card-value {
      font-size: 28px;
      font-weight: 600;
      color: #303133;
    }

    .card-label {
      font-size: 14px;
      color: #909399;
      margin-top: 4px;
    }
  }

  .stat-card-header {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 12px;

    .stat-icon {
      width: 36px;
      height: 36px;
      border-radius: 8px;
      display: flex;
      align-items: center;
      justify-content: center;
      flex-shrink: 0;
    }

    .stat-title {
      font-size: 15px;
      font-weight: 600;
      color: #303133;
    }

    .stat-sub {
      font-size: 12px;
      color: #c0c4cc;
      margin-left: auto;
    }
  }

  .stat-grid {
    display: flex;

    .stat-item {
      flex: 1;
      text-align: center;

      .stat-value {
        font-size: 26px;
        font-weight: 600;
      }

      .stat-label {
        font-size: 12px;
        color: #909399;
        margin-top: 2px;
      }

      &.clickable {
        cursor: pointer;
        border-radius: 6px;
        padding: 6px 0;
        transition: background 0.2s;

        &:hover {
          background: rgba(0, 0, 0, 0.04);
        }

        .stat-value {
          text-decoration: underline;
          text-decoration-style: dotted;
          text-underline-offset: 3px;
        }
      }
    }
  }

  .breaking-banner {
    display: flex;
    align-items: center;
    justify-content: center;
    margin-top: 12px;
    padding: 6px 12px;
    background: rgba(245, 108, 108, 0.08);
    border-radius: 6px;
    font-size: 13px;
  }

  .chart-row {
    margin-bottom: 20px;
  }

  .chart-container {
    height: 300px;
  }

  .chart-container-large {
    height: 380px;
  }

  .card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
  }

  .url-chart-controls {
    display: flex;
    align-items: center;
    gap: 4px;
  }

  .reminder-badge {
    margin-left: 6px;
  }
}

.vuln-hint {
  font-size: 12px;
  color: #909399;
  margin-bottom: 14px;
}

.vuln-section-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 12px;
}

.vuln-card {
  display: block;
  padding: 14px;
  margin-bottom: 12px;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  text-decoration: none;
  background: #fff;
  transition: all 0.2s;

  &:hover {
    border-color: #409eff;
    box-shadow: 0 4px 12px rgba(64, 158, 255, 0.15);
    transform: translateY(-2px);
  }
}

.vuln-card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
  margin-bottom: 6px;
}

.vuln-card-name {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}

.vuln-card-desc {
  font-size: 12px;
  color: #909399;
  line-height: 1.5;
  margin-bottom: 6px;
}

.vuln-card-url {
  font-size: 11px;
  color: #c0c4cc;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.vuln-list {
  display: flex;
  flex-direction: column;
}

.vuln-list-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 9px 12px;
  border-radius: 6px;
  text-decoration: none;
  transition: background 0.2s;

  &:hover {
    background: #f5f7fa;

    .vuln-list-arrow {
      color: #409eff;
    }
  }
}

.vuln-list-name {
  font-size: 13px;
  font-weight: 500;
  color: #303133;
  min-width: 210px;
}

.vuln-list-desc {
  flex: 1;
  font-size: 12px;
  color: #909399;
}

.vuln-list-arrow {
  font-size: 13px;
  color: #c0c4cc;
}
</style>
