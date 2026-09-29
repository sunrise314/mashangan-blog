<script setup lang="ts">
// 自建后台：数据看板（访客分析）+ 内容流水线入口
// 纯客户端渲染（nuxt.config routeRules /admin ssr:false），口令存 localStorage

definePageMeta({ layout: false });

interface PvUv {
  pv: number;
  uv: number;
}
interface TopRow {
  k: string;
  c: number;
}
interface RecentRow {
  ts: number;
  type: string;
  path: string;
  title: string;
  ref: string;
  kw: string;
  dur: number;
  ip: string;
  region: string;
  isp: string;
  browser: string;
  os: string;
  device: string;
  vid?: string;
}
interface Summary {
  enabled: boolean;
  today?: PvUv;
  yesterday?: PvUv;
  online?: number;
  trend?: Array<{ d: string; pv: number; uv: number }>;
  topRefs?: TopRow[];
  topKws?: TopRow[];
  topPages?: TopRow[];
  topBrowsers?: TopRow[];
  topOss?: TopRow[];
  topDevices?: TopRow[];
  topRegions?: TopRow[];
  recent?: RecentRow[];
}
interface VisitRow {
  id: number;
  ts: number;
  type: string;
  path: string;
  title: string;
  ref: string;
  kw: string;
  dur: number;
  ip: string;
  region: string;
  isp: string;
  browser: string;
  os: string;
  device: string;
  extra: { href?: string } | null;
}

const TOKEN_KEY = "analytics_token";
const token = ref("");
const authed = ref(false);
const checking = ref(false);
const summary = ref<Summary | null>(null);
const loadErr = ref("");

// 访客轨迹查询
const vidQuery = ref("");
const vidLoading = ref(false);
const vidVisits = ref<VisitRow[] | null>(null);

const fmtTs = (ts: number | string): string =>
  new Date(Number(ts)).toLocaleString("zh-CN", {
    timeZone: "Asia/Shanghai",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  });
const fmtDur = (s: number): string => {
  if (!s) return "-";
  if (s < 60) return `${s}秒`;
  return `${Math.floor(s / 60)}分${s % 60}秒`;
};
const shortPath = (p: string, n = 28): string => (p.length > n ? `${p.slice(0, n)}…` : p);

async function api<T>(url: string): Promise<{ ok: boolean; status: number; data: T | null }> {
  try {
    const res = await fetch(url, { headers: { "x-analytics-token": token.value } });
    const data = res.status === 204 ? null : await res.json().catch(() => null);
    return { ok: res.ok, status: res.status, data };
  } catch {
    return { ok: false, status: 0, data: null };
  }
}

async function load(): Promise<void> {
  loadErr.value = "";
  const r = await api<Summary>("/api/analytics/summary");
  if (r.status === 401) {
    authed.value = false;
    return;
  }
  if (!r.ok || !r.data) {
    loadErr.value = "加载失败，请稍后重试";
    return;
  }
  if (r.data.enabled === false) {
    loadErr.value = "分析服务未启用（未配置 ANALYTICS_PG）";
    authed.value = true;
    return;
  }
  authed.value = true;
  summary.value = r.data;
}

function login(): void {
  checking.value = true;
  load().finally(() => {
    checking.value = false;
    if (authed.value) localStorage.setItem(TOKEN_KEY, token.value);
  });
}

async function queryVid(): Promise<void> {
  const vid = vidQuery.value.trim();
  if (!vid) return;
  vidLoading.value = true;
  const r = await api<{ enabled: boolean; visits: VisitRow[] }>(
    `/api/analytics/visitor/${encodeURIComponent(vid)}`,
  );
  vidLoading.value = false;
  if (r.status === 401) {
    authed.value = false;
    return;
  }
  vidVisits.value = r.data?.visits ?? [];
}

const maxTrendPv = computed(() => Math.max(1, ...(summary.value?.trend ?? []).map((t) => t.pv)));

