<template>
  <div class="max-w-5xl mx-auto px-4 py-8">
    <!-- 面包屑 -->
    <nav class="flex items-center gap-2 text-sm text-slate-500 mb-6">
      <NuxtLink to="/" class="hover:text-[#0562a9]">首页</NuxtLink>
      <span>/</span>
      <span class="text-slate-700">文章</span>
    </nav>

    <div class="mb-8">
      <h1 class="text-3xl font-bold text-slate-900 mb-2">全部文章</h1>
      <p class="text-slate-500">共 {{ posts.length }} 篇文章，按发布时间倒序</p>
    </div>

    <div class="bg-white rounded-xl border border-slate-200 divide-y divide-slate-100">
      <NuxtLink
        v-for="post in pagedPosts"
        :key="post.metadata.name"
        :to="postLink(post)"
        class="flex items-center gap-4 p-4 hover:bg-slate-50 transition-colors group"
      >
        <div
          v-if="post.spec.cover"
          class="hidden sm:block w-32 h-20 rounded-lg overflow-hidden bg-slate-100 flex-shrink-0"
        >
          <img
            :src="post.spec.cover"
            :alt="post.spec.title"
            referrerpolicy="no-referrer"
            loading="lazy"
            class="w-full h-full object-cover group-hover:scale-105 transition-transform duration-300"
          />
        </div>
        <div class="flex-1 min-w-0">
          <h3
            class="font-medium text-slate-900 group-hover:text-[#0562a9] transition-colors truncate"
          >
            {{ post.spec.title }}
          </h3>
          <p class="text-sm text-slate-500 truncate">
            {{ post.spec.excerpt?.raw || post.status.excerpt || "" }}
          </p>
        </div>
        <span class="hidden md:block text-xs text-slate-400 flex-shrink-0">
          {{ formatDate(post.status.publishTime) }}
        </span>
        <svg
          class="w-5 h-5 text-slate-400 group-hover:text-[#0562a9] transition-colors flex-shrink-0"
          fill="none"
          stroke="currentColor"
          viewBox="0 0 24 24"
        >
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5l7 7-7 7" />
        </svg>
      </NuxtLink>
    </div>

    <Pagination :page="page" :total-pages="totalPages" />

    <div
      v-if="posts.length === 0"
      class="bg-white rounded-xl border border-slate-200 p-12 text-center text-slate-500"
    >
      暂无已发布的文章
    </div>
  </div>
</template>

<script setup lang="ts">
import type { HaloPost } from "~/types/halo";

const PAGE_SIZE = 15;

const route = useRoute();
const { getAllPosts, getSectionCategoryNames } = useHaloApi();

const { data: posts } = await useAsyncData<HaloPost[]>("all-posts-paged", async () => {
  // 归档只收常规专栏，八股题库等独立栏目从归档中排除
  const sectionNames = await getSectionCategoryNames();
  const items = await getAllPosts({ excludeCategoryNames: sectionNames });
  // 按发布时间倒序（Halo 默认顺序不保证符合前台展示预期）
  return items
    .slice()
    .sort(
      (a, b) =>
        new Date(b.status.publishTime || b.metadata.creationTimestamp).getTime() -
        new Date(a.status.publishTime || a.metadata.creationTimestamp).getTime(),
    );
});

const allPosts = computed(() => posts.value ?? []);
const totalPages = computed(() => Math.max(1, Math.ceil(allPosts.value.length / PAGE_SIZE)));
const page = computed(() => {
  const p = Number.parseInt(String(route.query.page ?? "1"), 10);
  if (!Number.isFinite(p) || p < 1) return 1;
  return Math.min(p, totalPages.value);
});
const pagedPosts = computed(() =>
  allPosts.value.slice((page.value - 1) * PAGE_SIZE, page.value * PAGE_SIZE),
);

/** 优先跳规范地址（带分类），兜底 Halo 原生固定链接 */
function postLink(post: HaloPost): string {
  const categorySlug = post.categories?.[0]?.spec.slug;
  if (categorySlug) return `/categories/${categorySlug}/${post.spec.slug}`;
  return `/archives/${post.spec.slug}`;
}

function formatDate(dateStr: string): string {
  if (!dateStr) return "";
  const d = new Date(dateStr);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}

useHead({ title: "全部文章" });
</script>
