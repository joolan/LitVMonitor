<template>
  <div class="not-found">
    <el-result icon="warning" title="404" sub-title="抱歉，你访问的页面不存在">
      <template #extra>
        <el-button type="primary" @click="$router.push('/dashboard')">返回仪表盘</el-button>
      </template>
    </el-result>
  </div>
</template>

<script setup>
</script>

<style lang="scss" scoped>
.not-found {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 60vh;
}
</style>
