<template>
  <div class="status-page">
    <!-- Header -->
    <header class="status-header">
      <div class="header-bg"></div>
      <div class="header-content">
        <div class="brand">
          <div class="brand-icon">
            <img src="/vite.svg" alt="Logo" />
          </div>
          <h1>LitVMonitor</h1>
        </div>
        <div class="overall-badge" :class="overallStatus.status">
          <span class="status-dot"></span>
          <span class="status-label">{{ statusText[overallStatus.status] || '未知状态' }}</span>
        </div>
        <div class="header-meta">
          <span class="meta-item">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="14" height="14">
              <circle cx="12" cy="12" r="10"/><path d="M12 6v6l4 2"/>
            </svg>
            {{ lastUpdated }}
          </span>
          <span class="meta-item">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="14" height="14">
              <path d="M22 12h-4l-3 9L9 3l-3 9H2"/>
            </svg>
            {{ monitors.length }} 个监控项
          </span>
        </div>
      </div>
    </header>

    <!-- Monitor Grid -->
    <main class="status-main">
      <div v-if="monitors.length === 0" class="empty-state">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" width="64" height="64">
          <path d="M21 12a9 9 0 11-18 0 9 9 0 0118 0z"/>
          <path d="M9 10h.01M15 10h.01M8 14s1.5 2 4 2 4-2 4-2"/>
        </svg>
        <p>暂无监控项</p>
      </div>

      <div v-else class="monitor-grid">
        <div
          v-for="monitor in monitors"
          :key="monitor.id"
          class="monitor-card"
          :class="monitor.status"
        >
          <div class="card-top">
            <div class="card-status-dot" :class="monitor.status"></div>
            <div class="card-status-text">{{ statusText[monitor.status] || '未知' }}</div>
          </div>
          <div class="card-name" :title="monitor.name">{{ monitor.name }}</div>
          <div class="card-url" :title="monitor.url">{{ monitor.url }}</div>
          <div class="card-footer">
            <div class="card-metric" v-if="monitor.lastResponseTime">
              <span class="metric-value">{{ monitor.lastResponseTime }}</span>
              <span class="metric-unit">ms</span>
            </div>
            <div class="card-metric" v-else>
              <span class="metric-value">-</span>
            </div>
            <div class="card-time" v-if="monitor.lastExecuted">
              {{ formatTime(monitor.lastExecuted) }}
            </div>
          </div>
          <div class="card-glow" :class="monitor.status"></div>
        </div>
      </div>
    </main>

    <!-- Footer -->
    <footer class="status-footer">
      <span>Powered by LitVMonitor</span>
    </footer>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { statusApi } from '@/api'
import { formatTime } from '@/utils/format'

const monitors = ref([])
const overallStatus = ref({})
const lastUpdated = ref('')
let timer = null

const statusText = {
  'operational': '正常运行',
  'degraded': '部分异常',
  'down': '服务中断',
  'unknown': '未知状态'
}

const loadData = async () => {
  try {
    const res = await statusApi.getStatus()
    const data = res.data || {}
    monitors.value = data.monitors || []
    overallStatus.value = data.overallStatus || {}
    lastUpdated.value = data.lastUpdated || ''
  } catch (error) {
    console.error('Failed to load status:', error)
  }
}

onMounted(() => {
  loadData()
  timer = setInterval(loadData, 30000)
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
})
</script>

<style lang="scss" scoped>
.status-page {
  min-height: 100vh;
  background: #0f1923;
  color: #e0e6ed;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
}

