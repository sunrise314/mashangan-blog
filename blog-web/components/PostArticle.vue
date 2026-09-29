<template>
  <div class="flex gap-8">
    <!-- 左侧：正文 TOC（桌面端） -->
    <aside class="hidden lg:block w-60 flex-shrink-0">
      <TocSidebar :toc="toc" :active-id="activeId" />
    </aside>

    <!-- 正文区域 -->
    <article class="flex-1 min-w-0 bg-white rounded-lg border border-slate-200 px-6 py-8 md:px-10">
      <h1 class="text-2xl md:text-3xl font-bold text-slate-900 mb-3">{{ post.spec.title }}</h1>
      <div
        class="flex flex-wrap items-center gap-3 text-sm text-slate-500 mb-6 pb-4 border-b border-slate-100"
      >
        <!-- 系列进度：第 N / M 章（发刊词等无编号文章不显示） -->
        <span
          v-if="chapterNumber != null"
          class="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full bg-sky-50 border border-sky-100 text-[#0562a9] text-xs font-medium"
        >
          第 {{ chapterNumber }} / {{ chapterTotal }} 章
          <span
            class="inline-block h-1 w-14 rounded-full bg-sky-100 overflow-hidden align-middle"
            aria-hidden="true"
          >
            <span
              class="block h-full rounded-full bg-[#0562a9]"
              :style="{
                width: `${Math.min(100, Math.round((chapterNumber / chapterTotal) * 100))}%`,
              }"
            />
          </span>
        </span>
        <span>发布于 {{ formatDate(post.status.publishTime) }}</span>
      </div>

      <!-- 封面图 -->
      <div v-if="post.spec.cover" class="mb-8 rounded-lg overflow-hidden bg-slate-100">
        <img
          :src="post.spec.cover"
          :alt="post.spec.title"
          referrerpolicy="no-referrer"
          class="w-full max-h-96 object-cover"
        />
      </div>

      <div class="prose-halo max-w-none" v-html="renderedHtml"></div>

      <!-- 上下章导航（归属分类的文章才展示；连载分类显示「上一章/下一章」） -->
      <div
        v-if="categorySlug && (prevPost || nextPost)"
        class="mt-12 pt-6 border-t border-slate-100 grid grid-cols-2 gap-4"
      >
        <NuxtLink
          v-if="prevPost"
          :to="`/categories/${categorySlug}/${prevPost.spec.slug}`"
          class="p-4 rounded-lg border border-slate-200 hover:border-sky-300 hover:bg-sky-50 transition-colors group"
        >
          <div class="text-xs text-slate-500 mb-1">{{ isSeries ? "上一章" : "上一篇" }}</div>
          <div class="text-sm font-medium text-slate-900 group-hover:text-[#0562a9] truncate">
            {{ prevPost.spec.title }}
          </div>
        </NuxtLink>
        <div v-else></div>
        <NuxtLink
          v-if="nextPost"
          :to="`/categories/${categorySlug}/${nextPost.spec.slug}`"
          class="p-4 rounded-lg border border-slate-200 hover:border-sky-300 hover:bg-sky-50 transition-colors group text-right"
        >
          <div class="text-xs text-slate-500 mb-1">{{ isSeries ? "下一章" : "下一篇" }}</div>
          <div class="text-sm font-medium text-slate-900 group-hover:text-[#0562a9] truncate">
            {{ nextPost.spec.title }}
          </div>
        </NuxtLink>
      </div>
    </article>

    <!-- 右侧：系列章节目录（2xl 起三栏布局） -->
    <aside v-if="series" class="hidden 2xl:block w-64 flex-shrink-0">
      <PostSidebar :category="series.category" :posts="series.posts" :current-post="post" />
    </aside>
  </div>
</template>

<script setup lang="ts">
import type { HaloCategory, HaloPost } from "~/types/halo";
import { extractToc } from "~/composables/useToc";
import { getChapterNumber } from "~/utils/tutorial";

const props = defineProps<{
  post: HaloPost;
  /** 原始正文 HTML（来自 Halo content API），组件内消毒后渲染 */
  rawContent?: string;
  /** 文章归属分类的 slug；无分类文章不展示上下篇导航 */
  categorySlug?: string;
  prevPost?: HaloPost;
  nextPost?: HaloPost;
  /** 章节式连载上下文：提供后显示进度角标与右侧章节目录 */
  series?: { category: HaloCategory; posts: HaloPost[] };
}>();

// 系列进度：当前章号取自 slug 编号；总数 = 带编号文章数（发刊词不计）
const chapterNumber = computed(() =>
  props.series ? getChapterNumber(props.post.spec.slug) : undefined,
);
const chapterTotal = computed(
  () => props.series?.posts.filter((p) => getChapterNumber(p.spec.slug) != null).length ?? 0,
);
const isSeries = computed(() => !!props.series);

// 先消毒正文（防存储型 XSS），再解析标题、注入 id、生成 TOC
const { html: renderedHtml, toc } = extractToc(sanitizeHtml(props.rawContent || ""));

// 滚动高亮当前标题
const activeId = ref<string>("");
let scrollHandler: (() => void) | null = null;

onMounted(() => {
  scrollHandler = () => {
    if (!toc.length) return;
    // 找到当前视口顶部（导航栏下方 120px）之上、最靠下的标题
    let current = toc[0].id;
    for (const item of toc) {
      const el = document.getElementById(item.id);
      if (!el) continue;
      const top = el.getBoundingClientRect().top;
      if (top <= 120) {
        current = item.id;
      }
    }
    activeId.value = current;
  };
  window.addEventListener("scroll", scrollHandler, { passive: true });
  scrollHandler();
});

onUnmounted(() => {
  if (scrollHandler) window.removeEventListener("scroll", scrollHandler);
});

function formatDate(dateStr: string): string {
  if (!dateStr) return "";
  const d = new Date(dateStr);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}
</script>
