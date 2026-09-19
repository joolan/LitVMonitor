<template>
  <div class="security-settings">
    <div class="page-header">
      <h2>安全设置</h2>
    </div>

    <!-- IP Access Control -->
    <el-card class="setting-card" v-loading="loading">
      <template #header>
        <div class="card-header">
          <span>IP 访问控制</span>
        </div>
      </template>
      <el-form label-width="140px">
        <el-divider content-position="left">IP 白名单</el-divider>
        <el-form-item label="启用白名单">
          <el-switch v-model="settings.ip_whitelist_enabled" />
          <span class="form-tip">启用后仅允许白名单中的IP访问</span>
        </el-form-item>
        <el-form-item label="白名单IP列表">
          <el-input
            v-model="settings.ip_whitelist"
            type="textarea"
            :rows="4"
            placeholder="每行一个IP地址，支持CIDR格式&#10;例如：&#10;192.168.1.100&#10;10.0.0.0/24"
          />
        </el-form-item>

        <el-divider content-position="left">IP 黑名单</el-divider>
        <el-form-item label="启用黑名单">
          <el-switch v-model="settings.ip_blacklist_enabled" />
          <span class="form-tip">黑名单优先级最高，被黑名单匹配的IP无法访问</span>
        </el-form-item>
        <el-form-item label="黑名单IP列表">
          <el-input
            v-model="settings.ip_blacklist"
            type="textarea"
            :rows="4"
            placeholder="每行一个IP地址，支持CIDR格式"
          />
        </el-form-item>

        <el-divider content-position="left">IP 来源配置</el-divider>
        <el-form-item label="IP来源请求头">
          <el-input v-model="settings.ip_source_header" placeholder="X-Real-IP" style="width: 300px" />
          <div class="form-tip">
            <el-tooltip placement="right" :width="360">
              <template #content>
                <div style="line-height: 1.8">
                  <div>从指定请求头获取客户端真实IP，多个头用逗号分隔。</div>
                  <div style="margin-top: 4px"><b>常用值:</b></div>
                  <div><code>X-Real-IP</code> — Nginx 反代常用</div>
                  <div><code>X-Forwarded-For</code> — 标准代理头</div>
                  <div><code>REMOTE_ADDR</code> — 使用连接来源IP（不读请求头）</div>
                  <div style="margin-top: 4px;color:#f56c6c">修改后需重启服务生效</div>
                </div>
              </template>
              <el-icon style="vertical-align: middle; cursor: pointer; color: #909399"><QuestionFilled /></el-icon>
            </el-tooltip>
          </div>
        </el-form-item>
        <el-form-item label="严格IP模式">
          <el-switch v-model="settings.ip_strict_mode" />
          <div class="form-tip">
            <el-tooltip placement="right" :width="300">
              <template #content>
                <div style="line-height: 1.8">
                  <div>开启后，如果 X-Forwarded-For 包含多个IP（可能被伪造），将拒绝请求。</div>
                  <div style="margin-top: 4px">正常Nginx反代只会有一个IP值。</div>
                  <div style="margin-top: 4px;color:#f56c6c">修改后需重启服务生效</div>
                </div>
              </template>
              <el-icon style="vertical-align: middle; cursor: pointer; color: #909399"><QuestionFilled /></el-icon>
            </el-tooltip>
          </div>
        </el-form-item>
        <el-form-item label="可信代理IP">
          <el-input v-model="settings.xff_trusted_proxies" placeholder="127.0.0.1,::1" style="width: 300px" />
          <div class="form-tip">逗号分隔的可信代理IP列表，非可信代理的XFF头不读取。修改后需重启服务生效</div>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- Account Lockout -->
    <el-card class="setting-card">
      <template #header>
        <div class="card-header">
          <span>登录失败锁定</span>
        </div>
      </template>
      <el-form label-width="140px">
        <el-form-item label="启用锁定">
          <el-switch v-model="settings.lockout_enabled" />
          <span class="form-tip">连续登录失败后自动锁定账号</span>
        </el-form-item>
        <el-form-item label="最大失败次数">
          <el-input-number v-model="lockoutMaxAttempts" :min="3" :max="20" :disabled="!settings.lockout_enabled" />
          <span class="form-tip">次</span>
        </el-form-item>
        <el-form-item label="锁定时长">
          <el-input-number v-model="lockoutDuration" :min="5" :max="1440" :disabled="!settings.lockout_enabled" />
          <span class="form-tip">分钟</span>
        </el-form-item>
        <el-form-item>
          <el-dropdown @command="handleUnlock">
            <el-button type="warning" :disabled="!settings.lockout_enabled" v-permission="'security:unlock'">
              解锁账号 <el-icon class="el-icon--right"><ArrowDown /></el-icon>
            </el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="all">解锁所有账号</el-dropdown-item>
                <el-dropdown-item command="select" divided>解锁指定账号</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
          <span class="form-tip">admin账号被锁只能重启后端服务解锁</span>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- Max Concurrent Sessions -->
    <el-card class="setting-card">
      <template #header>
        <div class="card-header">
          <span>同时在线人数限制</span>
        </div>
      </template>
      <el-form label-width="140px">
        <el-form-item label="启用限制">
          <el-switch v-model="settings.max_sessions_enabled" />
          <span class="form-tip">超过限制时自动挤掉最早登录的会话</span>
        </el-form-item>
        <el-form-item label="最大在线人数">
          <el-input-number v-model="maxSessions" :min="1" :max="50" :disabled="!settings.max_sessions_enabled" />
          <span class="form-tip">人/账号</span>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- Public Status Page -->
    <el-card class="setting-card">
      <template #header>
        <div class="card-header">
          <span>公开状态页</span>
        </div>
      </template>
      <el-form label-width="140px">
        <el-form-item label="允许匿名访问">
          <el-switch v-model="settings.status_page_public" />
          <span class="form-tip">开启后，匿名用户可通过 /status 访问状态页</span>
          <el-tooltip placement="right" :width="320">
            <template #content>
              <div style="line-height: 1.8">
                <div>状态页展示的监控项由各监控项的「公开状态页展示」开关控制。</div>
                <div>即使本开关开启，未勾选「公开状态页展示」的监控项也不会出现在状态页。</div>
              </div>
            </template>
            <el-icon style="margin-left: 6px; color: #909399; cursor: pointer"><QuestionFilled /></el-icon>
          </el-tooltip>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- Outbound Request Security (SSRF) -->
    <el-card class="setting-card">
      <template #header>
        <div class="card-header">
          <span>监控请求安全（SSRF 防护）</span>
        </div>
      </template>
      <el-form label-width="140px">
        <el-form-item label="防护模式">
          <el-select v-model="settings.ssrf_mode" style="width: 320px">
            <el-option label="严格：禁止访问内网/保留地址" value="strict" />
            <el-option label="允许内网：仍禁止云元数据等" value="allow_internal" />
            <el-option label="关闭：不限制（纯内网部署）" value="off" />
          </el-select>
          <el-tooltip placement="right" :width="400">
            <template #content>
              <div style="line-height: 1.8">
                <div><b>严格</b>：禁止监控/告警请求访问 回环、内网(10/172.16/192.168)、链路本地、CGNAT、云元数据(169.254.169.254)、IPv6 ULA</div>
                <div style="margin-top:4px"><b>允许内网</b>：允许访问内网地址，但仍禁止 回环、链路本地/云元数据、CGNAT 等</div>
                <div style="margin-top:4px"><b>关闭</b>：不做任何限制，适用于系统本身部署在内网、需要监控内网地址的场景</div>
                <div style="margin-top:4px;color:#67c23a">修改后最多 5 秒生效，无需重启</div>
              </div>
            </template>
            <el-icon style="margin-left: 6px; color: #909399; cursor: pointer"><QuestionFilled /></el-icon>
          </el-tooltip>
        </el-form-item>
      </el-form>
    </el-card>

    <div class="setting-actions">
      <el-button type="primary" @click="saveSettings" :loading="saving" v-permission="'security:settings'">保存设置</el-button>
    </div>

    <!-- Unlock User Dialog -->
    <el-dialog v-model="unlockDialogVisible" title="解锁指定账号" width="400px">
      <el-form label-width="80px">
        <el-form-item label="选择账号">
          <el-select v-model="unlockUserId" placeholder="请选择要解锁的账号" style="width: 100%" filterable>
            <el-option
              v-for="u in unlockableUsers"
              :key="u.id"
              :label="`${u.username} (${u.nickname || '-'})`"
              :value="u.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="unlockDialogVisible = false">取消</el-button>
        <el-button type="warning" @click="confirmUnlockUser" :disabled="!unlockUserId" v-permission="'security:unlock'">确认解锁</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { QuestionFilled } from '@element-plus/icons-vue'
