<template>
  <div class="min-h-screen flex flex-col">
    <!-- 顶部导航：半透明毛玻璃，固定定位 -->
    <header class="sticky top-0 z-50 bg-white/85 backdrop-blur border-b border-slate-200">
      <div class="max-w-5xl mx-auto px-4 h-14 flex items-center justify-between gap-4">
        <!-- 品牌区 -->
        <NuxtLink
          to="/"
          class="flex items-center gap-2 font-bold shrink-0"
          style="color: var(--color-primary)"
        >
          <img
            v-if="siteLogo"
            :src="siteLogo"
            :alt="siteTitle"
            referrerpolicy="no-referrer"
            class="h-7 w-auto"
          />
          <svg
            v-else
            class="w-7 h-7"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="2"
          >
            <path
              d="M12 2L2 7l10 5 10-5-10-5zM2 17l10 5 10-5M2 12l10 5 10-5"
              stroke-linecap="round"
              stroke-linejoin="round"
            />
          </svg>
          <span class="text-lg">{{ siteTitle }}</span>
        </NuxtLink>

        <!-- 桌面端导航 -->
        <nav
          class="hidden lg:flex items-center gap-1 text-sm whitespace-nowrap text-slate-600"
        >
          <template v-for="item in menuItems" :key="item.metadata.name">
            <!-- 含子菜单 -->
            <div v-if="item.children.length > 0" class="relative group">
              <button
                type="button"
                class="flex items-center gap-1 px-2 lg:px-2.5 rounded-md transition-colors hover:text-[#0562a9]"
              >
                {{ itemName(item) }}
                <svg
                  class="w-4 h-4"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="2"
                >
                  <path d="M6 9l6 6 6-6" stroke-linecap="round" stroke-linejoin="round" />
                </svg>
              </button>
              <div
                class="hidden group-hover:block group-focus-within:block absolute right-0 top-full pt-1 z-50"
              >
                <div
                  class="min-w-[180px] bg-white rounded-lg shadow-lg border border-slate-200 py-1"
                >
                  <template v-for="child in item.children" :key="child.metadata.name">
                    <NuxtLink
                      v-if="!isExternal(itemHref(child))"
                      :to="normalizeHref(itemHref(child))"
                      @mouseenter="prefetchOnHover(child)"
                      class="block px-4 py-2 text-sm text-slate-600 hover:text-[#0562a9] hover:bg-slate-50 whitespace-nowrap"
                    >
                      {{ itemName(child) }}
                    </NuxtLink>
                    <a
                      v-else
                      :href="itemHref(child)"
                      :target="child.spec.target || '_blank'"
                      rel="noopener noreferrer"
                      class="block px-4 py-2 text-sm text-slate-600 hover:text-[#0562a9] hover:bg-slate-50 whitespace-nowrap"
                    >
                      {{ itemName(child) }}
                    </a>
                  </template>
                </div>
              </div>
            </div>

            <!-- 普通站内链接 -->
            <NuxtLink
              v-else-if="!isExternal(itemHref(item))"
              :to="normalizeHref(itemHref(item))"
              @mouseenter="prefetchOnHover(item)"
              :class="[
                'px-2 lg:px-2.5 rounded-md transition-colors hover:text-[#0562a9]',
                isActive(itemHref(item)) ? 'text-[#0562a9] font-medium' : '',
              ]"
            >
              {{ itemName(item) }}
            </NuxtLink>

            <!-- 外部链接 -->
            <a
              v-else
              :href="itemHref(item)"
              :target="item.spec.target || '_blank'"
              rel="noopener noreferrer"
              class="px-2 lg:px-2.5 rounded-md transition-colors hover:text-[#0562a9]"
            >
              {{ itemName(item) }}
            </a>
          </template>
        </nav>

        <!-- 桌面端搜索入口 -->
        <form class="hidden lg:flex items-center" role="search" @submit.prevent="submitSearch">
          <div class="relative">
            <input
              v-model="searchKeyword"
              type="search"
              placeholder="搜索教程"
              aria-label="搜索教程"
              class="w-44 lg:w-52 h-8 pl-8 pr-3 rounded-full bg-slate-100 border border-transparent text-sm text-slate-700 placeholder:text-slate-400 focus:outline-none focus:bg-white focus:border-sky-300 transition-all"
            />
            <svg
              class="absolute left-2.5 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400 pointer-events-none"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
            >
              <circle cx="11" cy="11" r="7" />
              <path d="M21 21l-4.3-4.3" stroke-linecap="round" />
            </svg>
          </div>
        </form>

        <!-- 移动端菜单按钮 -->
        <button
          type="button"
          class="lg:hidden p-2 -mr-2 text-slate-600 hover:text-[#0562a9]"
          aria-label="切换导航菜单"
          @click="mobileOpen = !mobileOpen"
        >
          <svg
            v-if="!mobileOpen"
            class="w-6 h-6"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="2"
          >
            <path d="M4 6h16M4 12h16M4 18h16" stroke-linecap="round" />
          </svg>
          <svg
            v-else
            class="w-6 h-6"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="2"
          >
            <path d="M6 18L18 6M6 6l12 12" stroke-linecap="round" />
          </svg>
        </button>
      </div>

      <!-- 移动端导航面板 -->
      <div v-if="mobileOpen" class="lg:hidden border-t border-slate-200 bg-white">
        <!-- 移动端搜索（桌面搜索框为 hidden lg:flex，移动端在此补齐入口） -->
        <form class="px-4 pt-3 pb-1" role="search" @submit.prevent="submitSearch">
          <div class="relative">
            <input
              v-model="searchKeyword"
              type="search"
              placeholder="搜索教程"
              aria-label="搜索教程"
              class="w-full h-9 pl-8 pr-3 rounded-full bg-slate-100 border border-transparent text-sm text-slate-700 placeholder:text-slate-400 focus:outline-none focus:bg-white focus:border-sky-300 transition-all"
            />
            <svg
              class="absolute left-2.5 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400 pointer-events-none"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
            >
              <circle cx="11" cy="11" r="7" />
              <path d="M21 21l-4.3-4.3" stroke-linecap="round" />
            </svg>
          </div>
        </form>

        <nav class="max-w-5xl mx-auto px-4 py-2 flex flex-col text-slate-600">
          <template v-for="item in menuItems" :key="`m-${item.metadata.name}`">
            <div v-if="item.children.length > 0" class="border-b border-slate-100 last:border-0">
              <div class="px-2 pt-2 pb-1 text-xs font-medium text-slate-400">
                {{ itemName(item) }}
              </div>
              <template v-for="child in item.children" :key="child.metadata.name">
                <NuxtLink
                  v-if="!isExternal(itemHref(child))"
                  :to="normalizeHref(itemHref(child))"
                  class="block pl-6 pr-2 py-2 rounded-md hover:text-[#0562a9]"
                >
                  {{ itemName(child) }}
                </NuxtLink>
                <a
                  v-else
                  :href="itemHref(child)"
                  :target="child.spec.target || '_blank'"
                  rel="noopener noreferrer"
                  class="block pl-6 pr-2 py-2 rounded-md hover:text-[#0562a9]"
                >
                  {{ itemName(child) }}
                </a>
              </template>
            </div>
            <NuxtLink
              v-else-if="!isExternal(itemHref(item))"
              :to="normalizeHref(itemHref(item))"
              :class="[
                'px-2 py-2 rounded-md border-b border-slate-100 last:border-0 hover:text-[#0562a9]',
                isActive(itemHref(item)) ? 'text-[#0562a9] font-medium' : '',
              ]"
            >
              {{ itemName(item) }}
            </NuxtLink>
            <a
              v-else
              :href="itemHref(item)"
              :target="item.spec.target || '_blank'"
              rel="noopener noreferrer"
              class="px-2 py-2 rounded-md border-b border-slate-100 last:border-0 hover:text-[#0562a9]"
            >
              {{ itemName(item) }}
            </a>
          </template>
        </nav>
      </div>
    </header>

    <!-- 主体内容 -->
    <main class="flex-1">
      <slot />
    </main>

    <!-- 页脚 -->
    <footer class="bg-white border-t border-slate-200 py-10 mt-12">
      <div class="max-w-5xl mx-auto px-4 text-center text-slate-500 text-sm space-y-4">
        <!-- 品牌行 -->
        <div
          class="flex items-center justify-center gap-2 font-bold"
          style="color: var(--color-primary)"
        >
          <img
            v-if="siteLogo"
            :src="siteLogo"
            :alt="siteTitle"
            referrerpolicy="no-referrer"
            class="h-7 w-auto"
          />
          <svg
            v-else
            class="w-6 h-6"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="2"
          >
            <path
              d="M12 2L2 7l10 5 10-5-10-5zM2 17l10 5 10-5M2 12l10 5 10-5"
              stroke-linecap="round"
              stroke-linejoin="round"
            />
          </svg>
          <span class="text-base">{{ siteTitle }}</span>
        </div>

        <!-- 社交链接 -->
        <div v-if="socialLinks.length" class="flex items-center justify-center gap-4">
          <a
            v-for="s in socialLinks"
            :key="s.id"
            :href="s.url"
            :target="s.platform === 'email' ? undefined : '_blank'"
            rel="noopener noreferrer"
            class="text-slate-400 hover:text-[#0562a9] transition-colors"
            :title="s.label"
          >
            {{ s.label }}
          </a>
        </div>
        <p>{{ footerText }}</p>
        <div
          v-if="siteConfig?.beianIcp || siteConfig?.beianPublicSecurity"
          class="flex flex-wrap items-center justify-center gap-x-4 gap-y-1 text-xs text-slate-400"
        >
          <a
            v-if="siteConfig?.beianIcp"
            href="https://beian.miit.gov.cn"
            target="_blank"
            rel="noopener noreferrer"
            >{{ siteConfig.beianIcp }}</a
          >
          <span v-if="siteConfig?.beianPublicSecurity">{{ siteConfig.beianPublicSecurity }}</span>
        </div>
      </div>
    </footer>

    <!-- 返回顶部（长页刚需，全站可用） -->
    <BackToTop />
  </div>
