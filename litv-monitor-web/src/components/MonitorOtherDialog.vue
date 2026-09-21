<template>
  <el-dialog
    :model-value="modelValue"
    :title="editingRow ? '编辑监控' : '添加其他类型监控'"
    width="650px"
    top="5vh"
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
      <el-form-item label="监控类型" prop="monitorType">
        <el-select v-model="form.monitorType" placeholder="选择类型" style="width: 100%">
          <el-option-group label="网络探测">
            <el-option label="Ping (ICMP)" value="PING" />
            <el-option label="TCP 端口" value="TCP" />
          </el-option-group>
          <el-option-group label="远程登录 / 文件">
            <el-option label="SSH (22)" value="SSH" />
            <el-option label="Telnet (23)" value="TELNET" />
            <el-option label="FTP (21)" value="FTP" />
            <el-option label="VNC (5900)" value="VNC" />
          </el-option-group>
          <el-option-group label="数据库 / 缓存">
            <el-option label="MySQL (3306)" value="MYSQL" />
            <el-option label="PostgreSQL (5432)" value="POSTGRESQL" />
            <el-option label="Redis (6379)" value="REDIS" />
            <el-option label="Memcached (11211)" value="MEMCACHED" />
            <el-option label="MongoDB (27017)" value="MONGODB" />
            <el-option label="ZooKeeper (2181)" value="ZOOKEEPER" />
          </el-option-group>
          <el-option-group label="消息队列">
            <el-option label="AMQP / RabbitMQ (5672)" value="AMQP" />
            <el-option label="MQTT (1883)" value="MQTT" />
          </el-option-group>
        </el-select>
      </el-form-item>
      <el-form-item label="名称" prop="name">
        <el-input v-model="form.name" placeholder="监控项名称" />
      </el-form-item>
      <el-form-item label="主机地址" prop="host">
        <el-input v-model="form.host" placeholder="IP 或域名" />
      </el-form-item>
      <el-form-item label="端口" v-if="showPort">
        <el-input-number v-model="form.port" :min="1" :max="65535" :placeholder="defaultPortHint" />
        <span style="margin-left:8px;color:#909399;font-size:12px">{{ defaultPortHint }}</span>
      </el-form-item>
      <el-form-item label="超时(秒)">
        <el-input-number v-model="form.timeout" :min="1" :max="300" />
      </el-form-item>
      <el-form-item label="启用">
        <el-switch v-model="form.enabled" />
      </el-form-item>
      <el-form-item label="公开状态页展示">
        <el-switch v-model="form.showOnStatusPage" />
      </el-form-item>
      <el-form-item label="监控项告警">
        <el-switch v-model="form.alertEnabled" />
      </el-form-item>
      <el-form-item label="告警阈值" v-if="form.alertEnabled">
        <el-input-number v-model="form.alertConsecutiveCount" :min="1" :max="20" />
        <span style="margin-left:8px;color:#909399;font-size:12px">次连续失败后告警</span>
      </el-form-item>
      <el-form-item label="告警通道" v-if="form.alertEnabled">
        <el-select v-model="form.alertConfigIds" multiple placeholder="选择告警通道" style="width:100%">
          <el-option v-for="ch in alertConfigs" :key="ch.id" :label="ch.name" :value="String(ch.id)">
            <span>{{ ch.name }}</span>
            <el-tag :type="ch.enabled ? 'success' : 'info'" size="small" style="margin-left: 8px; float: right">
              {{ ch.enabled ? '启用' : '禁用' }}
            </el-tag>
          </el-option>
        </el-select>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" @click="submit" :loading="submitting"
        v-permission="editingRow ? 'monitor:edit' : 'monitor:create'">确定</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, reactive, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { monitorApi } from '@/api'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  editingRow: { type: Object, default: null },
  alertConfigs: { type: Array, default: () => [] }
})
const emit = defineEmits(['update:modelValue', 'saved'])

const DEFAULT_PORTS = {
  SSH: 22, TELNET: 23, FTP: 21, VNC: 5900,
  MYSQL: 3306, POSTGRESQL: 5432, REDIS: 6379,
  MEMCACHED: 11211, MONGODB: 27017, ZOOKEEPER: 2181,
  AMQP: 5672, MQTT: 1883
}

const formRef = ref(null)
const submitting = ref(false)
const form = reactive({
  monitorType: 'PING',
  name: '',
  host: '',
  port: null,
  timeout: 5,
  enabled: true,
  showOnStatusPage: false,
  alertEnabled: false,
  alertConsecutiveCount: 3,
  alertConfigIds: []
})

const rules = {
  monitorType: [{ required: true, message: '请选择监控类型', trigger: 'change' }],
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  host: [{ required: true, message: '请输入主机地址', trigger: 'blur' }]
}

const defaultPortHint = computed(() => {
  const p = DEFAULT_PORTS[form.monitorType]
  return p ? `默认 ${p}` : '必填'
})
const showPort = computed(() => form.monitorType !== 'PING')

const resetForm = () => {
  Object.assign(form, {
    monitorType: 'PING', name: '', host: '', port: null, timeout: 5,
    enabled: true, showOnStatusPage: false, alertEnabled: false,
    alertConsecutiveCount: 3, alertConfigIds: []
  })
}

const fillForm = (row) => {
  if (!row) {
    resetForm()
    return
  }
  form.monitorType = row.monitorType || 'PING'
  form.name = row.name || ''
  form.timeout = row.timeout || 5
  form.enabled = row.enabled !== false
  form.showOnStatusPage = row.showOnStatusPage || false
  form.alertEnabled = row.alertEnabled || false
  form.alertConsecutiveCount = row.alertConsecutiveCount || 3
  let host = '', port = null
  if (row.config) {
    try {
      const cfg = JSON.parse(row.config)
      host = cfg.host || ''
      port = cfg.port || null
    } catch (e) { /* ignore */ }
  }
  if (!host && row.url) {
    host = row.url.replace(/^https?:\/\//, '').replace(/:\d+$/, '').replace(/\/.*$/, '')
  }
  form.host = host
  form.port = port
  form.alertConfigIds = row.alertConfigIds && typeof row.alertConfigIds === 'string'
    ? row.alertConfigIds.split(',').filter(Boolean)
    : (row.alertConfigIds || [])
}

watch(() => props.modelValue, (v) => { if (v) fillForm(props.editingRow) })

const submit = async () => {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  const type = form.monitorType
  const port = type === 'PING' ? null : (form.port || DEFAULT_PORTS[type] || null)
  const url = port ? `${form.host}:${port}` : form.host
  const config = JSON.stringify(port ? { host: form.host, port } : { host: form.host })
  const alertConfigIds = Array.isArray(form.alertConfigIds)
    ? form.alertConfigIds.join(',') : (form.alertConfigIds || '')
  const payload = {
    name: form.name,
    url,
    monitorType: type,
    method: 'GET',
    config,
    timeout: form.timeout,
    enabled: form.enabled,
    showOnStatusPage: form.showOnStatusPage,
    alertEnabled: form.alertEnabled,
    alertConsecutiveCount: form.alertConsecutiveCount,
    alertConfigIds
  }
  submitting.value = true
  try {
    if (props.editingRow) {
      await monitorApi.update(props.editingRow.id, payload)
      ElMessage.success('更新成功')
    } else {
      await monitorApi.create(payload)
      ElMessage.success('创建成功')
    }
    emit('update:modelValue', false)
    emit('saved')
  } catch (error) {
    ElMessage.error(error.message || '操作失败')
  } finally {
    submitting.value = false
  }
}
</script>
