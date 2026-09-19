<template>
  <div class="audit-log-list">
    <div class="page-header">
      <h2>审计日志</h2>
    </div>

    <el-card class="search-card">
      <el-form :inline="true">
        <el-form-item label="操作用户">
          <el-input v-model="searchForm.username" placeholder="用户名" clearable />
        </el-form-item>
        <el-form-item label="操作类型">
          <el-select v-model="searchForm.action" clearable placeholder="全部" style="width: 150px">
            <el-option label="创建" value="CREATE" />
            <el-option label="更新" value="UPDATE" />
            <el-option label="删除" value="DELETE" />
            <el-option label="执行" value="EXECUTE" />
            <el-option label="登录" value="LOGIN" />
            <el-option label="启用" value="ENABLE" />
            <el-option label="禁用" value="DISABLE" />
            <el-option label="测试" value="TEST" />
            <el-option label="恢复" value="RESTORE" />
            <el-option label="踢出" value="KICK" />
            <el-option label="解锁" value="UNLOCK" />
            <el-option label="修改资料" value="UPDATE_PROFILE" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="resetSearch">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card>
      <el-table :data="logs" stripe v-loading="loading">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="username" label="用户" width="120" />
        <el-table-column prop="action" label="操作" width="100">
          <template #default="{ row }">
            <el-tag :type="actionType(row.action)" size="small">{{ actionLabel(row.action) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="targetType" label="对象类型" width="100" />
        <el-table-column prop="targetName" label="对象名称" min-width="150" show-overflow-tooltip />
        <el-table-column prop="detail" label="详情" min-width="200" show-overflow-tooltip />
        <el-table-column prop="requestUrl" label="操作URL" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <el-text v-if="row.requestUrl" size="small" truncated>{{ row.requestUrl }}</el-text>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="requestBody" label="请求数据" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <el-text v-if="row.requestBody" size="small" truncated>{{ row.requestBody }}</el-text>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="ipAddress" label="IP地址" width="130" />
        <el-table-column prop="createdAt" label="时间" width="180">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="pagination.page"
        v-model:page-size="pagination.size"
        :total="pagination.total"
        layout="total, sizes, prev, pager, next"
        @change="loadList"
      />
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { auditApi } from '@/api'
import { formatTime } from '@/utils/format'
import { AUDIT_ACTIONS } from '@/constants/enums'

const loading = ref(false)
const logs = ref([])
const pagination = reactive({ page: 1, size: 20, total: 0 })
const searchForm = reactive({ username: '', action: '' })

onMounted(() => { loadList() })

const loadList = async () => {
  loading.value = true
  try {
    const res = await auditApi.list({
      page: pagination.page,
      size: pagination.size,
      username: searchForm.username,
      action: searchForm.action
    })
    logs.value = res.data?.records || []
    pagination.total = res.data?.total || 0
  } catch (error) {
    ElMessage.error('加载失败')
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  pagination.page = 1
  loadList()
}

const resetSearch = () => {
  searchForm.username = ''
  searchForm.action = ''
  pagination.page = 1
  loadList()
}

const actionType = (action) => AUDIT_ACTIONS[action]?.tagType || 'info'
const actionLabel = (action) => AUDIT_ACTIONS[action]?.label || action
</script>

<style lang="scss" scoped>
.audit-log-list {
  .search-card { margin-bottom: 20px; }
  .el-pagination { margin-top: 20px; justify-content: flex-end; }
  .text-muted { color: #c0c4cc; }
}
</style>