</template>

<script setup lang="ts">
import type { HaloMenu, HaloMenuItem } from "~/types/halo";
import type { SiteConfig } from "~/composables/useSiteConfig";

const config = useRuntimeConfig();
const route = useRoute();
const { getPrimaryMenu } = useHaloApi();

// 站点配置：优先用后台 site-config，回退 nuxt.config 默认值
const siteConfigData = useSiteConfig();
const siteConfig = computed<SiteConfig | null>(() => siteConfigData.value?.config ?? null);
const socialLinks = computed(() => siteConfigData.value?.socialLinks ?? []);
const siteTitle = computed(
  () => siteConfig.value?.title || (config.public.siteTitle as string) || "码上岸",
);
const siteLogo = computed(
  () => siteConfig.value?.logoUrl || (config.public.siteLogo as string) || "",
);
const footerText = computed(
  () =>
    siteConfig.value?.footerText ||
    `© ${new Date().getFullYear()} ${siteTitle.value} · Powered by Halo`,
);
const haloApiBase = config.public.haloApiBase as string;

// SEO 元信息 + 统计代码注入 <head>
const siteUrl = ((config.public.siteUrl as string) || "").replace(/\/+$/, "");
useHead(() => {
  const c = siteConfig.value;
  const head: Record<string, any> = {
    title: c?.title || (config.public.siteTitle as string) || "码上岸",
  };
  if (c?.seoDescription) head.meta = [{ name: "description", content: c.seoDescription }];
  if (c?.seoKeywords)
    head.meta = [...(head.meta || []), { name: "keywords", content: c.seoKeywords }];
  // OG 基础：og:site_name/og:locale 全站统一，og:type=website 由详情页覆盖为 article
  head.meta = [
    ...(head.meta || []),
    { property: "og:site_name", content: siteTitle.value },
    { property: "og:locale", content: "zh_CN" },
    { property: "og:type", content: "website" },
  ];
  // canonical：规范地址 = 站点根 + 当前路径（不含 query，避免搜索页参数被收录）
  head.link = [
    { rel: "alternate", type: "application/rss+xml", title: "RSS 订阅", href: "/rss.xml" },
    ...(siteUrl
      ? [{ rel: "canonical", href: `${siteUrl}${route.path === "/" ? "/" : route.path}` }]
      : []),
    ...(c?.faviconUrl ? [{ rel: "icon", type: "image/x-icon", href: c.faviconUrl }] : []),
  ];
  // WebSite 结构化数据（站点级，详情页会追加 BlogPosting/BreadcrumbList）
  if (siteUrl) {
    head.script = [
      {
        type: "application/ld+json",
        innerHTML: jsonLdSafe({
          "@context": "https://schema.org",
          "@type": "WebSite",
          name: siteTitle.value,
          url: `${siteUrl}/`,
        }),
      },
      ...(c?.analyticsHeadCode ? parseAnalyticsScripts(c.analyticsHeadCode) : []),
    ];
  } else if (c?.analyticsHeadCode) {
    head.script = parseAnalyticsScripts(c.analyticsHeadCode);
  }
  return head;
});

