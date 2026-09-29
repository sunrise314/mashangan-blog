<template>
  <LayoutShell>
    <div class="topbar">
      <h2>AI 配图提供商配置</h2>
      <button class="btn btn-primary" :disabled="loading" @click="refresh">{{ loading ? '加载中…' : '刷新状态' }}</button>
    </div>
    <div class="msg msg-error" v-if="error">{{ error }}</div>
    <div class="msg msg-ok" v-if="saved">已保存</div>

    <div class="card-desc">
      AI 配图流水线依赖三个外部服务：<b>智谱 GLM</b> 解析文章产出配图点位、
      <b>SiliconFlow Kolors</b> 生成 AI 插画、<b>Pexels</b> 检索 CC0 商用图库。
      每个提供商独立配置，可单独测试连通性与查看额度/余额。
    </div>

    <div class="provider-grid">
      <div v-for="p in providers" :key="p.name" class="provider-card" :class="{ error: p.error }">
        <div class="provider-header">
          <div class="provider-name">
            <span class="status-dot" :class="p.configured && !p.error ? 'ok' : p.error ? 'bad' : 'warn'"></span>
            {{ p.displayName }}
          </div>
          <span class="status-tag" :class="p.configured && !p.error ? 'ok' : p.error ? 'bad' : 'warn'">
            {{ p.error ? '异常' : p.configured ? '已配置' : '未配置' }}
          </span>
        </div>

        <div class="provider-info">
          <div class="info-row" v-if="p.balance">
            <span class="info-label">余额/额度</span>
            <span class="info-value">{{ p.balance }}</span>
          </div>
          <div class="info-row" v-if="p.rateLimit">
            <span class="info-label">实时限流</span>
            <span class="info-value">{{ p.rateLimit }}</span>
          </div>
          <div class="info-row">
            <span class="info-label">当前 Key</span>
            <span class="info-value mono">{{ p.keyMasked }}</span>
          </div>
          <div class="info-row error-text" v-if="p.error">
            <span class="info-label">错误</span>
            <span class="info-value">{{ p.error }}</span>
          </div>
        </div>

        <div class="provider-actions">
          <input
            type="password"
            v-model="editKeys[p.name]"
            :placeholder="p.configured ? '输入新 Key 以替换' : '输入 API Key'"
          >
          <div class="btn-row">
            <button class="btn btn-ghost" :disabled="testing === p.name" @click="testProvider(p.name)">
              {{ testing === p.name ? '测试中…' : '测试连通' }}
            </button>
            <button class="btn btn-primary" :disabled="!editKeys[p.name] || saving === p.name" @click="saveKey(p.name)">
              {{ saving === p.name ? '保存中…' : '保存' }}
            </button>
          </div>
        </div>
      </div>
    </div>
  </LayoutShell>
</template>

<script setup lang="ts">
import { ref, onMounted, reactive } from 'vue'
import LayoutShell from '../../components/LayoutShell.vue'
import { aiConfigApi, type ProviderStatus } from '../../api/aiConfig'

const providers = ref<ProviderStatus[]>([])
const loading = ref(false)
const error = ref('')
const saved = ref('')
const testing = ref('')
const saving = ref('')
const editKeys = reactive<Record<string, string>>({})

async function refresh() {
  loading.value = true; error.value = ''; saved.value = ''
  try {
    providers.value = await aiConfigApi.list()
    // 同步当前配置到编辑框
    providers.value.forEach(p => { editKeys[p.name] = '' })
  } catch (e: any) { error.value = e.message }
  finally { loading.value = false }
}

async function testProvider(name: string) {
  testing.value = name; error.value = ''
  try {
    const updated = await aiConfigApi.test(name)
    // 只替换对应项
    const i = providers.value.findIndex(p => p.name === name)
    if (i >= 0) providers.value[i] = updated
  } catch (e: any) { error.value = e.message }
  finally { testing.value = '' }
}

async function saveKey(name: string) {
  const val = editKeys[name]
  if (!val) return
  saving.value = name; error.value = ''; saved.value = ''
  const patch: any = {}
  // 映射前端 name → 后端字段名
  const fieldMap: Record<string, keyof typeof patch> = {
    zhipu: 'zhipuKey',
    pexels: 'pexelsKey',
    siliconflow: 'siliconflowKey',
  }
  patch[fieldMap[name] || name] = val
  try {
    const fresh = await aiConfigApi.updateKeys(patch)
    providers.value = fresh
    editKeys[name] = ''
    saved.value = '已保存'
    setTimeout(() => { saved.value = '' }, 2000)
  } catch (e: any) { error.value = e.message }
  finally { saving.value = '' }
}

onMounted(refresh)
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
.provider-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(360px, 1fr));
  gap: 16px;
}
.provider-card {
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  transition: border-color .2s, box-shadow .2s;
}
.provider-card:hover {
  border-color: #94a3b8;
  box-shadow: 0 2px 8px rgba(0,0,0,0.06);
}
.provider-card.error {
  border-color: #f87171;
  background: #fef2f2;
}
.provider-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.provider-name {
  font-size: 15px;
  font-weight: 600;
  color: #1e293b;
  display: flex;
  align-items: center;
  gap: 8px;
}
.status-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  display: inline-block;
}
.status-dot.ok { background: #22c55e; }
.status-dot.warn { background: #f59e0b; }
.status-dot.bad { background: #ef4444; }
.status-tag {
  font-size: 12px;
  padding: 2px 10px;
  border-radius: 12px;
  font-weight: 500;
}
.status-tag.ok { background: #dcfce7; color: #15803d; }
.status-tag.warn { background: #fef3c7; color: #a16207; }
.status-tag.bad { background: #fee2e2; color: #b91c1c; }
.provider-info {
  background: #f8fafc;
  border-radius: 6px;
  padding: 10px 12px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.info-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 13px;
}
.info-label {
  color: #64748b;
  flex-shrink: 0;
}
.info-value {
  color: #1e293b;
  text-align: right;
  word-break: break-all;
  max-width: 70%;
}
.info-value.mono { font-family: monospace; font-size: 12px; color: #475569; }
.error-text .info-value { color: #dc2626; }
.provider-actions {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.btn-row {
  display: flex;
  gap: 8px;
}
.btn-row .btn {
  flex: 1;
}
</style>
