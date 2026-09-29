<template>
  <div class="interview-detail-page">
    <div class="max-w-5xl mx-auto px-4 py-6">
      <!-- 面包屑：八股文 / 专题 / 题目 -->
      <nav class="interview-breadcrumb">
        <NuxtLink to="/java-interview" class="hover:text-[#0562a9]">八股文</NuxtLink>
        <span class="text-slate-300">/</span>
        <NuxtLink v-if="located" to="/java-interview" class="hover:text-[#0562a9]">
          {{ located.topic.category.spec.displayName }}
        </NuxtLink>
        <span v-if="located" class="text-slate-300">/</span>
        <span class="text-slate-700 truncate">{{ located?.post.spec.title }}</span>
      </nav>

      <div class="flex gap-8">
        <!-- 左侧：TOC（桌面端） -->
        <aside class="hidden lg:block w-60 flex-shrink-0">
          <TocSidebar :toc="toc" :active-id="activeId" />
        </aside>

        <!-- 题目正文 -->
        <article
          class="flex-1 min-w-0 bg-white rounded-lg border border-slate-200 px-6 py-8 md:px-10"
        >
          <h1 class="text-2xl md:text-3xl font-bold text-slate-900 mb-3">
            {{ located?.post.spec.title }}
          </h1>
          <div
            class="flex items-center gap-4 text-sm text-slate-500 mb-6 pb-4 border-b border-slate-100"
          >
            <span v-if="located" class="interview-topic-tag">
              {{ located.topic.category.spec.displayName }}
            </span>
            <span>发布于 {{ formatDate(located?.post.status.publishTime ?? "") }}</span>
          </div>

          <div v-if="located?.post.spec.cover" class="mb-8 rounded-lg overflow-hidden bg-slate-100">
            <img
              :src="located.post.spec.cover"
              :alt="located.post.spec.title"
              referrerpolicy="no-referrer"
              class="w-full max-h-96 object-cover"
            />
          </div>

          <div class="prose-halo max-w-none" v-html="renderedHtml" />

          <!-- 上一题 / 下一题 -->
          <div class="mt-12 pt-6 border-t border-slate-100 grid grid-cols-2 gap-4">
            <NuxtLink
              v-if="located?.prev"
              :to="`/java-interview/${located.prev.spec.slug}`"
              class="p-4 rounded-lg border border-slate-200 hover:border-sky-300 hover:bg-sky-50 transition-colors group"
            >
              <div class="text-xs text-slate-500 mb-1">上一题</div>
              <div class="text-sm font-medium text-slate-900 group-hover:text-[#0562a9] truncate">
                {{ located.prev.spec.title }}
              </div>
            </NuxtLink>
            <div v-else />
            <NuxtLink
              v-if="located?.next"
              :to="`/java-interview/${located.next.spec.slug}`"
              class="p-4 rounded-lg border border-slate-200 hover:border-sky-300 hover:bg-sky-50 transition-colors group text-right"
            >
              <div class="text-xs text-slate-500 mb-1">下一题</div>
              <div class="text-sm font-medium text-slate-900 group-hover:text-[#0562a9] truncate">
                {{ located.next.spec.title }}
              </div>
            </NuxtLink>
          </div>

          <div class="mt-8 text-center">
            <NuxtLink to="/java-interview" class="text-sm text-[#0562a9] hover:underline">
              ← 返回题库目录
            </NuxtLink>
          </div>
        </article>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { HaloPostDetail } from "~/types/halo";
import type { InterviewBank } from "~/composables/useInterviewBank";
import { useInterviewBank } from "~/composables/useInterviewBank";
import { extractToc } from "~/composables/useToc";

const route = useRoute();
const { getPostDetail } = useHaloApi();
const { fetchBank, locateQuestion } = useInterviewBank();

const slug = decodeSlug(route.params.slug as string);

const { data: bank } = await useAsyncData<InterviewBank>("interview-bank", () => fetchBank());

const located = computed(() => (bank.value ? locateQuestion(bank.value, slug) : null));

// 数据就绪后若题库或题目不存在，走 404
if (!bank.value?.root || !located.value) {
  throw createError({ statusCode: 404, statusMessage: "题目不存在", fatal: true });
}

const { data: postDetail } = await useAsyncData<HaloPostDetail>(
  `interview-post-${located.value.post.metadata.name}`,
  () => getPostDetail(located.value!.post.metadata.name),
);

// 先消毒正文（防存储型 XSS），再生成 TOC
const { html: renderedHtml, toc } = extractToc(
  sanitizeHtml(postDetail.value?.content?.content || ""),
);

const activeId = ref<string>("");
let scrollHandler: (() => void) | null = null;

onMounted(() => {
  scrollHandler = () => {
    if (!toc.length) return;
    let current = toc[0].id;
    for (const item of toc) {
      const el = document.getElementById(item.id);
      if (!el) continue;
      if (el.getBoundingClientRect().top <= 120) current = item.id;
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
  if (Number.isNaN(d.getTime())) return "";
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}

useHead(() => ({
  title: `${located.value?.post.spec.title ?? ""} - ${located.value?.topic.category.spec.displayName ?? "八股文"}`,
}));
</script>

<style scoped>
.interview-detail-page {
  background: #f5f6f7;
  min-height: 100vh;
}
.interview-breadcrumb {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  color: #64748b;
  margin-bottom: 20px;
  max-width: 100%;
  overflow: hidden;
}
.interview-topic-tag {
  padding: 2px 10px;
  border-radius: 999px;
  background: rgba(20, 111, 184, 0.1);
  color: #0962a9;
  font-size: 12px;
  flex-shrink: 0;
}
</style>
