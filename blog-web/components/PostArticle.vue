<template>
  <div class="flex gap-8">
    <!-- 阅读进度条（仅阅读页挂载） -->
    <ReadingProgress />

    <!-- 左侧：正文 TOC（桌面端） -->
    <aside class="hidden lg:block w-60 flex-shrink-0">
      <TocSidebar :toc="toc" :active-id="activeId" />
    </aside>

    <!-- 正文区域 -->
    <article
      ref="articleEl"
      class="flex-1 min-w-0 bg-white rounded-lg border border-slate-200 px-6 py-8 md:px-10"
    >
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
        <span v-if="post.status.publishTime">发布于 {{ formatDate(post.status.publishTime) }}</span>
        <!-- 标签：链到 /tags/{slug} 归档页 -->
        <span
          v-for="tag in post.tags ?? []"
          :key="tag.metadata.name"
          class="inline-flex items-center"
        >
          <NuxtLink
            :to="`/tags/${tag.spec.slug}`"
            class="px-2 py-0.5 rounded-full bg-slate-100 hover:bg-sky-50 border border-slate-200 hover:border-sky-200 text-xs text-slate-600 hover:text-[#0562a9] transition-colors"
          >
            # {{ tag.spec.displayName }}
          </NuxtLink>
        </span>
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

      <!-- 上下章导航（有链接前缀且存在上一/下一篇时展示；连载显示「上一章/下一章」） -->
      <div
        v-if="navBase && (prevPost || nextPost)"
        class="mt-12 pt-6 border-t border-slate-100 grid grid-cols-2 gap-4"
      >
        <NuxtLink
          v-if="prevPost"
          :to="`${navBase}/${prevPost.spec.slug}`"
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
          :to="`${navBase}/${nextPost.spec.slug}`"
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
  /** 上下篇链接前缀，默认 /categories/{categorySlug}；项目阅读页传 /column/{seriesSlug} */
  basePath?: string;
  prevPost?: HaloPost;
  nextPost?: HaloPost;
  /** 章节式连载上下文：提供后显示进度角标与右侧章节目录 */
  series?: { category: HaloCategory; posts: HaloPost[] };
  /** 显式章节进度（不依赖分类目录，用于 /column 阅读页） */
  chapter?: { current: number; total: number };
}>();

// 上下篇/章导航的链接前缀
const navBase = computed(() =>
  props.basePath ?? (props.categorySlug ? `/categories/${props.categorySlug}` : ""),
);

// 系列进度：优先使用显式 chapter 参数，否则从 series 上下文的 slug 编号推导
const chapterNumber = computed(() =>
  props.chapter?.current ?? (props.series ? getChapterNumber(props.post.spec.slug) : undefined),
);
const chapterTotal = computed(
  () =>
    props.chapter?.total ??
    (props.series
      ? props.series.posts.filter((p) => getChapterNumber(p.spec.slug) != null).length
      : 0),
);
const isSeries = computed(() => !!props.series || !!props.chapter);

// 先消毒正文（防存储型 XSS）、去除与文章标题重复的首个 h1（爬虫导入文常见），
// 再解析标题、注入 id、生成 TOC。用 computed 包裹：站内换页复用组件时正文与 TOC 同步刷新
const rendered = computed(() =>
  extractToc(stripDuplicateTitle(sanitizeHtml(props.rawContent || ""), props.post.spec.title)),
);
const renderedHtml = computed(() => rendered.value.html);
const toc = computed(() => rendered.value.toc);

// 代码块增强：语法高亮 + 语言标签 + 复制按钮（highlight.js 客户端动态加载，不进首屏包）
const articleEl = ref<HTMLElement | null>(null);
const { enhance: enhanceCodeBlocks } = useCodeEnhance();

// 滚动高亮当前标题
const activeId = ref<string>("");
let scrollHandler: (() => void) | null = null;

onMounted(() => {
  enhanceCodeBlocks(articleEl.value);
  watch(renderedHtml, () => nextTick(() => enhanceCodeBlocks(articleEl.value)));

  scrollHandler = () => {
    if (!toc.value.length) return;
    // 找到当前视口顶部（导航栏下方 120px）之上、最靠下的标题
    let current = toc.value[0].id;
    for (const item of toc.value) {
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
