<template>
  <NuxtLink
    :to="`/categories/${category.spec.slug}`"
    class="group flex flex-col bg-white rounded-lg border border-slate-200 overflow-hidden hover:shadow-md hover:border-slate-300 transition-all duration-200"
  >
    <!-- 封面图（横向） -->
    <div class="relative overflow-hidden bg-slate-100" style="aspect-ratio: 16 / 7;">
      <img
        v-if="coverUrl"
        :src="coverUrl"
        :alt="category.spec.displayName"
        referrerpolicy="no-referrer"
        loading="lazy"
        class="w-full h-full object-cover group-hover:scale-[1.03] transition-transform duration-300"
      />
      <div v-else class="w-full h-full flex items-center justify-center" style="background: linear-gradient(135deg,#0562a9,#4d9bd8);">
        <span class="text-white text-3xl font-bold opacity-90">{{ category.spec.displayName.charAt(0) }}</span>
      </div>
      <!-- 状态徽章：已完结（绿）/ 连载中（紫），来源见 utils/tutorial.ts -->
      <span
        :class="status === 'completed' ? 'badge-complete' : 'badge-updating'"
        class="absolute top-3 right-3 px-2.5 py-0.5 text-xs font-medium rounded-full shadow-sm"
      >
        {{ statusLabel }}
      </span>
    </div>

    <!-- 信息区 -->
    <div class="p-4">
      <h3 class="text-lg font-bold text-slate-900 group-hover:text-[#0562a9] transition-colors mb-1.5">
        {{ category.spec.displayName }}
      </h3>
      <p class="text-sm text-slate-500 line-clamp-2 leading-relaxed">
        {{ category.spec.description || '暂无描述' }}
      </p>
      <div class="mt-2.5 flex items-center gap-1.5 text-xs text-slate-400">
        <svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
            d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
        </svg>
        <span>{{ category.status.visiblePostCount }} 篇文章</span>
        <template v-if="lastUpdated">
          <span>·</span>
          <span>更新于 {{ formatDateCN(lastUpdated) }}</span>
        </template>
      </div>
    </div>
  </NuxtLink>
</template>

<script setup lang="ts">
import type { HaloCategory } from "~/types/halo";
import { getTutorialStatus, TUTORIAL_STATUS_LABEL, formatDateCN } from "~/utils/tutorial";

const props = defineProps<{
  category: HaloCategory;
  fallbackCover?: string;
  /** 该分类下最新一篇文章的发布时间（ISO 字符串），由首页计算传入 */
  lastUpdated?: string;
}>();

const coverUrl = computed(() => props.category.spec.cover || props.fallbackCover || "");

const status = computed(() => getTutorialStatus(props.category));
const statusLabel = computed(() => TUTORIAL_STATUS_LABEL[status.value]);
</script>
