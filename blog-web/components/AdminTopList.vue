<script setup lang="ts">
const props = defineProps<{
  title: string;
  rows: Array<{ k: string; c: number }>;
  empty?: string;
  small?: boolean;
}>();

const max = computed(() => Math.max(1, ...props.rows.map((r) => r.c)));
</script>

<template>
  <div class="rounded-xl bg-white p-5 shadow-sm">
    <h2 class="mb-3 font-semibold text-slate-700" :class="small ? 'text-sm' : ''">{{ title }}</h2>
    <ul v-if="rows.length" class="space-y-1.5">
      <li v-for="r in rows" :key="r.k" class="text-sm">
        <div class="flex items-center justify-between gap-2">
          <span class="truncate text-slate-700" :title="r.k">{{ r.k }}</span>
          <span class="shrink-0 text-xs text-slate-400">{{ r.c }}</span>
        </div>
        <div class="mt-0.5 h-1 rounded bg-slate-100">
          <div class="h-1 rounded bg-slate-500" :style="{ width: `${(r.c / max) * 100}%` }" />
        </div>
      </li>
    </ul>
    <p v-else class="text-sm text-slate-400">{{ empty || "暂无数据" }}</p>
  </div>
</template>