/** 把后台粘贴的统计代码（含 <script> 标签）解析成 useHead 可用的 script 条目。 */
function parseAnalyticsScripts(raw: string): Array<Record<string, any>> {
  const out: Array<Record<string, any>> = [];
  const re = /<script\b([^>]*)>([\s\S]*?)<\/script>/gi;
  let m: RegExpExecArray | null;
  while ((m = re.exec(raw)) !== null) {
    const attrs = m[1] || "";
    const body = (m[2] || "").trim();
    const srcMatch = attrs.match(/src=["']([^"']+)["']/i);
    if (srcMatch) {
      out.push({ src: srcMatch[1], async: /async/i.test(attrs), tagPosition: "head" });
    } else if (body) {
      out.push({ innerHTML: body, tagPosition: "head" });
    }
  }
  return out;
}

const mobileOpen = ref(false);
const searchKeyword = ref("");
const router = useRouter();

function submitSearch() {
  const q = searchKeyword.value.trim();
  if (!q) return;
  router.push({ path: "/search", query: { q } });
}

function fallbackItems(): HaloMenuItem[] {
  return [
    {
      metadata: { name: "fallback-home" },
      spec: { displayName: "首页", href: "/", priority: 0 },
      children: [],
    },
    {
      metadata: { name: "fallback-console" },
      spec: { displayName: "后台", href: `${haloApiBase}/console`, target: "_blank", priority: 1 },
      children: [],
    },
  ];
}

