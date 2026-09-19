<template>
  <div class="backup-list">
    <div class="page-header">
      <h2>数据备份</h2>
      <div class="header-actions">
        <el-button type="primary" @click="createBackup" :loading="creating" v-permission="'backup:create'">
          <el-icon><Download /></el-icon>
          创建备份
        </el-button>
        <el-upload
          ref="uploadRef"
          :action="'/api/backup/upload-restore'"
          :headers="uploadHeaders"
          :show-file-list="false"
          :before-upload="beforeUpload"
          :on-success="onUploadSuccess"
          :on-error="onUploadError"
          accept=".db"
        >
          <el-button type="warning" v-permission="'backup:restore'">
            <el-icon><Upload /></el-icon>
            上传恢复
          </el-button>
        </el-upload>
      </div>
    </div>

    <el-card>
      <div v-if="downloading" class="download-progress">
        <div class="progress-info">
          <span>正在下载：{{ downloadFileName }}</span>
          <span v-if="downloadTotal > 0">{{ formatSize(downloadLoaded) }} / {{ formatSize(downloadTotal) }}</span>
        </div>
        <el-progress :percentage="downloadProgress" :stroke-width="12" :status="downloadProgress >= 100 ? 'success' : ''" />
      </div>
      <el-table :data="backups" stripe v-loading="loading">
        <el-table-column prop="name" label="文件名" min-width="250" />
        <el-table-column label="大小" width="120">
          <template #default="{ row }">{{ formatSize(row.size) }}</template>
        </el-table-column>
        <el-table-column label="创建时间" width="180">
          <template #default="{ row }">{{ formatTime(row.lastModified) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="downloadBackup(row.path)" v-permission="'backup:download'">下载</el-button>
            <el-popconfirm title="确认恢复此备份？当前数据库将被替换，需要重启应用。" @confirm="restoreBackup(row.path)">
              <template #reference>
                <el-button type="warning" link v-permission="'backup:restore'">恢复</el-button>
              </template>
            </el-popconfirm>
            <el-popconfirm title="确认删除此备份?" @confirm="deleteBackup(row.path)">
              <template #reference>
                <el-button type="danger" link v-permission="'backup:delete'">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!loading && backups.length === 0" description="暂无备份" />
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { backupApi } from '@/api'
import { formatTime } from '@/utils/format'

const loading = ref(false)
const creating = ref(false)
const backups = ref([])
const uploadRef = ref(null)

const uploadHeaders = computed(() => ({
  Authorization: `Bearer ${localStorage.getItem('token')}`
}))

onMounted(() => { loadBackups() })

const loadBackups = async () => {
  loading.value = true
  try {
    const res = await backupApi.list()
    backups.value = res.data || []
  } catch (error) {
    ElMessage.error('加载备份列表失败')
  } finally {
    loading.value = false
  }
}

const createBackup = async () => {
  creating.value = true
  try {
    const res = await backupApi.create()
    if (res.code === 200) {
      ElMessage.success('备份创建成功')
      loadBackups()
    } else {
      ElMessage.error(res.message || '备份创建失败')
    }
  } catch (error) {
    ElMessage.error(error.message || '备份创建失败')
  } finally {
    creating.value = false
  }
}

const restoreBackup = async (path) => {
  try {
    const res = await backupApi.restore({ path })
    if (res.code === 200) {
      ElMessage.success('恢复成功，请重启应用使备份生效')
    } else {
      ElMessage.error(res.message || '恢复失败')
    }
  } catch (error) {
    ElMessage.error(error.message || '恢复失败')
  }
}

const deleteBackup = async (path) => {
  try {
    const res = await backupApi.delete(path)
    if (res.code === 200) {
      ElMessage.success('删除成功')
      loadBackups()
    } else {
      ElMessage.error(res.message || '删除失败')
    }
  } catch (error) {
    ElMessage.error(error.message || '删除失败')
  }
}

const downloading = ref(false)
const downloadProgress = ref(0)
const downloadTotal = ref(0)
const downloadLoaded = ref(0)
const downloadFileName = ref('')

