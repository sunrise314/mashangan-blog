<template>
  <div v-if="series && chapter && post" class="max-w-5xl mx-auto px-4 py-6">
    <!-- 面包屑 -->
    <nav class="flex items-center gap-2 text-sm text-slate-500 mb-5 flex-wrap">
      <NuxtLink to="/" class="hover:text-[#0562a9]">首页</NuxtLink>
      <span class="text-slate-300">/</span>
      <NuxtLink to="/column" class="hover:text-[#0562a9]">项目实战</NuxtLink>
      <span class="text-slate-300">/</span>
      <NuxtLink :to="`/column/${series.slug}`" class="hover:text-[#0562a9]">
        {{ series.title }}
      </NuxtLink>
      <span class="text-slate-300">/</span>
      <span class="text-slate-700 truncate">第 {{ chapter.order }} 章</span>
    </nav>

    <!-- 免费章节：正文 -->
    <PostArticle
      v-if="!locked"
      :post="post"
      :raw-content="post.content?.content || ''"
      :base-path="`/column/${series.slug}`"
      :prev-post="prevChapter"
      :next-post="nextChapter"
      :chapter="{ current: chapter.order, total: series.totalChapters }"
    />

    <!-- 锁定章节：摘要 + 付费墙（正文已由服务端剥离） -->
    <article v-else class="bg-white rounded-lg border border-slate-200 px-6 py-8 md:px-10">
      <h1 class="text-2xl md:text-3xl font-bold text-slate-900 mb-3">{{ post.spec.title }}</h1>
      <div class="flex flex-wrap items-center gap-3 text-sm text-slate-500 mb-6 pb-4 border-b border-slate-100">
        <span class="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full bg-orange-50 border border-orange-100 text-orange-700 text-xs font-medium">
          <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <rect x="3" y="11" width="18" height="11" rx="2" />
            <path d="M7 11V7a5 5 0 0110 0v4" stroke-linecap="round" />
          </svg>
          第 {{ chapter.order }} / {{ series.totalChapters }} 章 · 星球专属
        </span>
        <span>发布于 {{ formatDate(post.status.publishTime) }}</span>
      </div>

      <div v-if="post.spec.cover" class="mb-8 rounded-lg overflow-hidden bg-slate-100">
        <img
          :src="post.spec.cover"
          :alt="post.spec.title"
          referrerpolicy="no-referrer"
          class="w-full max-h-96 object-cover"
        />
      </div>

      <p class="text-slate-600 leading-8 text-[15px]">
        {{ post.status.excerpt || chapter.excerpt || "本章为知识星球专属内容，加入星球后即可阅读。" }}
      </p>

      <SeriesPaywall
        :series-slug="series.slug"
        :series-title="series.title"
        :free-chapter-count="series.freeChapterCount"
        :chapter-order="chapter.order"
        :total-chapters="series.totalChapters"
      />
    </article>
  </div>
</template>

<script setup lang="ts">
import type { HaloPost, HaloPostDetail, SeriesChapter, SeriesDetail } from "~/types/halo";

const route = useRoute();
const { getSeriesDetail, getPostDetail } = useHaloApi();

const seriesSlug = route.params.seriesSlug as string;
const postSlug = decodeSlug(route.params.postSlug as string);

const { data: series } = await useAsyncData<SeriesDetail>(`series-read-${seriesSlug}`, () =>
  getSeriesDetail(seriesSlug),
);

const chapter = computed<SeriesChapter | undefined>(() =>
  series.value?.chapters.find((c) => c.slug === postSlug),
);
const currentChapter = chapter.value;

const { data: post } = await useAsyncData<HaloPostDetail | undefined>(
  `series-post-${postSlug}`,
  async () => {
    if (!currentChapter) return undefined;
    return await getPostDetail(currentChapter.name);
  },
);

if (!series.value || !currentChapter || !post.value) {
  throw createError({ statusCode: 404, statusMessage: "章节不存在" });
}

// 服务端闸门为准（access.locked），章节免费标记兜底
const locked = computed(() => post.value?.access?.locked ?? !currentChapter?.free);

