<template>
  <div class="vip-page">
    <main class="vip-main">
      <div class="vip-card">
        <div class="vip-icon">
          <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
            <rect x="3" y="11" width="18" height="11" rx="2" />
            <path d="M7 11V7a5 5 0 0110 0v4" stroke-linecap="round" />
          </svg>
        </div>

        <!-- 未激活：输码表单 -->
        <template v-if="!member">
          <h1>星球会员激活</h1>
          <p class="vip-desc">
            加入「码上岸」知识星球后，在星球内置顶帖获取兑换码，输入即可解锁本站全部付费专栏（有效期一年）。
          </p>
          <form class="vip-form" @submit.prevent="submit">
            <label>
              <span>兑换码</span>
              <input
                v-model="code"
                type="text"
                required
                autocomplete="off"
                spellcheck="false"
                placeholder="例如：MSAS-XXXX-XXXX"
              />
            </label>
            <label>
              <span>昵称（选填，便于星主核对）</span>
              <input v-model="nickname" type="text" maxlength="20" placeholder="你的星球昵称" />
            </label>
            <p v-if="error" class="vip-error">{{ error }}</p>
            <button type="submit" :disabled="loading" class="vip-btn">
              {{ loading ? "激活中…" : "激活会员" }}
            </button>
          </form>
          <p class="vip-hint">
            还不是星球成员？
            <a :href="joinUrl" target="_blank" rel="noopener">先加入知识星球</a>
          </p>
        </template>

        <!-- 已激活：成功态 -->
        <template v-else>
          <h1>会员已激活</h1>
          <p class="vip-desc">
            {{ nickname ? `欢迎，${nickname}` : "" }}你已解锁全部付费专栏章节，有效期一年。
          </p>
          <div class="vip-actions">
            <NuxtLink to="/column" class="vip-btn">去阅读付费专栏</NuxtLink>
            <button type="button" class="vip-ghost" @click="deactivate">退出会员登录</button>
          </div>
        </template>
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
import { zsxqConfig } from "~/data/zsxq";

const { readToken, fetchStatus, redeem, logout } = useVip();

const code = ref("");
const nickname = ref("");
const error = ref("");
const loading = ref(false);
const member = ref(false);
const memberNickname = ref("");

const joinUrl = zsxqConfig.joinUrl;

onMounted(async () => {
  if (!readToken()) return;
  const status = await fetchStatus();
  if (status.member) {
    member.value = true;
    memberNickname.value = status.nickname || "";
  }
});

async function submit() {
  if (loading.value) return;
  error.value = "";
  loading.value = true;
  const result = await redeem(code.value.trim(), nickname.value.trim());
  loading.value = false;
  if (result.ok) {
    member.value = true;
    memberNickname.value = result.nickname || "";
  } else {
    error.value = result.message || "兑换失败，请稍后再试";
  }
}

function deactivate() {
  logout();
  member.value = false;
  memberNickname.value = "";
  code.value = "";
}

useHead({ title: "星球会员激活 - 码上岸" });
</script>

<style scoped>
.vip-page {
  min-height: 100vh;
  background: linear-gradient(160deg, #f0f7fd 0%, #f5f6f7 55%);
  display: flex;
  align-items: flex-start;
  justify-content: center;
  padding: 60px 16px;
}
.vip-main {
  width: 100%;
  max-width: 460px;
}
.vip-card {
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 16px;
  padding: 36px 30px 30px;
  text-align: center;
  box-shadow: 0 10px 30px rgba(9, 98, 169, 0.08);
}
.vip-icon {
  width: 62px;
  height: 62px;
  margin: 0 auto 16px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--color-primary);
  background: #f0f8ff;
  border: 1px solid #d6e9f8;
}
.vip-card h1 {
  margin: 0 0 10px;
  font-size: 21px;
  font-weight: 700;
  color: #0f172a;
}
.vip-desc {
  margin: 0 0 22px;
  font-size: 14px;
  line-height: 1.9;
  color: #64748b;
}
.vip-form {
  text-align: left;
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.vip-form label {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.vip-form label span {
  font-size: 13px;
  color: #475569;
}
.vip-form input {
  height: 42px;
  padding: 0 14px;
  border: 1px solid #dbe4ec;
  border-radius: 10px;
  font-size: 14px;
  color: #0f172a;
  background: #f8fafc;
  transition: border-color 0.15s, box-shadow 0.15s;
}
.vip-form input:focus {
  outline: none;
  border-color: var(--color-primary);
  background: #fff;
  box-shadow: 0 0 0 3px rgba(9, 98, 169, 0.12);
}
.vip-error {
  margin: 0;
  font-size: 13px;
  color: #dc2626;
}
.vip-btn {
  height: 44px;
  border: none;
  border-radius: 999px;
  background: linear-gradient(135deg, var(--color-primary), #2b8ad4);
  color: #fff;
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
  text-decoration: none;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 6px 18px rgba(9, 98, 169, 0.3);
  transition: transform 0.15s, box-shadow 0.15s;
}
.vip-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 10px 24px rgba(9, 98, 169, 0.38);
}
.vip-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
  transform: none;
}
.vip-hint {
  margin: 18px 0 0;
  font-size: 13px;
  color: #94a3b8;
}
.vip-hint a {
  color: var(--color-primary);
  text-decoration: none;
}
.vip-hint a:hover {
  text-decoration: underline;
}
.vip-actions {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-top: 6px;
}
.vip-ghost {
  height: 40px;
  border: 1px solid #dbe4ec;
  border-radius: 999px;
  background: #fff;
  color: #64748b;
  font-size: 13px;
  cursor: pointer;
  transition: border-color 0.15s, color 0.15s;
}
.vip-ghost:hover {
  border-color: #cbd5e1;
  color: #334155;
}
</style>
