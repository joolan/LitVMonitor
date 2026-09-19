<template>
  <router-view />
</template>

<script setup>
import { onMounted } from 'vue'
import { useUserStore } from './stores/user'

const userStore = useUserStore()

onMounted(() => {
  const token = localStorage.getItem('token')
  if (token) {
    userStore.setToken(token)
  }
})
</script>
