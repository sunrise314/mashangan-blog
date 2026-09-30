<template>
  <!-- 有分类时 SSR 阶段直接 301，此模板仅作为跳转占位 -->
  <div v-if="!post" class="max-w-7xl mx-auto px-4 py-20 text-center text-slate-500">
    正在跳转文章…
  </div>
  <div v-else class="max-w-5xl mx-auto px-4 py-6">
    <!-- 面包屑（无分类文章没有分类链接） -->
    <nav class="flex items-center gap-2 text-sm text-slate-500 mb-5">
      <NuxtLink to="/" class="hover:text-[#0562a9]">首页</NuxtLink>
      <span class="text-slate-300">/</span>
      <span class="text-slate-700 truncate">{{ post.spec.title }}</span>
    </nav>

    <PostArticle :post="post" :raw-content="rawContent" />
  </div>
</template>

<script setup lang="ts">
import type { HaloPost } from "~/types/halo";

// Halo 原生文章固定链接为 /archives/{slug}：
// 系列文章 301 到 /column/{系列slug}/{文章slug}，有分类的文章 301 到
// /categories/{分类slug}/{文章slug}；未挂分类的文章在本页直接渲染正文
const route = useRoute();
const { findPostBySlug, findAnyPostBySlug, getPostDetail } = useHaloApi();

const postSlug = decodeSlug(route.params.slug as string);

// 全局 /posts 排除了系列文章，查不到时回落到 by-slug 接口（含系列文章）
const { data: post } = await useAsyncData<HaloPost | undefined>(
  `post-redirect-${postSlug}`,
  async () => (await findPostBySlug(postSlug)) ?? (await findAnyPostBySlug(postSlug)),
);

if (!post.value) {
  throw createError({ statusCode: 404, statusMessage: "文章不存在" });
}

const seriesSlug = post.value.metadata.annotations?.["haloweb/series"];
const categorySlug = !seriesSlug ? post.value.categories?.[0]?.spec.slug : undefined;
// 注意：setup 中 navigateTo 不会立即中止后续执行，必须互斥调用，
// 否则第二个 navigateTo 会覆盖第一个 301 的 Location
if (seriesSlug) {
  await navigateTo(`/column/${seriesSlug}/${postSlug}`, { redirectCode: 301 });
} else if (categorySlug) {
  await navigateTo(`/categories/${categorySlug}/${postSlug}`, { redirectCode: 301 });
}

// 有系列/分类时走重定向，无需拉取正文（handler 内短路，不发请求）
const { data: postDetail } = await useAsyncData(`post-detail-${postSlug}`, async () => {
  if (!post.value || seriesSlug || categorySlug) return null;
  return await getPostDetail(post.value.metadata.name);
});

const rawContent = computed(() => postDetail.value?.content?.content || "");

useHead(() => ({ title: post.value?.spec.title || "" }));
</script>
