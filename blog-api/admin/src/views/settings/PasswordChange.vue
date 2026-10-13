<template>
  <LayoutShell>
    <div class="topbar">
      <h2>修改密码</h2>
    </div>
    <div class="msg msg-error" v-if="error">{{ error }}</div>
    <div class="msg msg-ok" v-if="ok">{{ ok }}</div>

    <div class="card-desc">
      修改的是博客后台登录密码（即 <b>{{ username }}</b> 账号的密码）。
      保存后本会话仍有效，<b>下次登录请使用新密码</b>。
      数据看板与后台共用同一登录态，改密码后无需另行配置。
    </div>

    <div class="pwd-form">
      <label>
        <span>当前密码</span>
        <input type="text" v-model="oldPwd" autocomplete="off" placeholder="输入当前登录密码">
      </label>
      <label>
        <span>新密码</span>
        <input type="text" v-model="newPwd" autocomplete="off" placeholder="至少 6 位">
      </label>
      <label>
        <span>确认新密码</span>
        <input type="text" v-model="confirmPwd" autocomplete="off" placeholder="再输一遍新密码">
      </label>
      <div class="btn-row">
        <button class="btn btn-primary" :disabled="!canSubmit || saving" @click="submit">
          {{ saving ? '保存中…' : '修改密码' }}
        </button>
      </div>
    </div>
  </LayoutShell>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import LayoutShell from '../../components/LayoutShell.vue'
import { api } from '../../api/client'
import { useAuth } from '../../composables/useAuth'

const { token } = useAuth()

/** 从 JWT 里取用户名仅作展示；实际身份由后端 Token 校验 */
const username = computed(() => {
  try {
    const payload = JSON.parse(atob(token.value.split('.')[1] || ''))
    return payload.username || payload.sub || ''
  } catch { return '' }
})

const oldPwd = ref('')
const newPwd = ref('')
const confirmPwd = ref('')
const saving = ref(false)
const error = ref('')
const ok = ref('')

const canSubmit = computed(() => oldPwd.value && newPwd.value.length >= 6 && newPwd.value === confirmPwd.value)

async function submit() {
  if (newPwd.value !== confirmPwd.value) {
    error.value = '两次输入的新密码不一致'
    return
  }
  saving.value = true
  error.value = ''
  ok.value = ''
  try {
    const r = await api<{ message: string }>('POST', '/api/admin/auth/password', {
      oldPassword: oldPwd.value,
      newPassword: newPwd.value,
    })
    ok.value = r.message || '密码已修改'
    oldPwd.value = ''
    newPwd.value = ''
    confirmPwd.value = ''
  } catch (e: any) {
    error.value = e.message
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.card-desc {
  background: #f8fafc;
  border-left: 3px solid #2563eb;
  padding: 12px 16px;
  margin-bottom: 16px;
  border-radius: 4px;
  color: #475569;
  font-size: 14px;
  line-height: 1.6;
}
.pwd-form {
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 20px;
  max-width: 480px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.pwd-form label {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.pwd-form label span {
  font-size: 13px;
  color: #475569;
  font-weight: 500;
}
.pwd-form input {
  border: 1px solid #cbd5e1;
  border-radius: 6px;
  padding: 9px 12px;
  font-size: 14px;
  outline: none;
}
.pwd-form input:focus {
  border-color: #2563eb;
}
.btn-row {
  margin-top: 4px;
}
</style>
