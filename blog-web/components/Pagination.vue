<template>
  <nav v-if="totalPages > 1" class="flex items-center justify-center gap-1.5 mt-8" aria-label="分页">
    <NuxtLink
      v-if="page > 1"
      :to="pageLink(page - 1)"
      class="px-3 py-1.5 text-sm rounded-md border border-slate-200 text-slate-600 hover:text-[#0562a9] hover:border-sky-300 transition-colors"
    >
      上一页
    </NuxtLink>
    <span v-else class="px-3 py-1.5 text-sm rounded-md border border-slate-100 text-slate-300 cursor-not-allowed">
      上一页
    </span>

    <template v-for="(p, idx) in pageItems" :key="idx">
      <span v-if="p === '...'" class="px-2 text-slate-400">…</span>
      <NuxtLink
        v-else
        :to="pageLink(p)"
        :class="[
          'min-w-[34px] text-center px-2 py-1.5 text-sm rounded-md border transition-colors',
          p === page
            ? 'bg-[#0562a9] border-[#0562a9] text-white font-medium'
            : 'border-slate-200 text-slate-600 hover:text-[#0562a9] hover:border-sky-300',
        ]"
      >
        {{ p }}
      </NuxtLink>
    </template>

    <NuxtLink
      v-if="page < totalPages"
      :to="pageLink(page + 1)"
      class="px-3 py-1.5 text-sm rounded-md border border-slate-200 text-slate-600 hover:text-[#0562a9] hover:border-sky-300 transition-colors"
    >
      下一页
    </NuxtLink>
    <span v-else class="px-3 py-1.5 text-sm rounded-md border border-slate-100 text-slate-300 cursor-not-allowed">
      下一页
    </span>
  </nav>
</template>

<script setup lang="ts">
const props = withDefaults(
  defineProps<{
    page: number;
    totalPages: number;
    /** URL query 中页码使用的参数名 */
    queryKey?: string;
  }>(),
  { queryKey: "page" },
);

const route = useRoute();

function pageLink(target: number) {
  const query: Record<string, unknown> = { ...route.query };
  if (target <= 1) delete query[props.queryKey];
  else query[props.queryKey] = String(target);
  return { path: route.path, query };
}

/** 生成带省略号的页码序列：首页、末页、当前页前后各 2 页 */
const pageItems = computed<(number | "...")[]>(() => {
  const total = props.totalPages;
  const current = props.page;
  if (total <= 7) return Array.from({ length: total }, (_, i) => i + 1);

  const pages = new Set<number>([1, total, current, current - 1, current - 2, current + 1, current + 2]);
  const sorted = [...pages].filter((p) => p >= 1 && p <= total).sort((a, b) => a - b);

  const result: (number | "...")[] = [];
  let prev = 0;
  for (const p of sorted) {
    if (p - prev > 1) result.push("...");
    result.push(p);
    prev = p;
  }
  return result;
});
</script>
