<template>
  <div class="mx-auto px-4 py-6" :class="series ? 'max-w-7xl' : 'max-w-5xl'">
    <!-- 面包屑 -->
    <nav class="flex items-center gap-2 text-sm text-slate-500 mb-5">
      <NuxtLink to="/" class="hover:text-[#0562a9]">首页</NuxtLink>
      <span class="text-slate-300">/</span>
      <NuxtLink :to="`/categories/${category.spec.slug}`" class="hover:text-[#0562a9]">
        {{ category.spec.displayName }}
      </NuxtLink>
      <span class="text-slate-300">/</span>
      <span class="text-slate-700 truncate">{{ post.spec.title }}</span>
    </nav>

    <PostArticle
      :post="post"
      :raw-content="postDetail?.content?.content || ''"
      :category-slug="category.spec.slug"
      :prev-post="prevPost"
      :next-post="nextPost"
      :series="series"
    />
  </div>
</template>

<script setup lang="ts">
import type { HaloCategory, HaloPost, HaloPostDetail } from "~/types/halo";
import { isSeriesPosts, sortPostsBySeries } from "~/utils/tutorial";

const route = useRoute();
const { getCategoryBySlug, getPostsByCategory, getPostDetail } = useHaloApi();

const categorySlug = decodeSlug(route.params.categorySlug as string);
const postSlug = decodeSlug(route.params.postSlug as string);

const { data: category } = await useAsyncData(`cat-${categorySlug}`, () =>
  getCategoryBySlug(categorySlug),
);

if (!category.value) {
  throw createError({ statusCode: 404, statusMessage: "分类不存在" });
}

const { data: postsResult } = await useAsyncData(`catposts-${categorySlug}`, () =>
  getPostsByCategory(category.value!.metadata.name),
);

// 系列顺序 = 发布时间升序。接口默认倒序，直接用会导致
// 上一篇/下一篇语义颠倒（读第 5 章时「上一篇」指向第 6 章）
const posts = computed<HaloPost[]>(() => sortPostsBySeries(postsResult.value ?? []));

// 章节式连载（≥3 篇带编号 slug）：开启进度角标与右侧章节目录
const series = computed(() => {
  if (!category.value || !posts.value.length || !isSeriesPosts(posts.value)) return undefined;
  return { category: category.value, posts: posts.value };
});

const post = computed(() => posts.value.find((p) => p.spec.slug === postSlug));

if (!post.value) {
  throw createError({ statusCode: 404, statusMessage: "文章不存在" });
}

const { data: postDetail } = await useAsyncData<HaloPostDetail>(
  `post-detail-${post.value.metadata.name}`,
  () => getPostDetail(post.value!.metadata.name),
);

const currentIndex = computed(() => {
  const idx = posts.value?.findIndex((p) => p.metadata.name === post.value?.metadata.name) ?? -1;
  return idx;
});

const prevPost = computed(() => {
  if (currentIndex.value > 0) return posts.value?.[currentIndex.value - 1];
  return undefined;
});

const nextPost = computed(() => {
  if (currentIndex.value >= 0 && currentIndex.value < (posts.value?.length ?? 0) - 1) {
    return posts.value?.[currentIndex.value + 1];
  }
  return undefined;
});

useHead(() => ({
  title: `${post.value?.spec.title} - ${category.value?.spec.displayName}`,
}));
</script>
