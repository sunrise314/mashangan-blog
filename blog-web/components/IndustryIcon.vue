<template>
  <svg
    :width="size"
    :height="size"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    stroke-width="1.6"
    stroke-linecap="round"
    stroke-linejoin="round"
    v-html="paths"
  />
</template>

<script setup lang="ts">
// 行业图标：按行业 slug 映射 24x24 线性图标（未知 slug 回退网格图标）
const props = withDefaults(defineProps<{ slug: string; size?: number }>(), { size: 24 });

const ICONS: Record<string, string> = {
  manufacturing: '<path d="M2 20V9l6 4V9l6 4V4h8v16H2z"/><path d="M6 16h2M12 16h2M18 16h2"/>',
  finance: '<path d="M12 3 3 8v2h18V8z"/><path d="M5 10v7M9 10v7M15 10v7M19 10v7"/><path d="M3 20h18"/>',
  'ecommerce-retail': '<path d="M5 8h14l-1.2 12H6.2z"/><path d="M9 10V6a3 3 0 016 0v4"/>',
  'internet-social': '<circle cx="12" cy="12" r="9"/><path d="M3 12h18M12 3c3 3.5 3 14.5 0 18M12 3c-3 3.5-3 14.5 0 18"/>',
  'gov-enterprise': '<path d="M12 3 3 9h18z"/><path d="M5 9v8M9 9v8M15 9v8M19 9v8"/><path d="M3 21h18"/>',
  healthcare: '<path d="M12 21C7 16.5 3 13 3 9a5 5 0 019-3 5 5 0 019 3c0 4-4 7.5-9 12z"/>',
  logistics: '<path d="M2 6h12v10H2zM14 10h4l3 3v3h-7z"/><circle cx="6" cy="18" r="1.6"/><circle cx="17" cy="18" r="1.6"/>',
  education: '<path d="M2 9l10-5 10 5-10 5z"/><path d="M6 11.5V16c0 1.6 2.7 3 6 3s6-1.4 6-3v-4.5"/>',
  'energy-power': '<path d="M13 2 4 14h6l-1 8 9-12h-6z"/>',
  'travel-transport': '<rect x="5" y="3" width="14" height="14" rx="3"/><path d="M5 11h14M9 21l1.5-4M15 21l-1.5-4"/><circle cx="9" cy="14" r="0.9"/><circle cx="15" cy="14" r="0.9"/>',
  gaming: '<rect x="2" y="8" width="20" height="10" rx="5"/><path d="M7 11v4M5 13h4"/><circle cx="16" cy="12" r="0.8"/><circle cx="18.5" cy="14" r="0.8"/>',
  saas: '<path d="M12 3 2 8l10 5 10-5z"/><path d="M2 13l10 5 10-5"/>',
};

const paths = computed(() => ICONS[props.slug] || '<path d="M12 3v18M3 12h18"/>');
</script>