import { securityApi, userApi } from '@/api'

const loading = ref(false)
const saving = ref(false)

const settings = reactive({
  ip_whitelist_enabled: false,
  ip_whitelist: '',
  ip_blacklist_enabled: false,
  ip_blacklist: '',
  ip_source_header: 'X-Real-IP',
  ip_strict_mode: false,
  xff_trusted_proxies: '127.0.0.1,::1',
  lockout_enabled: false,
  lockout_max_attempts: '5',
  lockout_duration_minutes: '30',
  max_sessions_enabled: false,
  max_sessions: '3',
  status_page_public: false,
  ssrf_mode: 'strict'
})

const lockoutMaxAttempts = ref(5)
const lockoutDuration = ref(30)
const maxSessions = ref(3)

const unlockDialogVisible = ref(false)
const unlockUserId = ref(null)
const unlockableUsers = ref([])

onMounted(() => loadSettings())

const loadSettings = async () => {
  loading.value = true
  try {
    const res = await securityApi.getSettings()
    if (res.data) {
      Object.keys(settings).forEach(key => {
        if (res.data[key] !== undefined) {
          const val = res.data[key]
          // Convert string booleans
          if (val === 'true' || val === 'false') {
            settings[key] = val === 'true'
          } else {
            settings[key] = val
          }
        }
      })
      lockoutMaxAttempts.value = parseInt(settings.lockout_max_attempts) || 5
      lockoutDuration.value = parseInt(settings.lockout_duration_minutes) || 30
      maxSessions.value = parseInt(settings.max_sessions) || 3
    }
  } catch (error) {
    ElMessage.error('加载设置失败')
  } finally {
    loading.value = false
  }
}

