<template>
  <div class="bg-white rounded-lg border border-slate-200 p-4 sticky top-20">
    <h3 class="font-bold text-slate-900 mb-3 pb-2 border-b border-slate-100 text-sm">
      目录
    </h3>
    <nav v-if="toc.length" class="space-y-0.5 max-h-[70vh] overflow-y-auto pr-1">
      <a
        v-for="item in toc"
        :key="item.id"
        :href="`#${item.id}`"
        class="block text-sm leading-relaxed transition-colors truncate"
        :class="levelClass(item)"
        :style="{ paddingLeft: `${(item.level - 1) * 12}px` }"
        @click.prevent="scrollTo(item.id)"
      >
        {{ item.text }}
      </a>
    </nav>
    <p v-else class="text-xs text-slate-400">暂无目录</p>
  </div>
</template>

<script setup lang="ts">
import type { TocItem } from "~/composables/useToc";

const props = defineProps<{
  toc: TocItem[];
  activeId?: string;
}>();

function levelClass(item: TocItem): string {
  const active = props.activeId === item.id;
  if (active) return "text-[#0562a9] font-medium bg-sky-50 -mx-1 px-1 rounded";
  return "text-slate-600 hover:text-[#0562a9]";
}

function scrollTo(id: string) {
  const el = document.getElementById(id);
  if (el) {
    el.scrollIntoView({ behavior: "smooth", block: "start" });
    history.replaceState(null, "", `#${id}`);
  }
}
</script>
