<template>
  <div class="max-w-4xl mx-auto px-4 py-8">
    <!-- 面包屑 -->
    <nav class="flex items-center gap-2 text-sm text-slate-500 mb-6">
      <NuxtLink to="/" class="hover:text-[#0562a9]">首页</NuxtLink>
      <span>/</span>
      <span class="text-slate-700">{{ page?.spec.title }}</span>
    </nav>

    <article v-if="page" class="bg-white rounded-xl border border-slate-200 p-8">
      <h1 class="text-3xl font-bold text-slate-900 mb-4">{{ page.spec.title }}</h1>
      <div
        class="flex items-center gap-4 text-sm text-slate-500 mb-8 pb-4 border-b border-slate-100"
      >
        <span v-if="page.status.publishTime">发布于 {{ formatDate(page.status.publishTime) }}</span>
      </div>
      <div class="prose-halo max-w-none" v-html="safeContent"></div>
    </article>
  </div>
</template>

<script setup lang="ts">
// 独立页面（关于、隐私政策、工具/星球/特价页等，菜单中选择「页面」类型时指向的链接 /{slug}）
// 取数逻辑与菜单悬停预取共享（composables/useSinglePageBySlug.ts，同 key 缓存）：
// 预取命中时 await 同步复用、零请求；未预取时等待数据（slug 不存在抛 404）
const route = useRoute();

const slug = decodeSlug(route.params.slug as string);

const { data: page } = await useSinglePageBySlug(slug);

// 404 必须在 setup 上下文抛出才会进错误页（fetcher 内抛错会被 useAsyncData 捕获吞掉）
if (!page.value) {
  throw createError({ statusCode: 404, message: "页面不存在", fatal: true });
}

// 消毒独立页面正文，防止存储型 XSS
const safeContent = computed(() => sanitizeHtml(page.value?.content?.content ?? ""));

function formatDate(dateStr: string): string {
  if (!dateStr) return "";
  const d = new Date(dateStr);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}

useHead(() => ({ title: page.value?.spec.title ?? "页面" }));
</script>