let timer: ReturnType<typeof setInterval> | null = null;
onMounted(() => {
  token.value = localStorage.getItem(TOKEN_KEY) || "";
  if (token.value) load();
  timer = setInterval(() => {
    if (authed.value) load();
  }, 30_000);
});
onUnmounted(() => {
  if (timer) clearInterval(timer);
});

useHead({ title: "后台 · 数据看板", meta: [{ name: "robots", content: "noindex, nofollow" }] });
</script>

<template>
  <div class="flex min-h-screen bg-slate-100">
    <!-- 左侧导航 -->
    <aside class="flex w-52 shrink-0 flex-col bg-slate-900 text-slate-300">
      <div class="px-5 py-5 text-lg font-bold text-white">码上岸 · 后台</div>
      <nav class="flex flex-1 flex-col gap-1 px-3">
        <span class="rounded-md bg-slate-700/60 px-3 py-2 text-sm font-medium text-white"
          >📊 数据看板</span
        >
        <NuxtLink
          to="/studio"
          class="rounded-md px-3 py-2 text-sm hover:bg-slate-800 hover:text-white"
        >
          ⚙️ 内容流水线
        </NuxtLink>
        <NuxtLink to="/" class="rounded-md px-3 py-2 text-sm hover:bg-slate-800 hover:text-white">
          🌐 返回前台
        </NuxtLink>
      </nav>
      <div class="px-5 py-4 text-xs text-slate-500">halo-web admin</div>
    </aside>

    <!-- 主区域 -->
    <main class="flex-1 p-6">
      <!-- 口令门禁 -->
      <div v-if="!authed" class="mx-auto mt-24 max-w-sm rounded-xl bg-white p-8 shadow">
        <h1 class="mb-1 text-xl font-bold text-slate-800">后台登录</h1>
        <p class="mb-6 text-sm text-slate-500">输入访问口令以查看数据看板</p>
        <input
          v-model="token"
          type="password"
          class="mb-4 w-full rounded-lg border border-slate-300 px-3 py-2 outline-none focus:border-slate-500"
          placeholder="访问口令"
          @keyup.enter="login"
        />
        <button
          class="w-full rounded-lg bg-slate-900 py-2 font-medium text-white hover:bg-slate-700 disabled:opacity-50"
          :disabled="checking || !token"
          @click="login"
        >
          {{ checking ? "验证中…" : "进入看板" }}
        </button>
      </div>

      <!-- 看板内容 -->
      <template v-else>
        <div class="mb-4 flex items-center justify-between">
          <h1 class="text-xl font-bold text-slate-800">数据看板</h1>
          <span class="text-xs text-slate-400">每 30 秒自动刷新</span>
        </div>
        <p v-if="loadErr" class="mb-4 rounded-lg bg-amber-50 px-4 py-3 text-sm text-amber-700">
          {{ loadErr }}
        </p>

        <template v-if="summary">
          <!-- 概览卡片 -->
          <div class="mb-6 grid grid-cols-2 gap-4 lg:grid-cols-4">
            <div class="rounded-xl bg-white p-5 shadow-sm">
              <div class="text-sm text-slate-500">今日浏览（PV）</div>
              <div class="mt-1 text-3xl font-bold text-slate-800">{{ summary.today?.pv ?? 0 }}</div>
            </div>
            <div class="rounded-xl bg-white p-5 shadow-sm">
              <div class="text-sm text-slate-500">今日访客（UV）</div>
              <div class="mt-1 text-3xl font-bold text-slate-800">{{ summary.today?.uv ?? 0 }}</div>
            </div>
            <div class="rounded-xl bg-white p-5 shadow-sm">
              <div class="text-sm text-slate-500">昨日 PV / UV</div>
              <div class="mt-1 text-2xl font-bold text-slate-800">
                {{ summary.yesterday?.pv ?? 0 }} / {{ summary.yesterday?.uv ?? 0 }}
              </div>
            </div>
            <div class="rounded-xl bg-white p-5 shadow-sm">
              <div class="text-sm text-slate-500">实时在线（5 分钟内）</div>
              <div class="mt-1 flex items-center gap-2 text-3xl font-bold text-emerald-600">
                <span class="inline-block h-2.5 w-2.5 animate-pulse rounded-full bg-emerald-500" />
                {{ summary.online ?? 0 }}
              </div>
            </div>
          </div>

          <div class="grid gap-6 lg:grid-cols-2">
            <!-- 7 日趋势 -->
            <div class="rounded-xl bg-white p-5 shadow-sm">
              <h2 class="mb-4 font-semibold text-slate-700">近 7 日趋势</h2>
              <div class="flex h-40 items-end gap-3">
                <div
                  v-for="t in summary.trend ?? []"
                  :key="t.d"
                  class="flex flex-1 flex-col items-center gap-1"
                >
                  <div class="text-xs text-slate-500">{{ t.pv }}</div>
                  <div
                    class="w-full rounded-t bg-slate-800 transition-all hover:bg-slate-600"
                    :style="{ height: `${Math.max(4, (t.pv / maxTrendPv) * 110)}px` }"
                    :title="`${t.d}：PV ${t.pv} / UV ${t.uv}`"
                  />
                  <div class="text-[10px] text-slate-400">{{ t.d.slice(5) }}</div>
                </div>
                <div
                  v-if="!(summary.trend ?? []).length"
                  class="w-full text-center text-sm text-slate-400"
                >
                  暂无数据
                </div>
              </div>
            </div>

            <!-- TOP 来源 / 搜索词 -->
            <div class="grid gap-6 sm:grid-cols-2 lg:grid-cols-1 xl:grid-cols-2">
              <AdminTopList
                title="TOP 来源域名"
                :rows="summary.topRefs ?? []"
                empty="暂无外部来源"
              />
              <AdminTopList
                title="TOP 搜索关键词"
                :rows="summary.topKws ?? []"
                empty="暂无搜索词"
              />
            </div>
          </div>

          <!-- 设备与地区 -->
          <div class="mt-6 grid gap-4 sm:grid-cols-2 xl:grid-cols-5">
            <AdminTopList title="页面" :rows="summary.topPages ?? []" small empty="暂无" />
            <AdminTopList title="浏览器" :rows="summary.topBrowsers ?? []" small empty="暂无" />
            <AdminTopList title="操作系统" :rows="summary.topOss ?? []" small empty="暂无" />
            <AdminTopList title="设备类型" :rows="summary.topDevices ?? []" small empty="暂无" />
            <AdminTopList title="地区" :rows="summary.topRegions ?? []" small empty="暂无" />
          </div>

          <!-- 最新访问 -->
          <div class="mt-6 rounded-xl bg-white p-5 shadow-sm">
            <h2 class="mb-4 font-semibold text-slate-700">最新访问（50 条）</h2>
            <div class="overflow-x-auto">
              <table class="w-full text-left text-sm">
                <thead class="text-xs text-slate-400">
                  <tr>
                    <th class="py-2 pr-4">时间</th>
                    <th class="py-2 pr-4">页面</th>
                    <th class="py-2 pr-4">来源</th>
                    <th class="py-2 pr-4">地区 / ISP</th>
                    <th class="py-2 pr-4">浏览器</th>
                    <th class="py-2 pr-4">系统</th>
                    <th class="py-2 pr-4">设备</th>
                    <th class="py-2 pr-4">IP</th>
                    <th class="py-2 pr-4">停留</th>
                    <th class="py-2">vid</th>
                  </tr>
                </thead>
                <tbody class="text-slate-700">
                  <tr
                    v-for="v in summary.recent ?? []"
                    :key="v.id"
                    class="border-t border-slate-100"
                  >
                    <td class="whitespace-nowrap py-2 pr-4 text-slate-500">{{ fmtTs(v.ts) }}</td>
                    <td class="py-2 pr-4" :title="v.path">{{ shortPath(v.path) }}</td>
                    <td class="py-2 pr-4 text-slate-500" :title="v.ref">
                      {{
                        v.type === "click"
                          ? "外链点击"
                          : v.kw
                            ? `搜索:${v.kw}`
                            : v.ref
                              ? v.ref.replace(/^https?:\/\//, "").split("/")[0]
                              : "直接访问"
                      }}
                    </td>
                    <td class="whitespace-nowrap py-2 pr-4">
                      {{ v.region || "-" }}{{ v.isp ? ` · ${v.isp}` : "" }}
                    </td>
                    <td class="py-2 pr-4">{{ v.browser }}</td>
                    <td class="whitespace-nowrap py-2 pr-4">{{ v.os }}</td>
                    <td class="py-2 pr-4">{{ v.device }}</td>
                    <td class="py-2 pr-4 font-mono text-xs">{{ v.ip }}</td>
                    <td class="py-2 pr-4">{{ fmtDur(v.dur) }}</td>
                    <td class="py-2">
                      <button
                        v-if="v.vid"
                        class="font-mono text-xs text-blue-600 hover:underline"
                        @click="
                          vidQuery = v.vid;
                          queryVid();
                        "
                      >
                        {{ v.vid.slice(0, 8) }}…
                      </button>
                    </td>
                  </tr>
                </tbody>
              </table>
              <p v-if="!(summary.recent ?? []).length" class="text-center text-sm text-slate-400">
                暂无访问记录
              </p>
            </div>
          </div>

          <!-- 访客轨迹 -->
          <div class="mt-6 rounded-xl bg-white p-5 shadow-sm">
            <h2 class="mb-4 font-semibold text-slate-700">访客轨迹查询</h2>
            <div class="mb-4 flex gap-2">
              <input
                v-model="vidQuery"
                class="w-72 rounded-lg border border-slate-300 px-3 py-2 font-mono text-sm outline-none focus:border-slate-500"
                placeholder="输入访客 ID（vid）"
                @keyup.enter="queryVid"
              />
              <button
                class="rounded-lg bg-slate-900 px-4 py-2 text-sm font-medium text-white hover:bg-slate-700 disabled:opacity-50"
                :disabled="vidLoading"
                @click="queryVid"
              >
                {{ vidLoading ? "查询中…" : "查询" }}
              </button>
            </div>
            <ol
              v-if="vidVisits && vidVisits.length"
              class="ml-3 space-y-3 border-l-2 border-slate-200 pl-5"
            >
              <li v-for="v in vidVisits" :key="v.id" class="relative text-sm">
                <span class="absolute -left-[26px] top-1.5 h-2.5 w-2.5 rounded-full bg-slate-400" />
                <div class="text-slate-500">
                  {{ fmtTs(v.ts) }}
                  <span class="ml-2 rounded bg-slate-100 px-1.5 py-0.5 text-xs">{{ v.type }}</span>
                  <span v-if="v.dur" class="ml-2 text-xs">停留 {{ fmtDur(v.dur) }}</span>
                </div>
                <div class="mt-0.5 font-medium text-slate-800">
                  {{ v.path }}
                  <a
                    v-if="v.extra?.href"
                    :href="v.extra.href"
                    target="_blank"
                    rel="noopener"
                    class="ml-2 text-xs font-normal text-blue-600 hover:underline"
                  >
                    点击外链 → {{ v.extra.href }}
                  </a>
                </div>
                <div class="mt-0.5 text-xs text-slate-400">
                  {{ [v.region, v.isp].filter(Boolean).join(" · ") || "未知地区" }} |
                  {{ v.browser }} / {{ v.os }} / {{ v.device }} | IP {{ v.ip }}
                </div>
              </li>
            </ol>
            <p v-else-if="vidVisits" class="text-sm text-slate-400">该访客无记录</p>
          </div>
        </template>
      </template>
    </main>
  </div>
</template>
