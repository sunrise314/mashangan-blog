<template>
  <div class="max-w-5xl mx-auto px-4 py-8">
    <!-- 面包屑 -->
    <nav class="flex items-center gap-2 text-sm text-slate-500 mb-6">
      <NuxtLink to="/" class="hover:text-[#0562a9]">首页</NuxtLink>
      <span>/</span>
      <span class="text-slate-700">标签云</span>
    </nav>

    <header class="bg-white rounded-xl border border-slate-200 p-8 mb-8">
      <h1 class="text-3xl font-bold text-slate-900 mb-2">标签云</h1>
      <p class="text-slate-600">按技术主题聚合全站文章，点击标签查看相关内容。</p>
    </header>

    <div v-if="tags.length" class="bg-white rounded-xl border border-slate-200 p-8 flex flex-wrap gap-3">
      <NuxtLink
        v-for="t in tags"
        :key="t.metadata.name"
        :to="`/tags/${t.spec.slug}`"
        class="tag-chip"
        :style="chipStyle(t.postCount)"
      >
        {{ t.spec.displayName }}
        <span class="tag-count">{{ t.postCount }}</span>
      </NuxtLink>
    </div>
    <div v-else-if="!pending" class="bg-white rounded-xl border border-slate-200 p-8 text-center text-slate-500">
      暂无标签
    </div>
  </div>
</template>

<script setup lang="ts">
import type { TagCard } from "~/types/halo";

const { getTags } = useHaloApi();

const { data: tags, pending } = await useAsyncData<TagCard[]>("tags-cloud", () => getTags());

// 站点名尾巴与 description
const siteCfg = useSiteConfig();
const runtimeCfg = useRuntimeConfig();
const siteName = computed(
  () => siteCfg.value?.config?.title || (runtimeCfg.public.siteTitle as string) || "码上岸",
);

useHead({
  title: `标签云 - ${siteName.value}`,
  meta: [{ name: "description", content: "全站文章按技术主题聚合：Java、Spring、MySQL、Redis、Docker、Go 等标签归档入口。" }],
});

/** 文章数越多字号越大（13~22px），颜色深浅随之加深 */
function chipStyle(count: number) {
  const size = Math.min(22, 13 + Math.sqrt(count) * 2);
  const alpha = Math.min(0.85, 0.55 + Math.log10(count + 1) * 0.12);
  return { fontSize: `${size}px`, color: `rgba(5, 98, 169, ${alpha})` };
}
</script>

<style scoped>
.tag-chip {
  display: inline-flex;
  align-items: baseline;
  gap: 4px;
  padding: 6px 12px;
  border-radius: 999px;
  background: #f1f7fc;
  border: 1px solid #dbeafe;
  font-weight: 600;
  transition: all 0.15s;
}
.tag-chip:hover {
  background: #e0f0fb;
  border-color: #7cc0ec;
  transform: translateY(-1px);
}
.tag-count {
  font-size: 11px;
  font-weight: 500;
  color: #94a3b8;
}
</style>
