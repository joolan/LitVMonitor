<template>
  <div class="alert-channel-list">
    <div class="page-header">
      <h2>告警渠道</h2>
      <el-button type="primary" @click="showDialog()" v-permission="'alert-channel:create'">
        <el-icon><Plus /></el-icon>
        添加渠道
      </el-button>
    </div>

    <el-alert type="info" :closable="false" show-icon style="margin-bottom: 16px">
      <template #title>
        配置告警通知方式。邮件渠道通过SMTP发送告警邮件，Webhook渠道向指定URL发送HTTP请求。配置完成后点击"测试"验证连通性。禁用的渠道不会发送告警。
      </template>
    </el-alert>

    <el-card>
      <el-table :data="channels" stripe v-loading="loading">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="name" label="渠道名称" min-width="120" />
        <el-table-column prop="type" label="类型" width="100">
          <template #default="{ row }">
            <el-tag :type="getTagType(row.type)" size="small">
              {{ getTypeLabel(row.type) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="config" label="配置信息" min-width="250">
          <template #default="{ row }">
            <span v-if="row.type === 'EMAIL'">{{ getEmailDisplay(row.config) }}</span>
            <span v-else>{{ getWebhookDisplay(row.config) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="enabled" label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'info'" size="small">
              {{ row.enabled ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button type="success" link @click="testChannel(row.id)" :loading="testingId === row.id" v-permission="'alert-channel:test'">测试</el-button>
            <el-button type="primary" link @click="showDialog(row)" v-permission="'alert-channel:edit'">编辑</el-button>
            <el-popconfirm title="确认删除?" @confirm="deleteChannel(row.id)">
              <template #reference>
                <el-button type="danger" link v-permission="'alert-channel:delete'">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- Dialog -->
    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑渠道' : '添加渠道'" width="700px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="120px">
        <el-form-item label="渠道名称" prop="name">
          <el-input v-model="form.name" placeholder="例如: 邮件告警、钉钉Webhook" />
        </el-form-item>
        <el-form-item label="渠道类型" prop="type">
          <el-select v-model="form.type" @change="onTypeChange">
            <el-option label="邮件" value="EMAIL" />
            <el-option label="Webhook" value="WEBHOOK" />
            <el-option label="钉钉机器人" value="DINGTALK" />
            <el-option label="企业微信机器人" value="WECHAT" />
            <el-option label="飞书机器人" value="FEISHU" />
          </el-select>
        </el-form-item>

        <!-- Email Config -->
        <template v-if="form.type === 'EMAIL'">
          <el-divider content-position="left">SMTP服务器配置</el-divider>
          <el-form-item label="SMTP主机" prop="smtpHost">
            <el-input v-model="emailConfig.smtpHost" placeholder="smtp.gmail.com" />
          </el-form-item>
          <el-form-item label="SMTP端口" prop="smtpPort">
            <el-input-number v-model="emailConfig.smtpPort" :min="1" :max="65535" />
          </el-form-item>
          <el-form-item label="用户名">
            <el-input v-model="emailConfig.smtpUsername" placeholder="your@email.com" />
          </el-form-item>
          <el-form-item label="密码/授权码">
            <el-input v-model="emailConfig.smtpPassword" type="password" placeholder="SMTP密码或授权码" show-password />
          </el-form-item>
          <el-form-item label="使用SSL">
            <el-switch v-model="emailConfig.smtpSsl" />
          </el-form-item>
          <el-divider content-position="left">邮件内容</el-divider>
          <el-form-item label="发件人">
            <el-input v-model="emailConfig.from" :placeholder="emailConfig.smtpUsername || '自动使用SMTP用户名'" disabled />
          </el-form-item>
          <el-form-item label="收件人" prop="to">
            <el-input v-model="emailConfig.to" placeholder="多个邮箱用逗号分隔" />
          </el-form-item>
        </template>

        <!-- Webhook Config -->
        <template v-if="form.type === 'WEBHOOK'">
          <el-divider content-position="left">Webhook配置</el-divider>
          <el-form-item label="请求URL" prop="webhookUrl">
            <el-input v-model="webhookConfig.url" placeholder="https://your-webhook-url.com" />
          </el-form-item>
          <el-form-item label="请求方式">
            <el-select v-model="webhookConfig.method">
              <el-option label="POST" value="POST" />
              <el-option label="PUT" value="PUT" />
            </el-select>
          </el-form-item>
          <el-form-item label="Content-Type">
            <el-select v-model="webhookConfig.contentType">
              <el-option label="application/json" value="application/json" />
              <el-option label="application/x-www-form-urlencoded" value="application/x-www-form-urlencoded" />
            </el-select>
          </el-form-item>
          <el-form-item label="自定义请求头">
            <el-input v-model="webhookConfig.headers" type="textarea" :rows="2" placeholder='{"Authorization": "Bearer xxx"}' />
          </el-form-item>
        </template>

        <!-- DingTalk Config -->
        <template v-if="form.type === 'DINGTALK'">
          <el-divider content-position="left">钉钉机器人配置</el-divider>
          <el-form-item label="Webhook URL" prop="webhookUrl">
            <el-input v-model="webhookConfig.url" placeholder="https://oapi.dingtalk.com/robot/send?access_token=xxx" />
          </el-form-item>
          <el-alert type="info" :closable="false" show-icon style="margin-bottom: 16px">
            <template #title>
              在钉钉群中添加自定义机器人，复制Webhook地址填入上方。告警将以Markdown格式发送。
            </template>
          </el-alert>
        </template>

        <!-- WeChat Config -->
        <template v-if="form.type === 'WECHAT'">
          <el-divider content-position="left">企业微信机器人配置</el-divider>
          <el-form-item label="Webhook URL" prop="webhookUrl">
            <el-input v-model="webhookConfig.url" placeholder="https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=xxx" />
          </el-form-item>
          <el-alert type="info" :closable="false" show-icon style="margin-bottom: 16px">
            <template #title>
              在企业微信群中添加群机器人，复制Webhook地址填入上方。告警将以Markdown格式发送。
            </template>
          </el-alert>
        </template>

        <!-- Feishu Config -->
        <template v-if="form.type === 'FEISHU'">
          <el-divider content-position="left">飞书机器人配置</el-divider>
          <el-form-item label="Webhook URL" prop="webhookUrl">
            <el-input v-model="webhookConfig.url" placeholder="https://open.feishu.cn/open-apis/bot/v2/hook/xxx" />
          </el-form-item>
          <el-alert type="info" :closable="false" show-icon style="margin-bottom: 16px">
            <template #title>
              在飞书群中添加自定义机器人，复制Webhook地址填入上方。告警将以文本格式发送。
            </template>
          </el-alert>
        </template>

        <el-form-item label="启用">
          <el-switch v-model="form.enabled" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitForm" :loading="submitting" v-permission="editingId ? 'alert-channel:edit' : 'alert-channel:create'">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { alertChannelApi } from '@/api'

const loading = ref(false)
const submitting = ref(false)
const testingId = ref(null)
const dialogVisible = ref(false)
const editingId = ref(null)
const channels = ref([])
const formRef = ref(null)

const form = reactive({
  name: '',
  type: 'EMAIL',
  config: '',
  enabled: true
})

const emailConfig = reactive({
  smtpHost: '',
  smtpPort: 587,
  smtpUsername: '',
  smtpPassword: '',
  smtpSsl: true,
  from: '',
  to: ''
})

const webhookConfig = reactive({
  url: '',
  method: 'POST',
  contentType: 'application/json',
  headers: ''
})

const rules = {
  name: [{ required: true, message: '请输入渠道名称', trigger: 'blur' }],
  type: [{ required: true, message: '请选择渠道类型', trigger: 'change' }]
}

onMounted(() => loadChannels())

const loadChannels = async () => {
  loading.value = true
  try {
    const res = await alertChannelApi.list()
    channels.value = res.data || []
  } catch (error) {
    ElMessage.error('加载失败')
  } finally {
    loading.value = false
  }
}

const onTypeChange = () => {
  Object.assign(emailConfig, {
    smtpHost: '', smtpPort: 587, smtpUsername: '', smtpPassword: '',
    smtpSsl: true, from: '', to: ''
  })
  Object.assign(webhookConfig, {
    url: '', method: 'POST', contentType: 'application/json', headers: ''
  })
}

const showDialog = (row) => {
  if (row) {
    editingId.value = row.id
    form.name = row.name
    form.type = row.type
    form.enabled = row.enabled
    form.config = row.config
    try {
      const config = JSON.parse(row.config)
      if (row.type === 'EMAIL') {
        Object.assign(emailConfig, config)
      } else {
        Object.assign(webhookConfig, config)
      }
    } catch (e) {
      console.error('Failed to parse config:', e)
    }
  } else {
    editingId.value = null
    Object.assign(form, { name: '', type: 'EMAIL', config: '', enabled: true })
    onTypeChange()
  }
  dialogVisible.value = true
}

const submitForm = async () => {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  if (form.type === 'EMAIL') {
    if (!emailConfig.smtpHost || !emailConfig.smtpUsername || !emailConfig.to) {
      ElMessage.warning('请填写完整的SMTP配置和收件人')
      return
    }
    emailConfig.from = emailConfig.smtpUsername
    form.config = JSON.stringify(emailConfig)
  } else {
    if (!webhookConfig.url) {
      ElMessage.warning('请填写Webhook URL')
      return
    }
    form.config = JSON.stringify(webhookConfig)
  }

  submitting.value = true
  try {
    if (editingId.value) {
      await alertChannelApi.update(editingId.value, form)
      ElMessage.success('更新成功')
    } else {
      await alertChannelApi.create(form)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    loadChannels()
  } catch (error) {
    ElMessage.error(error.message || '操作失败')
  } finally {
    submitting.value = false
  }
}

const deleteChannel = async (id) => {
  try {
    await alertChannelApi.delete(id)
    ElMessage.success('删除成功')
    loadChannels()
  } catch (error) {
    ElMessage.error('删除失败')
  }
}

const testChannel = async (id) => {
  testingId.value = id
  try {
    await alertChannelApi.test(id)
    ElMessage.success('测试告警发送成功，请检查接收渠道')
  } catch (error) {
    ElMessage.error(error.message || '测试发送失败')
  } finally {
    testingId.value = null
  }
}

const getEmailDisplay = (configStr) => {
  try {
    const config = JSON.parse(configStr)
    return `${config.smtpHost}:${config.smtpPort} -> ${config.to}`
  } catch {
    return configStr
  }
}

const getWebhookDisplay = (configStr) => {
  try {
    const config = JSON.parse(configStr)
    return config.url || configStr
  } catch {
    return configStr
  }
}

const getTypeLabel = (type) => {
  const labels = {
    'EMAIL': '邮件',
    'WEBHOOK': 'Webhook',
    'DINGTALK': '钉钉',
    'WECHAT': '企微',
    'FEISHU': '飞书'
  }
  return labels[type] || type
}

const getTagType = (type) => {
  const types = {
    'EMAIL': 'primary',
    'WEBHOOK': 'success',
    'DINGTALK': 'danger',
    'WECHAT': 'success',
    'FEISHU': 'warning'
  }
  return types[type] || 'info'
}
</script>

<style lang="scss" scoped>
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  h2 { margin: 0; }
}
</style>
