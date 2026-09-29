<template>
  <div class="max-w-7xl mx-auto px-4 py-8">
    <!-- 面包屑 -->
    <nav class="flex items-center gap-2 text-sm text-slate-500 mb-6">
      <NuxtLink to="/" class="hover:text-[#0562a9]">首页</NuxtLink>
      <span>/</span>
      <span class="text-slate-700">{{ category?.spec.displayName }}</span>
    </nav>

    <!-- 专栏头部 -->
    <div v-if="category" class="bg-white rounded-xl border border-slate-200 p-8 mb-8">
      <div class="flex items-start gap-6">
        <div
          class="w-32 h-32 rounded-lg flex-shrink-0 flex items-center justify-center overflow-hidden"
          style="background: linear-gradient(135deg, #0562a9, #4d9bd8)"
        >
          <img
            v-if="category.spec.cover"
            :src="category.spec.cover"
            class="w-full h-full object-cover"
          />
          <span v-else class="text-white text-5xl font-bold">{{
            category.spec.displayName.charAt(0)
          }}</span>
        </div>
        <div class="flex-1">
          <div class="flex flex-wrap items-center gap-3 mb-2">
            <h1 class="text-3xl font-bold text-slate-900">{{ category.spec.displayName }}</h1>
            <span
              :class="status === 'completed' ? 'badge-complete' : 'badge-updating'"
              class="px-2.5 py-0.5 text-xs font-medium rounded-full"
            >
              {{ statusLabel }}
            </span>
          </div>
          <p class="text-slate-600 mb-4">{{ category.spec.description || "暂无描述" }}</p>
          <div class="flex flex-wrap items-center gap-4 text-sm text-slate-500">
            <span class="flex items-center gap-1">
              <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path
                  stroke-linecap="round"
                  stroke-linejoin="round"
                  stroke-width="2"
                  d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"
                />
              </svg>
              {{ category.status.visiblePostCount }} 篇文章
            </span>
            <!-- 开始阅读：直达目录第一篇 -->
            <NuxtLink
              v-if="firstPost"
              :to="`/categories/${category.spec.slug}/${firstPost.spec.slug}`"
              class="inline-flex items-center gap-1.5 px-5 py-2 rounded-lg text-white text-sm font-medium shadow-sm transition-opacity hover:opacity-90"
              style="background: var(--color-primary, #0562a9)"
            >
              开始阅读
              <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path
                  stroke-linecap="round"
                  stroke-linejoin="round"
                  stroke-width="2"
                  d="M9 5l7 7-7 7"
                />
              </svg>
            </NuxtLink>
          </div>
        </div>
      </div>
    </div>

    <!-- 文章列表 -->
    <h2 class="text-xl font-bold text-slate-900 mb-4">教程目录</h2>
    <div class="bg-white rounded-xl border border-slate-200 divide-y divide-slate-100">
      <NuxtLink
        v-for="(post, index) in posts"
        :key="post.metadata.name"
        :to="`/categories/${category?.spec.slug}/${post.spec.slug}`"
        class="flex items-center gap-4 p-4 hover:bg-slate-50 transition-colors group"
      >
        <span
          class="w-8 h-8 rounded-full bg-sky-100 text-[#0562a9] flex items-center justify-center text-sm font-bold flex-shrink-0"
        >
          {{ badgeOf(post, index) }}
        </span>
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
        <svg
          class="w-5 h-5 text-slate-400 group-hover:text-[#0562a9] transition-colors flex-shrink-0"
          fill="none"
          stroke="currentColor"
          viewBox="0 0 24 24"
        >
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5l7 7-7 7" />
        </svg>
      </NuxtLink>
      <div v-if="posts.length === 0" class="p-8 text-center text-slate-500">该分类下暂无文章</div>
    </div>

    <Pagination :page="page" :total-pages="totalPages" />
  </div>
</template>

<script setup lang="ts">
import type { HaloCategory, HaloPageResult, HaloPost } from "~/types/halo";
import {
  getChapterNumber,
  getTutorialStatus,
  isSeriesPosts,
  sortPostsBySeries,
  TUTORIAL_STATUS_LABEL,
} from "~/utils/tutorial";

// 一页拉全：课程目录必须整体有序（接口倒序、前端按系列升序重排），
// 若分页则跨页排序错乱；单页上限内（当前 20 篇 + 发刊词）一条页足够
const PAGE_SIZE = 100;

const route = useRoute();
const router = useRouter();
const { getCategoryBySlug, getPostsByCategoryPage, getPostsByCategory } = useHaloApi();

const slug = decodeSlug(route.params.slug as string);

const page = computed(() => {
  const p = Number.parseInt(String(route.query.page ?? "1"), 10);
  return Number.isFinite(p) && p >= 1 ? p : 1;
});

const { data: category } = await useAsyncData<HaloCategory | undefined>(`category-${slug}`, () =>
  getCategoryBySlug(slug),
);

if (!category.value) {
  throw createError({ statusCode: 404, statusMessage: "分类不存在" });
}

// 教程状态角标（annotation 优先，描述约定回退）
const status = computed(() => getTutorialStatus(category.value!));
const statusLabel = computed(() => TUTORIAL_STATUS_LABEL[status.value]);

// 「开始阅读」直达目录第一篇：拉全量按系列升序取第一条，
// 不能只取接口第 1 条——接口默认倒序，那会是最新一章而不是第一章
const { data: firstPostResult } = await useAsyncData<HaloPost[]>(`first-post-${slug}`, async () =>
  sortPostsBySeries(await getPostsByCategory(category.value!.metadata.name)),
);
const firstPost = computed<HaloPost | undefined>(() => firstPostResult.value?.[0]);

const { data: postResult } = await useAsyncData<HaloPageResult<HaloPost>>(
  `posts-${slug}`,
  () => getPostsByCategoryPage(category.value!.metadata.name, page.value, PAGE_SIZE),
  { watch: [() => page.value, () => category.value?.metadata.name] },
);

// 目录序 = 系列序（发刊词最前，章节 01→N）
const posts = computed<HaloPost[]>(() => sortPostsBySeries(postResult.value?.items ?? []));
const totalPages = computed(() => postResult.value?.totalPages ?? 1);

// 章节式连载判定：目录编号用章节号（发刊词显示「序」），否则退回列表序号
const series = computed(() => isSeriesPosts(posts.value));

function badgeOf(post: HaloPost, index: number): string {
  if (!series.value) return String(index + 1);
  const n = getChapterNumber(post.spec.slug);
  return n != null ? String(n).padStart(2, "0") : "序";
}

// 页码越界（如直接访问 ?page=999）时收敛到最后一页
if (import.meta.client) {
  watch(
    totalPages,
    (tp) => {
      if (tp > 0 && page.value > tp) {
        router.replace({ query: { ...route.query, page: String(tp) } });
      }
    },
    { immediate: true },
  );
}
</script>