const saveSettings = async () => {
  saving.value = true
  try {
    const data = {
      ip_whitelist_enabled: String(settings.ip_whitelist_enabled),
      ip_whitelist: settings.ip_whitelist || '',
      ip_blacklist_enabled: String(settings.ip_blacklist_enabled),
      ip_blacklist: settings.ip_blacklist || '',
      ip_source_header: settings.ip_source_header || 'X-Real-IP',
      ip_strict_mode: String(settings.ip_strict_mode),
      xff_trusted_proxies: settings.xff_trusted_proxies || '127.0.0.1,::1',
      lockout_enabled: String(settings.lockout_enabled),
      lockout_max_attempts: String(lockoutMaxAttempts.value),
      lockout_duration_minutes: String(lockoutDuration.value),
      max_sessions_enabled: String(settings.max_sessions_enabled),
      max_sessions: String(maxSessions.value),
      status_page_public: String(settings.status_page_public),
      ssrf_mode: settings.ssrf_mode || 'strict'
    }
    await securityApi.updateSettings(data)
    ElMessage.success('保存成功')
  } catch (error) {
    ElMessage.error('保存失败')
  } finally {
    saving.value = false
  }
}

const handleUnlock = async (command) => {
  if (command === 'all') {
    try {
      await ElMessageBox.confirm('确认解锁所有被锁定的账号？', '确认', { type: 'warning' })
      // Re-save current settings to trigger unlock on backend
      const data = {
        lockout_enabled: String(settings.lockout_enabled),
        lockout_max_attempts: String(lockoutMaxAttempts.value),
        lockout_duration_minutes: String(lockoutDuration.value)
      }
      await securityApi.updateSettings(data)
      ElMessage.success('已解锁所有账号')
    } catch (error) {
      if (error !== 'cancel') {
        ElMessage.error('操作失败')
      }
    }
  } else if (command === 'select') {
    unlockUserId.value = null
    await loadUnlockableUsers()
    unlockDialogVisible.value = true
  }
}

const loadUnlockableUsers = async () => {
  try {
    const res = await userApi.list({ page: 1, size: 100 })
    const allUsers = res.data?.records || []
    // Exclude admin from the list
    unlockableUsers.value = allUsers.filter(u => u.username !== 'admin')
  } catch (error) {
    ElMessage.error('加载用户列表失败')
  }
}

const confirmUnlockUser = async () => {
  if (!unlockUserId.value) return
  try {
    await securityApi.unlockUser(unlockUserId.value)
    ElMessage.success('已解锁')
    unlockDialogVisible.value = false
  } catch (error) {
    ElMessage.error('解锁失败')
  }
}
</script>

<style lang="scss" scoped>
.security-settings {
  .setting-card {
    margin-bottom: 20px;

    .card-header {
      font-weight: 600;
      font-size: 16px;
    }
  }

  .form-tip {
    margin-left: 12px;
    color: #909399;
    font-size: 13px;
  }

  .setting-actions {
    text-align: center;
    padding: 20px 0;
  }
}
</style>
