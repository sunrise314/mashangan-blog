<template>
  <div class="max-w-5xl mx-auto px-4 py-8">
    <!-- 面包屑 -->
    <nav class="flex items-center gap-2 text-sm text-slate-500 mb-6">
      <NuxtLink to="/" class="hover:text-[#0562a9]">首页</NuxtLink>
      <span>/</span>
      <NuxtLink to="/tags" class="hover:text-[#0562a9]">标签云</NuxtLink>
      <span>/</span>
      <span class="text-slate-700">{{ tag?.spec.displayName }}</span>
    </nav>

    <!-- 标签头 -->
    <header v-if="tag" class="bg-white rounded-xl border border-slate-200 p-8 mb-8">
      <div class="flex flex-wrap items-center gap-3 mb-1">
        <h1 class="text-3xl font-bold text-slate-900"># {{ tag.spec.displayName }}</h1>
        <span class="px-2.5 py-0.5 text-xs font-medium rounded-full bg-sky-50 border border-sky-100 text-[#0562a9]">
          {{ postResult?.total ?? 0 }} 篇文章
        </span>
      </div>
      <p class="text-slate-600">与「{{ tag.spec.displayName }}」相关的全部文章。</p>
    </header>

    <!-- 文章列表 -->
    <div class="bg-white rounded-xl border border-slate-200 divide-y divide-slate-100">
      <NuxtLink
        v-for="post in posts"
        :key="post.metadata.name"
        :to="postLink(post)"
        class="flex items-center gap-4 p-4 hover:bg-slate-50 transition-colors group"
      >
        <div class="flex-1 min-w-0">
          <h2 class="font-medium text-slate-900 group-hover:text-[#0562a9] transition-colors truncate">
            {{ post.spec.title }}
          </h2>
          <p class="text-sm text-slate-500 truncate">
            {{ post.status.excerpt || post.spec.excerpt?.raw || "" }}
          </p>
        </div>
        <span class="hidden sm:block text-xs text-slate-400 flex-shrink-0">
          {{ formatDate(post.status.publishTime || post.metadata.creationTimestamp) }}
        </span>
      </NuxtLink>
      <div v-if="!posts.length && !pending" class="p-8 text-center text-slate-500">该标签下暂无文章</div>
    </div>

    <Pagination :page="page" :total-pages="totalPages" />
  </div>
</template>

<script setup lang="ts">
import type { HaloPageResult, HaloPost, TagCard } from "~/types/halo";

const PAGE_SIZE = 20;

const route = useRoute();
const { getTags, getPostsByTagPage } = useHaloApi();

const tagSlug = decodeSlug(route.params.slug as string);

const page = computed(() => {
  const p = Number.parseInt(String(route.query.page ?? "1"), 10);
  return Number.isFinite(p) && p >= 1 ? p : 1;
});

// 复用 /tags 页的 tags-cloud key（同数据全站共享）
const { data: tags } = await useAsyncData<TagCard[]>(`tags-cloud`, () => getTags());
const tag = computed(() => tags.value?.find((t) => t.spec.slug === tagSlug));

if (!tag.value) {
  throw createError({ statusCode: 404, statusMessage: "标签不存在" });
}

const { data: postResult, pending } = await useAsyncData<HaloPageResult<HaloPost>>(
  `tagposts-${tagSlug}-${page.value}`,
  () => getPostsByTagPage(tagSlug, page.value, PAGE_SIZE),
  { watch: [() => page.value] },
);

const posts = computed<HaloPost[]>(() => postResult.value?.items ?? []);
const totalPages = computed(() => postResult.value?.totalPages ?? 1);

/** 列表链接：系列章节进 /column 阅读页，普通文章进分类页，无分类文章进归档 */
function postLink(post: HaloPost): string {
  const seriesSlug = post.metadata.annotations?.["haloweb/series"];
  if (seriesSlug) return `/column/${seriesSlug}/${post.spec.slug}`;
  if (post.categories?.length) {
    const cat = post.categories[0];
    if (cat?.spec?.slug) return `/categories/${cat.spec.slug}/${post.spec.slug}`;
  }
  return `/archives/${post.spec.slug}`;
}

function formatDate(dateStr?: string): string {
  if (!dateStr) return "";
  const d = new Date(dateStr);
  if (Number.isNaN(d.getTime())) return "";
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}

// ── SEO：title 尾巴用站点名，description 由标签名生成；prev/next 绝对 URL ──
const siteCfg = useSiteConfig();
const runtimeCfg = useRuntimeConfig();
const siteName = computed(
  () => siteCfg.value?.config?.title || (runtimeCfg.public.siteTitle as string) || "码上岸",
);
const siteUrl = ((runtimeCfg.public.siteUrl as string) || "").replace(/\/+$/, "");
const tagUrl = computed(() => `${siteUrl}/tags/${encodeURIComponent(tagSlug)}`);

useHead(() => ({
  title: `${tag.value?.spec.displayName} 标签下的文章 - ${siteName.value}`,
  meta: [
    { name: "description", content: `与「${tag.value?.spec.displayName}」相关的全部 ${postResult.value?.total ?? 0} 篇文章，按发布时间排列。` },
  ],
  link: [
    ...(page.value > 1
      ? [{ rel: "prev", href: page.value - 1 > 1 ? `${tagUrl.value}?page=${page.value - 1}` : tagUrl.value }]
      : []),
    ...(page.value < totalPages.value
      ? [{ rel: "next", href: `${tagUrl.value}?page=${page.value + 1}` }]
      : []),
  ],
}));
</script>
