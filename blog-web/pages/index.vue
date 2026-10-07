<template>
  <div class="max-w-7xl mx-auto px-4 py-8">
    <!-- 简洁标题区（无大 Hero）：标题/副标题由后台「站点设置 - 首页 Hero」控制 -->
    <div class="mb-10">
      <h1 class="text-3xl font-bold text-slate-900">{{ homepageTitle }}</h1>
      <p class="text-sm text-slate-500 mt-1.5">{{ homepageSubtitle }}</p>
      <!-- 站点统计：一行数字撑起门面感（数据与下方网格同源） -->
      <div class="mt-3 flex items-center gap-2.5 text-sm text-slate-400">
        <span>{{ categories?.length ?? 0 }} 个专栏</span>
        <span class="w-1 h-1 rounded-full bg-slate-300" aria-hidden="true"></span>
        <span>{{ posts?.length ?? 0 }} 篇教程</span>
      </div>
    </div>

    <!-- 教程卡片：桌面四列、平板两列、手机单列 -->
    <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 sm:gap-5 lg:gap-6 stagger-in">
      <CategoryCard
        v-for="category in categories"
        :key="category.metadata.name"
        :category="category"
        :fallback-cover="coverMap[category.metadata.name] || ''"
        :last-updated="lastUpdatedMap[category.metadata.name] || ''"
      />
      <div v-if="categories.length === 0" class="col-span-full text-center text-slate-500 py-12">
        暂无教程分类，请先在后台创建分类
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { HaloCategory, HaloPost } from "~/types/halo";

const { getCategories, getAllPosts, getSectionCategoryNames } = useHaloApi();

// 首页标题/副标题：后台「站点设置 - 首页 Hero」控制，回退硬编码默认
const siteConfigData = useSiteConfig();
const homepageTitle = computed(() => siteConfigData.value?.config?.homepageTitle || "全部教程");
const homepageSubtitle = computed(
  () => siteConfigData.value?.config?.homepageSubtitle || "系统化的编程技术教程，从入门到精通",
);

const { data: categories } = await useAsyncData<HaloCategory[]>("categories", () =>
  getCategories(),
);

const { data: posts } = await useAsyncData<HaloPost[]>("all-posts-covers", async () => {
  // 排除八股题库等独立栏目的题目，首页教程卡片只展示常规专栏
  const sectionNames = await getSectionCategoryNames();
  return getAllPosts({ excludeCategoryNames: sectionNames });
});

// 分类自身没有封面时，用该专栏下第一篇有封面的文章作为卡片封面
const coverMap = computed<Record<string, string>>(() => {
  const map: Record<string, string> = {};
  for (const post of posts.value ?? []) {
    if (!post.spec.cover) continue;
    for (const category of post.categories ?? []) {
      if (!map[category.metadata.name]) {
        map[category.metadata.name] = post.spec.cover;
      }
    }
  }
  return map;
});

// 每个分类下最新一篇文章的发布时间，用于卡片展示「更新于」
const lastUpdatedMap = computed<Record<string, string>>(() => {
  const map: Record<string, string> = {};
  for (const post of posts.value ?? []) {
    const time = post.spec.publishTime || post.status.publishTime;
    if (!time) continue;
    for (const category of post.categories ?? []) {
      const current = map[category.metadata.name];
      if (!current || time > current) {
        map[category.metadata.name] = time;
      }
    }
  }
  return map;
});
</script>