// 相邻章节导航（锁定章节也作为入口，阅读页会展示付费墙）
const prevChapter = computed<HaloPost | undefined>(() => {
  if (!currentChapter || currentChapter.order <= 1) return undefined;
  const p = series.value!.chapters.find((x) => x.order === currentChapter.order - 1);
  return p ? ({ spec: { slug: p.slug, title: p.title } } as HaloPost) : undefined;
});
const nextChapter = computed<HaloPost | undefined>(() => {
  if (!currentChapter || currentChapter.order >= series.value!.totalChapters) return undefined;
  const n = series.value!.chapters.find((x) => x.order === currentChapter.order + 1);
  return n ? ({ spec: { slug: n.slug, title: n.title } } as HaloPost) : undefined;
});

// ── SEO：title 保留「章节 - 系列名」（系列名有搜索语义）；description=章节摘要；
// OG/BlogPosting/BreadcrumbList 补齐，锁定章同样输出摘要供搜索展示 ──
const runtimeCfg = useRuntimeConfig();
// useSiteConfig 直接返回 data ref（内部已 useAsyncData 全站共享），勿再解构
const siteCfg = useSiteConfig();
const seoSiteName = computed(
  () => siteCfg.value?.config?.title || (runtimeCfg.public.siteTitle as string) || "码上岸",
);
const seoSiteUrl = ((runtimeCfg.public.siteUrl as string) || "").replace(/\/+$/, "");
const chapterUrl = computed(
  () =>
    `${seoSiteUrl}/column/${encodeURIComponent(seriesSlug)}/${encodeURIComponent(postSlug)}`,
);

useHead(() => ({
  title: `${post.value?.spec.title ?? "章节"} - ${series.value?.title ?? "项目实战"}`,
  meta: [
    { name: "description", content: chapter.value?.excerpt || "" },
    { property: "og:title", content: post.value?.spec.title },
    { property: "og:description", content: chapter.value?.excerpt || "" },
    { property: "og:type", content: "article" },
    { property: "og:url", content: chapterUrl.value },
    ...((absolutizeUrl(post.value?.spec.cover, seoSiteUrl) &&
      [{ property: "og:image", content: absolutizeUrl(post.value?.spec.cover, seoSiteUrl) }]) ||
      []),
    ...(post.value?.status?.publishTime
      ? [{ property: "article:published_time", content: post.value.status.publishTime }]
      : []),
    ...(post.value?.status?.lastModifyTime
      ? [{ property: "article:modified_time", content: post.value.status.lastModifyTime }]
      : []),
  ],
  script: [
    {
      type: "application/ld+json",
      innerHTML: jsonLdSafe({
        "@context": "https://schema.org",
        "@type": "BlogPosting",
        headline: post.value?.spec.title,
        description: chapter.value?.excerpt || "",
        image: absolutizeUrl(post.value?.spec.cover, seoSiteUrl) || undefined,
        datePublished: post.value?.status?.publishTime || undefined,
        dateModified: post.value?.status?.lastModifyTime || undefined,
        mainEntityOfPage: chapterUrl.value,
        isPartOf: { "@type": "Blog", name: series.value?.title },
        author: { "@type": "Person", name: seoSiteName.value },
        publisher: { "@type": "Organization", name: seoSiteName.value },
      }),
    },
    {
      type: "application/ld+json",
      innerHTML: jsonLdSafe({
        "@context": "https://schema.org",
        "@type": "BreadcrumbList",
        itemListElement: [
          { "@type": "ListItem", position: 1, name: "首页", item: `${seoSiteUrl}/` },
          { "@type": "ListItem", position: 2, name: "项目实战", item: `${seoSiteUrl}/column` },
          {
            "@type": "ListItem",
            position: 3,
            name: series.value?.title,
            item: `${seoSiteUrl}/column/${encodeURIComponent(seriesSlug)}`,
          },
          { "@type": "ListItem", position: 4, name: post.value?.spec.title },
        ],
      }),
    },
  ],
}));

function formatDate(dateStr?: string | null): string {
  if (!dateStr) return "";
  const d = new Date(dateStr);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}
</script>
