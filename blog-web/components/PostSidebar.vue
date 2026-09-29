<template>
  <div class="bg-white rounded-xl border border-slate-200 p-4 sticky top-20">
    <h3 class="font-bold text-slate-900">{{ category.spec.displayName }}</h3>
    <div class="flex items-center gap-2 mt-1 mb-3 pb-2 border-b border-slate-100 text-xs">
      <span class="text-slate-500">共 {{ posts.length }} 篇</span>
      <span
        class="px-1.5 py-0.5 rounded-full font-medium"
        :class="status === 'completed' ? 'bg-green-50 text-green-600' : 'bg-violet-50 text-violet-600'"
      >
        {{ statusLabel }}
      </span>
    </div>
    <ul class="space-y-1 max-h-[calc(100vh-12rem)] overflow-y-auto">
      <li v-for="(post, index) in posts" :key="post.metadata.name">
        <NuxtLink
          :to="`/categories/${category.spec.slug}/${post.spec.slug}`"
          class="flex items-start gap-2 px-2 py-1.5 rounded-lg text-sm transition-colors"
          :class="currentPost?.metadata.name === post.metadata.name
            ? 'bg-sky-50 text-[#0562a9] font-medium'
            : 'text-slate-600 hover:bg-slate-50 hover:text-[#0562a9]'"
        >
          <span
            class="w-7 h-7 rounded-md flex items-center justify-center text-xs font-bold flex-shrink-0 mt-0.5"
            :class="currentPost?.metadata.name === post.metadata.name
              ? 'bg-[#0562a9] text-white'
              : 'bg-sky-50 text-[#0562a9]'"
          >
            {{ badgeOf(post, index) }}
          </span>
          <span class="flex-1 min-w-0 leading-6">{{ post.spec.title }}</span>
        </NuxtLink>
      </li>
    </ul>
  </div>
</template>

<script setup lang="ts">
import type { HaloCategory, HaloPost } from '~/types/halo'
import { getChapterNumber, getTutorialStatus, TUTORIAL_STATUS_LABEL } from '~/utils/tutorial'

const props = defineProps<{
  category: HaloCategory
  /** 已按系列顺序（发布时间升序）排好的章节列表 */
  posts: HaloPost[]
  currentPost?: HaloPost
}>()

const status = computed(() => getTutorialStatus(props.category));
const statusLabel = computed(() => TUTORIAL_STATUS_LABEL[status.value]);

/** 编号 slug（digital-twin-05-x）显示章节号，发刊词等无编号文章显示「序」 */
function badgeOf(post: HaloPost, index: number): string {
  const n = getChapterNumber(post.spec.slug);
  return n != null ? String(n).padStart(2, "0") : index === 0 ? "序" : String(index + 1);
}
</script>