const downloadBackup = async (path) => {
  if (downloading.value) return
  const fileName = path.split(/[/\\]/).pop()

  // File System Access API: 用户选路径 + 流式写入，无延迟
  if (window.showSaveFilePicker) {
    try {
      const token = localStorage.getItem('token')
      const res = await fetch('/api/backup/download-token', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` },
        body: JSON.stringify({ path })
      })
      const data = await res.json()
      if (data.code !== 200 || !data.data?.token) {
        ElMessage.error(data.message || '获取下载令牌失败')
        return
      }

      const handle = await window.showSaveFilePicker({ suggestedName: fileName })
      const writable = await handle.createWritable()

      downloading.value = true
      downloadProgress.value = 0
      downloadFileName.value = fileName

      const dl = await fetch(`/api/backup/download?path=${encodeURIComponent(path)}`, {
        headers: { 'X-Download-Token': data.data.token }
      })
      if (!dl.ok) {
        ElMessage.error('下载失败')
        downloading.value = false
        return
      }

      const contentLength = parseInt(dl.headers.get('Content-Length') || '0', 10)
      downloadTotal.value = contentLength
      downloadLoaded.value = 0

      const reader = dl.body.getReader()
      while (true) {
        const { done, value } = await reader.read()
        if (done) break
        await writable.write(value)
        downloadLoaded.value += value.length
        downloadProgress.value = contentLength > 0
          ? Math.min(100, Math.round((downloadLoaded.value / contentLength) * 100))
          : 0
      }
      await writable.close()
      ElMessage.success('下载完成')
    } catch (e) {
      if (e.name !== 'AbortError') ElMessage.error('下载失败')
    } finally {
      downloading.value = false
    }
    return
  }

  // 降级方案：无 showSaveFilePicker 的浏览器（Firefox/Safari）
  try {
    const token = localStorage.getItem('token')
    const res = await fetch('/api/backup/download-token', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` },
      body: JSON.stringify({ path })
    })
    const data = await res.json()
    if (data.code !== 200 || !data.data?.token) {
      ElMessage.error(data.message || '获取下载令牌失败')
      return
    }

    downloading.value = true
    downloadProgress.value = 0
    downloadFileName.value = fileName

    const dl = await fetch(`/api/backup/download?path=${encodeURIComponent(path)}`, {
      headers: { 'X-Download-Token': data.data.token }
    })
    if (!dl.ok) {
      ElMessage.error('下载失败')
      downloading.value = false
      return
    }

    const contentLength = parseInt(dl.headers.get('Content-Length') || '0', 10)
    downloadTotal.value = contentLength
    downloadLoaded.value = 0

    const reader = dl.body.getReader()
    const chunks = []
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      chunks.push(value)
      downloadLoaded.value += value.length
      downloadProgress.value = contentLength > 0
        ? Math.min(100, Math.round((downloadLoaded.value / contentLength) * 100))
        : 0
    }

    const blob = new Blob(chunks)
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = fileName
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(url)
    ElMessage.success('下载完成')
  } catch (error) {
    ElMessage.error('下载失败')
  } finally {
    downloading.value = false
  }
}

const beforeUpload = (file) => {
  if (!file.name.endsWith('.db')) {
    ElMessage.error('仅支持 .db 备份文件')
    return false
  }
  return true
}

const onUploadSuccess = (response) => {
  if (response.code === 200) {
    ElMessage.success('上传恢复成功，请重启应用使备份生效')
    loadBackups()
  } else {
    ElMessage.error(response.message || '上传恢复失败')
  }
}

const onUploadError = () => {
  ElMessage.error('上传失败')
}

const formatSize = (bytes) => {
  if (!bytes) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  let i = 0
  let size = bytes
  while (size >= 1024 && i < units.length - 1) {
    size /= 1024
    i++
  }
  return size.toFixed(1) + ' ' + units[i]
}
</script>

<style lang="scss" scoped>
.backup-list {
  .page-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 20px;

    h2 { margin: 0; }

    .header-actions {
      display: flex;
      gap: 8px;
    }
  }
  .el-empty { padding: 40px 0; }
  .download-progress {
    margin-bottom: 16px;
    .progress-info {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 8px;
      font-size: 13px;
      color: #606266;
    }
  }
}
</style>