/* ── Header ── */
.status-header {
  position: relative;
  overflow: hidden;
  padding: 48px 24px 36px;
  text-align: center;

  .header-bg {
    position: absolute;
    inset: 0;
    background: linear-gradient(135deg, #0d253f 0%, #0f1923 40%, #1a1a2e 100%);
    &::before {
      content: '';
      position: absolute;
      top: -50%;
      left: -50%;
      width: 200%;
      height: 200%;
      background: radial-gradient(circle at 30% 50%, rgba(0, 212, 255, 0.06) 0%, transparent 50%),
                  radial-gradient(circle at 70% 50%, rgba(124, 58, 237, 0.06) 0%, transparent 50%);
    }
  }

  .header-content {
    position: relative;
    z-index: 1;
    max-width: 1200px;
    margin: 0 auto;
  }
}

.brand {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  margin-bottom: 20px;

  .brand-icon {
    width: 36px;
    height: 36px;
    img { width: 100%; height: 100%; }
  }

  h1 {
    font-size: 24px;
    font-weight: 700;
    letter-spacing: 1px;
    background: linear-gradient(135deg, #00d4ff, #7c3aed);
    -webkit-background-clip: text;
    -webkit-text-fill-color: transparent;
    background-clip: text;
    margin: 0;
  }
}

.overall-badge {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  padding: 10px 28px;
  border-radius: 40px;
  font-size: 16px;
  font-weight: 600;
  backdrop-filter: blur(12px);
  border: 1px solid rgba(255,255,255,0.08);
  margin-bottom: 16px;

  .status-dot {
    width: 10px;
    height: 10px;
    border-radius: 50%;
    flex-shrink: 0;
  }

  &.operational {
    background: rgba(34, 197, 94, 0.12);
    color: #22c55e;
    .status-dot { background: #22c55e; box-shadow: 0 0 8px #22c55e; animation: pulse 2s infinite; }
  }
  &.degraded {
    background: rgba(234, 179, 8, 0.12);
    color: #eab308;
    .status-dot { background: #eab308; box-shadow: 0 0 8px #eab308; animation: pulse 1.5s infinite; }
  }
  &.down {
    background: rgba(239, 68, 68, 0.12);
    color: #ef4444;
    .status-dot { background: #ef4444; box-shadow: 0 0 8px #ef4444; animation: pulse 1s infinite; }
  }
  &.unknown {
    background: rgba(148, 163, 184, 0.12);
    color: #94a3b8;
    .status-dot { background: #94a3b8; }
  }
}

.header-meta {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 24px;
  color: #64748b;
  font-size: 13px;

  .meta-item {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    svg { opacity: 0.6; }
  }
}

/* ── Main Grid ── */
.status-main {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 24px 48px;
}

.monitor-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 16px;
}

.monitor-card {
  position: relative;
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: 12px;
  padding: 20px;
  overflow: hidden;
  transition: all 0.3s ease;
  backdrop-filter: blur(8px);

  &:hover {
    transform: translateY(-2px);
    border-color: rgba(255, 255, 255, 0.12);
    box-shadow: 0 8px 32px rgba(0, 0, 0, 0.3);
  }

  .card-glow {
    position: absolute;
    top: -1px;
    left: 0;
    right: 0;
    height: 2px;
    border-radius: 12px 12px 0 0;
    &.operational { background: linear-gradient(90deg, transparent, #22c55e, transparent); }
    &.degraded { background: linear-gradient(90deg, transparent, #eab308, transparent); }
    &.down { background: linear-gradient(90deg, transparent, #ef4444, transparent); }
    &.unknown { background: linear-gradient(90deg, transparent, #94a3b8, transparent); }
  }
}

.card-top {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}

.card-status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;

  &.operational { background: #22c55e; box-shadow: 0 0 6px rgba(34, 197, 94, 0.5); }
  &.degraded { background: #eab308; box-shadow: 0 0 6px rgba(234, 179, 8, 0.5); animation: pulse 1.5s infinite; }
  &.down { background: #ef4444; box-shadow: 0 0 6px rgba(239, 68, 68, 0.5); animation: pulse 1s infinite; }
  &.unknown { background: #94a3b8; }
}

.card-status-text {
  font-size: 12px;
  font-weight: 500;
  text-transform: uppercase;
  letter-spacing: 0.5px;

  .operational & { color: #22c55e; }
  .degraded & { color: #eab308; }
  .down & { color: #ef4444; }
  .unknown & { color: #94a3b8; }
}

.card-name {
  font-size: 16px;
  font-weight: 600;
  color: #f1f5f9;
  margin-bottom: 4px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.card-url {
  font-size: 12px;
  color: #475569;
  margin-bottom: 16px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.card-footer {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  padding-top: 12px;
  border-top: 1px solid rgba(255, 255, 255, 0.04);
}

.card-metric {
  .metric-value {
    font-size: 20px;
    font-weight: 700;
    color: #e2e8f0;
    font-variant-numeric: tabular-nums;
  }
  .metric-unit {
    font-size: 12px;
    color: #64748b;
    margin-left: 2px;
  }
}

.card-time {
  font-size: 11px;
  color: #475569;
}

/* ── Empty State ── */
.empty-state {
  text-align: center;
  padding: 80px 0;
  color: #475569;

  svg { margin-bottom: 16px; opacity: 0.3; }
  p { font-size: 16px; margin: 0; }
}

/* ── Footer ── */
.status-footer {
  text-align: center;
  padding: 24px;
  color: #334155;
  font-size: 12px;
  border-top: 1px solid rgba(255, 255, 255, 0.04);
}

/* ── Animations ── */
@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.5; }
}

/* ── Responsive ── */
@media (max-width: 640px) {
  .status-header { padding: 32px 16px 24px; }
  .brand h1 { font-size: 20px; }
  .overall-badge { padding: 8px 20px; font-size: 14px; }
  .header-meta { flex-direction: column; gap: 8px; }
  .status-main { padding: 0 16px 32px; }
  .monitor-grid { grid-template-columns: 1fr; }
}

@media (min-width: 641px) and (max-width: 960px) {
  .monitor-grid { grid-template-columns: repeat(2, 1fr); }
}

@media (min-width: 961px) {
  .monitor-grid { grid-template-columns: repeat(3, 1fr); }
}

@media (min-width: 1280px) {
  .monitor-grid { grid-template-columns: repeat(4, 1fr); }
}
</style>
