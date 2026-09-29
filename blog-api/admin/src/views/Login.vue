<template>
  <div class="login-card" v-if="!isLoggedIn()">
    <div class="card">
      <h1 style="margin-bottom:16px">博客后台登录</h1>
      <div class="msg msg-error" v-if="error">{{ error }}</div>
      <div class="form-row"><label>用户名</label><input v-model="username" @keyup.enter="submit"></div>
      <div class="form-row"><label>密码</label><input type="password" v-model="password" @keyup.enter="submit"></div>
      <button class="btn btn-primary" style="width:100%" @click="submit" :disabled="loading">{{ loading ? '登录中...' : '登录' }}</button>
    </div>
  </div>
  <div v-else><RouterView /></div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuth } from '../composables/useAuth'

const { isLoggedIn, login } = useAuth()
const router = useRouter()
const username = ref('admin')
const password = ref('')
const error = ref('')
const loading = ref(false)

async function submit() {
  error.value = ''
  loading.value = true
  try {
    await login(username.value, password.value)
    router.push('/admin/posts')
  } catch (e: any) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}
</script>
