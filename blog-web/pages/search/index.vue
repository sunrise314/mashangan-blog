<template>
  <div class="max-w-3xl mx-auto px-4 py-8">
    <h1 class="text-2xl font-bold text-slate-900 mb-5">站内搜索</h1>

    <!-- 搜索框 -->
    <form class="relative mb-6" role="search" @submit.prevent="onSubmit">
      <input
        v-model="keyword"
        type="search"
        placeholder="搜索教程标题、正文关键字，如 Docker、MySQL、Redis"
        aria-label="搜索关键字"
        class="w-full h-11 pl-4 pr-24 rounded-lg border border-slate-300 bg-white text-sm text-slate-800 focus:outline-none focus:border-[#0562a9] focus:ring-2 focus:ring-sky-100"
      />
      <button
        type="submit"
        class="absolute right-1.5 top-1.5 h-8 px-4 rounded-md bg-[#0562a9] text-white text-sm hover:bg-[#044f87] transition-colors"
      >
        搜索
      </button>
    </form>

    <template v-if="q">
      <p v-if="!pending" class="text-sm text-slate-500 mb-4">
        「{{ q }}」共找到 {{ results.length }} 条结果
        <span v-if="truncated">（仅展示前 50 条，请细化关键字）</span>
      </p>

      <div
        v-if="error"
        class="bg-white rounded-lg border border-red-200 text-red-600 text-sm p-6 text-center"
      >
        搜索服务暂时不可用，请稍后再试。
      </div>

      <div
        v-else-if="results.length === 0 && !pending"
        class="bg-white rounded-lg border border-dashed border-slate-300 text-slate-500 text-sm p-10 text-center"
      >
        没有找到与「{{ q }}」相关的内容，换个关键字试试。
      </div>

      <ul v-else class="space-y-3">
        <li v-for="hit in results" :key="hit.metadataName">
          <NuxtLink
            :to="hit.permalink"
            class="block bg-white rounded-lg border border-slate-200 p-5 hover:border-sky-300 hover:shadow-sm transition-all group"
          >
            <h2
              class="text-base font-semibold text-slate-900 group-hover:text-[#0562a9] transition-colors mb-1.5"
              v-html="sanitizeHighlight(hit.title)"
            />
            <p
              class="text-sm text-slate-500 leading-relaxed line-clamp-2"
              v-html="sanitizeHighlight(hit.description)"
            />
          </NuxtLink>
        </li>
      </ul>
    </template>

    <div
      v-else
      class="bg-white rounded-lg border border-dashed border-slate-300 text-slate-500 text-sm p-10 text-center"
    >
      输入关键字开始搜索，支持标题与正文全文检索。
    </div>
  </div>
</template>

<script setup lang="ts">
import type { HaloSearchHit } from "~/types/halo";

const SEARCH_LIMIT = 50;

const route = useRoute();
const router = useRouter();
const { searchPosts, getSectionCategorySlugs } = useHaloApi();

const q = computed(() => String(route.query.q ?? "").trim());
const keyword = ref(q.value);

watch(q, (value) => {
  keyword.value = value;
});

const { data, pending, error } = await useAsyncData<HaloSearchHit[]>(
  "search-results",
  async () => {
    const keyword = q.value;
    if (!keyword) return [];
    const [hits, excludedSlugs] = await Promise.all([
      searchPosts(keyword, SEARCH_LIMIT),
      getSectionCategorySlugs(),
    ]);
    // 通用搜索排除八股题库等独立栏目（题目只在 /java-interview 内检索）
    return hits.filter(
      (hit) => !hit.categories?.some((slug) => excludedSlugs.has(slug)),
    );
  },
  { watch: [q] },
);

const results = computed(() => data.value ?? []);
const truncated = computed(() => results.value.length >= SEARCH_LIMIT);

function onSubmit() {
  const value = keyword.value.trim();
  if (!value) return;
  router.push({ path: "/search", query: { q: value } });
}

useHead(() => ({
  title: q.value ? `搜索：${q.value}` : "站内搜索",
}));
</script>
