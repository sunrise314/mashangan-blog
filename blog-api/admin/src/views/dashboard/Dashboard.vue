<template>
  <LayoutShell>
    <div class="topbar">
      <h2>数据看板</h2>
      <span class="muted">每 30 秒自动刷新</span>
    </div>
    <div class="msg msg-error" v-if="loadErr">{{ loadErr }}</div>

    <div v-if="!summary && !loadErr" class="loading">加载中…</div>

    <template v-else-if="summary">
      <!-- 概览卡片 -->
      <div class="grid grid-4">
        <div class="card stat">
          <div class="stat-label">今日浏览（PV）</div>
          <div class="stat-value">{{ summary.today?.pv ?? 0 }}</div>
        </div>
        <div class="card stat">
          <div class="stat-label">今日访客（UV）</div>
          <div class="stat-value">{{ summary.today?.uv ?? 0 }}</div>
        </div>
        <div class="card stat">
          <div class="stat-label">昨日 PV / UV</div>
          <div class="stat-value small">{{ summary.yesterday?.pv ?? 0 }} / {{ summary.yesterday?.uv ?? 0 }}</div>
        </div>
        <div class="card stat">
          <div class="stat-label">实时在线（5 分钟内）</div>
          <div class="stat-value online">
            <span class="dot-online"></span>
            {{ summary.online ?? 0 }}
          </div>
        </div>
      </div>

      <div class="grid grid-2">
        <!-- 7 日趋势 -->
        <div class="card">
          <h3>近 7 日趋势</h3>
          <div class="trend-chart">
            <div v-for="t in summary.trend ?? []" :key="t.d" class="trend-bar">
              <div class="trend-num">{{ t.pv }}</div>
              <div class="trend-fill" :style="{ height: `${Math.max(4, (t.pv / maxTrendPv) * 110)}px` }" :title="`${t.d}：PV ${t.pv} / UV ${t.uv}`"></div>
              <div class="trend-date">{{ t.d.slice(5) }}</div>
            </div>
            <div v-if="!(summary.trend ?? []).length" class="empty">暂无数据</div>
          </div>
        </div>

        <!-- TOP 来源 / 搜索引擎 -->
        <div class="grid grid-2-inner">
          <TopList title="TOP 来源域名" :rows="summary.topRefs ?? []" empty="暂无外部来源" />
          <TopList title="搜索引擎来源" :rows="summary.topEngines ?? []" empty="暂无搜索引擎来源" />
        </div>
      </div>

      <!-- 设备与地区 -->
      <div class="grid grid-5">
        <TopList title="页面" :rows="summary.topPages ?? []" small empty="暂无" />
        <TopList title="浏览器" :rows="summary.topBrowsers ?? []" small empty="暂无" />
        <TopList title="操作系统" :rows="summary.topOss ?? []" small empty="暂无" />
        <TopList title="设备类型" :rows="summary.topDevices ?? []" small empty="暂无" />
        <TopList title="地区" :rows="summary.topRegions ?? []" small empty="暂无" />
      </div>

      <!-- 最新访问 -->
      <div class="card">
        <h3>最新访问 <span class="muted">共 {{ recentTotal }} 条</span></h3>
        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>时间</th>
                <th>页面</th>
                <th>来源</th>
                <th>地区 / ISP</th>
                <th>浏览器</th>
                <th>系统</th>
                <th>设备</th>
                <th>IP</th>
                <th>停留</th>
                <th>vid</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="v in recentRows" :key="v.id">
                <td class="nowrap muted">{{ fmtTs(v.ts) }}</td>
                <td :title="v.path">{{ shortPath(v.path) }}</td>
                <td class="muted" :title="v.ref">
                  {{ v.type === 'click' ? '外链点击' : v.kw ? `搜索:${v.kw}` : v.ref ? v.ref.replace(/^https?:\/\//, '').split('/')[0] : '直接访问' }}
                </td>
                <td class="nowrap">{{ v.region || '-' }}{{ v.isp ? ` · ${v.isp}` : '' }}</td>
                <td>{{ v.browser }}</td>
                <td class="nowrap">{{ v.os }}</td>
                <td>{{ v.device }}</td>
                <td class="mono">{{ v.ip }}</td>
                <td>{{ fmtDur(v.dur) }}</td>
                <td>
                  <button v-if="v.vid" class="vid-link" @click="vidQuery = v.vid; queryVid()">
                    {{ v.vid.slice(0, 8) }}…
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
          <p v-if="recentErr" class="empty-center">{{ recentErr }}</p>
          <p v-else-if="!recentRows.length" class="empty-center">暂无访问记录</p>
        </div>
        <div class="pager">
          <button class="btn btn-ghost" :disabled="recentPage <= 1 || recentLoading" @click="recentPage--; loadRecent()">上一页</button>
          <span class="muted">第 {{ recentPage }} / {{ recentPages }} 页 · 每页 {{ recentSize }} 条</span>
          <button class="btn btn-ghost" :disabled="recentPage >= recentPages || recentLoading" @click="recentPage++; loadRecent()">下一页</button>
          <span v-if="recentLoading" class="muted">刷新中…</span>
        </div>
      </div>

      <!-- 访客轨迹 -->
      <div class="card">
        <h3>访客轨迹查询</h3>
        <div class="vid-form">
          <input v-model="vidQuery" placeholder="输入访客 ID（vid）" @keyup.enter="queryVid" class="vid-input">
          <button class="btn btn-primary" :disabled="vidLoading" @click="queryVid">
            {{ vidLoading ? '查询中…' : '查询' }}
          </button>
        </div>
        <ol v-if="vidVisits && vidVisits.length" class="timeline">
          <li v-for="v in vidVisits" :key="v.id">
            <span class="timeline-dot"></span>
            <div class="muted">
              {{ fmtTs(v.ts) }}
              <span class="tag">{{ v.type }}</span>
              <span v-if="v.dur" class="muted">停留 {{ fmtDur(v.dur) }}</span>
            </div>
            <div class="timeline-path">
              {{ v.path }}
              <a v-if="v.extra?.href" :href="v.extra.href" target="_blank" rel="noopener" class="ext-link">
                点击外链 → {{ v.extra.href }}
              </a>
            </div>
            <div class="muted small">
              {{ [v.region, v.isp].filter(Boolean).join(' · ') || '未知地区' }} |
              {{ v.browser }} / {{ v.os }} / {{ v.device }} | IP {{ v.ip }}
            </div>
          </li>
        </ol>
        <p v-else-if="vidVisits" class="empty">该访客无记录</p>
      </div>
    </template>
  </LayoutShell>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import LayoutShell from '../../components/LayoutShell.vue'
import TopList from './TopList.vue'
import { useAuth } from '../../composables/useAuth'

interface PvUv { pv: number; uv: number }
interface TopRow { k: string; c: number }
interface RecentRow {
  id: number; ts: number; type: string; path: string; title: string;
  ref: string; kw: string; dur: number; ip: string; region: string;
  isp: string; browser: string; os: string; device: string; vid?: string;
}
interface VisitRow {
  id: number; ts: number; type: string; path: string; title: string;
  ref: string; kw: string; dur: number; ip: string; region: string;
  isp: string; browser: string; os: string; device: string;
  extra: { href?: string } | null;
}
interface Summary {
  enabled: boolean
  today?: PvUv
  yesterday?: PvUv
  online?: number
  trend?: Array<{ d: string; pv: number; uv: number }>
  topRefs?: TopRow[]
  topEngines?: TopRow[]
  topPages?: TopRow[]
  topBrowsers?: TopRow[]
  topOss?: TopRow[]
  topDevices?: TopRow[]
  topRegions?: TopRow[]
}

const { getAnalyticsToken } = useAuth()
const summary = ref<Summary | null>(null)
const loadErr = ref('')
const vidQuery = ref('')
const vidLoading = ref(false)
const vidVisits = ref<VisitRow[] | null>(null)
const recentRows = ref<RecentRow[]>([])
const recentPage = ref(1)
const recentSize = 20
const recentTotal = ref(0)
const recentLoading = ref(false)
const recentErr = ref('')

const recentPages = computed(() => Math.max(1, Math.ceil(recentTotal.value / recentSize)))

const fmtTs = (ts: number) =>
  new Date(Number(ts)).toLocaleString('zh-CN', {
    timeZone: 'Asia/Shanghai', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', hour12: false,
  })

const fmtDur = (s: number) => {
  if (!s) return '-'
  if (s < 60) return `${s}秒`
  return `${Math.floor(s / 60)}分${s % 60}秒`
}

const shortPath = (p: string, n = 28) => (p.length > n ? `${p.slice(0, n)}…` : p)

const maxTrendPv = computed(() => Math.max(1, ...(summary.value?.trend ?? []).map(t => t.pv)))

async function fetchJson<T>(url: string): Promise<{ ok: boolean; status: number; data: T | null }> {
  try {
    const res = await fetch(url, { headers: { 'x-analytics-token': getAnalyticsToken() } })
    const data = res.status === 204 ? null : await res.json().catch(() => null)
    return { ok: res.ok, status: res.status, data }
  } catch {
    return { ok: false, status: 0, data: null }
  }
}

async function load() {
  loadErr.value = ''
  const r = await fetchJson<Summary>('/api/analytics/summary')
  if (r.status === 401) {
    loadErr.value = '看板接口未授权（请重新登录后台）'
    return
  }
  if (!r.ok || !r.data) {
    loadErr.value = '加载失败，请稍后重试'
    return
  }
  if (r.data.enabled === false) {
    loadErr.value = '分析服务未启用（未配置 ANALYTICS_PG）'
    return
  }
  summary.value = r.data
}

async function loadRecent() {
  recentLoading.value = true
  recentErr.value = ''
  const r = await fetchJson<{ enabled: boolean; total: number; visits: RecentRow[] }>(
    `/api/analytics/visits?page=${recentPage.value}&size=${recentSize}`,
  )
  recentLoading.value = false
  if (r.status === 401) {
    recentErr.value = '看板接口未授权（请重新登录后台）'
    return
  }
  if (!r.ok || !r.data || r.data.enabled === false) {
    recentErr.value = '加载失败，请稍后重试'
    return
  }
  recentRows.value = r.data.visits ?? []
  recentTotal.value = r.data.total ?? 0
  // 数据减少导致当前页越界时，回退到最后一页重新拉取
  if (recentPage.value > recentPages.value) {
    recentPage.value = recentPages.value
    loadRecent()
  }
}

async function queryVid() {
  const vid = vidQuery.value.trim()
  if (!vid) return
  vidLoading.value = true
  const r = await fetchJson<{ enabled: boolean; visits: VisitRow[] }>(
    `/api/analytics/visitor/${encodeURIComponent(vid)}`,
  )
  vidLoading.value = false
  if (r.status === 401) {
    loadErr.value = '看板接口未授权'
    return
  }
  vidVisits.value = r.data?.visits ?? []
}

let timer: ReturnType<typeof setInterval> | null = null
onMounted(() => {
  load()
  loadRecent()
  timer = setInterval(() => { load(); loadRecent() }, 30_000)
})
onUnmounted(() => { if (timer) clearInterval(timer) })
</script>

<style scoped>
.topbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.muted { color: #888; font-size: 13px; }
.loading { text-align: center; padding: 60px; color: #888; }
.grid { display: grid; gap: 16px; }
.grid-4 { grid-template-columns: repeat(4, 1fr); margin-bottom: 16px; }
.grid-2 { grid-template-columns: 1fr 1fr; margin-bottom: 16px; }
.grid-2-inner { grid-template-columns: 1fr 1fr; }
.grid-5 { grid-template-columns: repeat(5, 1fr); margin-bottom: 16px; }
.card { background: #fff; border-radius: 8px; padding: 16px; box-shadow: 0 1px 3px rgba(0,0,0,.05); }
.card h3 { margin: 0 0 12px 0; font-size: 14px; color: #444; }
.stat .stat-label { font-size: 12px; color: #888; }
.stat .stat-value { font-size: 28px; font-weight: 700; color: #1e293b; margin-top: 4px; }
.stat .stat-value.small { font-size: 22px; }
.stat .stat-value.online { color: #10b981; display: flex; align-items: center; gap: 8px; }
.dot-online { display: inline-block; width: 10px; height: 10px; border-radius: 50%; background: #10b981; animation: pulse 1.5s infinite; }
@keyframes pulse { 0%,100% { opacity: 1; } 50% { opacity: 0.5; } }
.trend-chart { display: flex; align-items: flex-end; gap: 8px; height: 130px; }
.trend-bar { flex: 1; display: flex; flex-direction: column; align-items: center; gap: 4px; }
.trend-num { font-size: 11px; color: #888; }
.trend-fill { width: 100%; background: #1e293b; border-radius: 4px 4px 0 0; min-height: 4px; }
.trend-date { font-size: 10px; color: #aaa; }
.empty { text-align: center; color: #aaa; padding: 20px; font-size: 13px; }
.empty-center { text-align: center; color: #aaa; padding: 12px; font-size: 13px; }
.table-wrap { overflow-x: auto; }
.pager { display: flex; align-items: center; gap: 12px; justify-content: flex-end; margin-top: 12px; }
table { width: 100%; border-collapse: collapse; font-size: 13px; }
th { text-align: left; padding: 6px 12px 6px 0; color: #aaa; font-weight: 500; border-bottom: 1px solid #eee; font-size: 11px; }
td { padding: 6px 12px 6px 0; border-bottom: 1px solid #f5f5f5; color: #444; }
.nowrap { white-space: nowrap; }
.mono { font-family: monospace; font-size: 12px; }
.vid-link { background: none; border: none; color: #2563eb; cursor: pointer; font-family: monospace; font-size: 12px; padding: 0; }
.vid-link:hover { text-decoration: underline; }
.vid-form { display: flex; gap: 8px; margin-bottom: 12px; }
.vid-input { flex: 0 0 280px; padding: 6px 10px; border: 1px solid #ddd; border-radius: 4px; font-family: monospace; font-size: 13px; }
.timeline { list-style: none; padding: 0; margin: 0 0 0 12px; border-left: 2px solid #eee; }
.timeline li { position: relative; padding-left: 16px; margin-bottom: 12px; }
.timeline-dot { position: absolute; left: -7px; top: 6px; width: 8px; height: 8px; border-radius: 50%; background: #aaa; }
.timeline-path { font-weight: 500; color: #1e293b; margin: 2px 0; }
.ext-link { margin-left: 8px; color: #2563eb; text-decoration: none; font-size: 12px; }
.ext-link:hover { text-decoration: underline; }
.tag { display: inline-block; background: #f1f5f9; padding: 1px 6px; border-radius: 3px; font-size: 11px; margin: 0 4px; }
.small { font-size: 12px; }
.top-list { padding: 14px; }
.small-title { font-size: 12px; }
.top-ul { list-style: none; padding: 0; margin: 0; }
.top-li { margin-bottom: 6px; }
.top-row { display: flex; justify-content: space-between; align-items: center; gap: 8px; }
.top-k { white-space: nowrap; overflow: hidden; text-overflow: ellipsis; color: #444; font-size: 13px; }
.top-c { color: #aaa; font-size: 11px; }
.top-bar-bg { height: 3px; background: #f1f5f9; border-radius: 2px; margin-top: 2px; }
.top-bar-fill { height: 3px; background: #64748b; border-radius: 2px; }
@media (max-width: 1024px) {
  .grid-4 { grid-template-columns: repeat(2, 1fr); }
  .grid-5 { grid-template-columns: repeat(2, 1fr); }
}
</style>
