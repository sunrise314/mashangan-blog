<template>
  <LayoutShell>
    <div class="topbar">
      <h2>SEO 提交</h2>
    </div>

    <div class="msg msg-error" v-if="error">{{ error }}</div>
    <div class="msg msg-ok" v-if="okMsg">{{ okMsg }}</div>

    <div class="card">
      <h3 style="margin-bottom:12px">推送配置</h3>
      <div class="grid-2">
        <div class="form-row">
          <label>站点 URL（校验与拼接用）</label>
          <input v-model="cfg.siteUrl" placeholder="https://www.mashangan.com">
        </div>
        <div class="form-row">
          <label>GSC 资源 ID（Google 网址检查跳转用）</label>
          <input v-model="cfg.gscResource" placeholder="sc-domain:www.mashangan.com">
        </div>
        <div class="form-row">
          <label>百度普通收录 token（ziyuan.baidu.com → 普通收录 → API 推送）</label>
          <input v-model="cfg.baiduToken" placeholder="未配置时「推送到百度」不可用">
        </div>
        <div class="form-row">
          <label>IndexNow key（须与站点根 /&lt;key&gt;.txt 文件一致）</label>
          <input v-model="cfg.indexnowKey">
        </div>
      </div>
      <button class="btn btn-primary" @click="saveConfig" :disabled="saving">{{ saving ? '保存中…' : '保存配置' }}</button>
      <p class="hint">Google 无公开提交 API，下方「GSC 检查」逐条深链到 Search Console 网址检查页，可请求编入索引。</p>
    </div>

    <div class="card">
      <h3 style="margin-bottom:12px">提交 URL</h3>
      <div class="form-row">
        <label>URL 列表（每行一个，须以站点 URL 开头，最多 100 条）</label>
        <textarea v-model="urlsText" rows="8" class="mono"></textarea>
      </div>
      <div class="toolbar">
        <button class="btn btn-ghost" @click="fill(20)" :disabled="loadingRecent">填充最近 20 篇</button>
        <button class="btn btn-ghost" @click="fill(50)" :disabled="loadingRecent">填充最近 50 篇</button>
        <span class="hint" style="margin:0">共 {{ urlCount }} 条 URL</span>
      </div>
      <div class="toolbar">
        <button class="btn btn-primary" @click="push('baidu')" :disabled="pushing">推送到百度</button>
        <button class="btn btn-primary" @click="push('indexnow')" :disabled="pushing">推送到 Bing / IndexNow</button>
      </div>
      <p class="hint" v-if="pushResult">{{ pushResult }}</p>
    </div>

    <div class="card">
      <h3 style="margin-bottom:4px">Google Search Console</h3>
      <p class="hint" style="margin-top:0">
        Google 不提供收录提交 API，且服务器侧网络不可达 Google——逐条点「GSC 检查」在新标签打开网址检查页，验证收录状态或请求编入索引。
      </p>
      <div v-if="urlList.length === 0" class="hint">在上方填写或填充 URL 后，这里会列出每条的 GSC 检查入口。</div>
      <div v-for="u in urlList" :key="u" class="gsc-row">
        <span class="gsc-url">{{ u }}</span>
        <a class="btn btn-ghost btn-sm" :href="gscInspectUrl(u)" target="_blank" rel="noopener">GSC 检查</a>
      </div>
    </div>
  </LayoutShell>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import LayoutShell from '../../components/LayoutShell.vue'
import { seoApi, type SeoPushConfig } from '../../api/seoPush'

const cfg = ref<SeoPushConfig>({ siteUrl: '', gscResource: '', baiduToken: '', indexnowKey: '' })
const urlsText = ref('')
const saving = ref(false)
const pushing = ref(false)
const loadingRecent = ref(false)
const error = ref('')
const okMsg = ref('')
const pushResult = ref('')

const urlList = computed(() =>
  urlsText.value.split('\n').map((s) => s.trim()).filter(Boolean),
)
const urlCount = computed(() => urlList.value.length)

async function load() {
  try {
    cfg.value = await seoApi.config()
  } catch (e: any) {
    error.value = e.message
  }
}
load()

async function saveConfig() {
  error.value = ''; okMsg.value = ''; saving.value = true
  try {
    await seoApi.saveConfig(cfg.value)
    okMsg.value = '配置已保存'
  } catch (e: any) { error.value = e.message }
  finally { saving.value = false }
}

async function fill(limit: number) {
  error.value = ''; okMsg.value = ''; loadingRecent.value = true
  try {
    const r = await seoApi.recent(limit)
    urlsText.value = r.items.map((i) => i.url).join('\n')
  } catch (e: any) { error.value = e.message }
  finally { loadingRecent.value = false }
}

async function push(channel: 'baidu' | 'indexnow') {
  error.value = ''; okMsg.value = ''; pushResult.value = ''
  const urls = urlList.value
  if (urls.length === 0) {
    error.value = '请先填写或填充 URL'
    return
  }
  pushing.value = true
  try {
    const r = channel === 'baidu' ? await seoApi.pushBaidu(urls) : await seoApi.pushIndexnow(urls)
    pushResult.value = r.message
    if (r.ok) okMsg.value = r.message
    else error.value = r.message
  } catch (e: any) { error.value = e.message }
  finally { pushing.value = false }
}

function gscInspectUrl(u: string): string {
  const site = cfg.value.siteUrl.replace(/\/+$/, '')
  const rid = cfg.value.gscResource.trim() || 'sc-domain:' + site.replace(/^https?:\/\//, '')
  return 'https://search.google.com/search-console/inspect?resource_id='
    + encodeURIComponent(rid) + '&id=' + encodeURIComponent(u)
}
</script>

<style scoped>
.hint {
  color: #64748b;
  font-size: 12px;
  margin-top: 8px;
}
.mono {
  font-family: monospace;
  font-size: 12px;
}
.gsc-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
  padding: 6px 0;
  border-bottom: 1px solid #f1f5f9;
}
.gsc-url {
  font-family: monospace;
  font-size: 12px;
  color: #475569;
  word-break: break-all;
}
</style>