const { data: menu } = await useAsyncData<HaloMenu | null>("primary-menu", () =>
  getPrimaryMenu().catch(() => null),
);

const menuItems = computed<HaloMenuItem[]>(() =>
  menu.value?.menuItems?.length ? menu.value.menuItems : fallbackItems(),
);

// 菜单悬停预取：站内单段路径（独立页）在悬停时预热数据缓存，
// 点击时 useAsyncData 同 key 直接命中 payload 缓存，导航零请求直达
const PREFETCH_RESERVED = new Set([
  "archives",
  "categories",
  "tags",
  "search",
  "column",
  "java-interview",
  "tools",
  "zsxq",
  "admin",
  "studio",
  "api",
  "upload",
  "console",
  "login",
]);
function prefetchOnHover(item: HaloMenuItem) {
  const m = /^\/([a-zA-Z0-9-]+)\/?$/.exec(itemHref(item));
  if (!m || PREFETCH_RESERVED.has(m[1])) return;
  prefetchSinglePage(m[1]);
}

function itemHref(item: HaloMenuItem): string {
  return item.status?.href || item.spec.href || "";
}
function itemName(item: HaloMenuItem): string {
  return item.status?.displayName || item.spec.displayName;
}
function isExternal(href: string): boolean {
  return /^https?:\/\//i.test(href) || href.startsWith("//");
}
function normalizeHref(href: string): string {
  if (!href || !href.startsWith("/")) return href ? `/${href}` : "/";
  return href;
}
function isActive(href: string): boolean {
  if (!href || isExternal(href)) return false;
  const path = normalizeHref(href);
  if (path === "/") return route.path === "/";
  return route.path === path || route.path.startsWith(`${path}/`);
}

watch(
  () => route.path,
  () => {
    mobileOpen.value = false;
  },
);
</script>
