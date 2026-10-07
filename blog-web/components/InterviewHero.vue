<template>
  <section class="interview-hero">
    <div class="interview-hero-inner">
      <span class="interview-badge">
        持续更新中<span class="interview-badge-dot" />
      </span>
      <h1 class="interview-hero-title">Java 面试题 | 八股文</h1>
      <p class="interview-hero-desc">
        本题库汇总了 Java 面试中常见的高频考题和经典八股文，搭配丰富的图文讲解，希望对备战校招、跳槽晋升的小伙伴有所帮助
      </p>
      <div v-if="updatedLabel" class="interview-hero-update">
        <svg
          xmlns="http://www.w3.org/2000/svg"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="2"
          stroke-linecap="round"
          stroke-linejoin="round"
        >
          <circle cx="12" cy="12" r="10" />
          <polyline points="12 6 12 12 16 14" />
        </svg>
        <time>最近更新于 {{ updatedLabel }}</time>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed } from "vue";

const props = defineProps<{
  /** ISO 时间字符串 */
  updatedAt: string | null;
}>();

const updatedLabel = computed(() => {
  if (!props.updatedAt) return "";
  const d = new Date(props.updatedAt);
  if (Number.isNaN(d.getTime())) return "";
  return `${d.getFullYear()}年${d.getMonth() + 1}月${d.getDate()}日`;
});
</script>

<style scoped>
.interview-hero {
  padding: 40px 16px 24px;
  background: #fff;
}
.interview-hero-inner {
  max-width: 800px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
}
.interview-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 12px;
  border-radius: 999px;
  background: rgba(20, 111, 184, 0.14);
  color: var(--color-primary);
  font-size: 13px;
  font-weight: 500;
  line-height: 1.6;
}
.interview-badge-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--color-primary);
  animation: interview-pulse 1.6s ease-in-out infinite;
}
@keyframes interview-pulse {
  0%,
  100% {
    opacity: 1;
    transform: scale(1);
  }
  50% {
    opacity: 0.35;
    transform: scale(0.8);
  }
}
.interview-hero-title {
  margin: 16px 0 12px;
  font-size: 28px;
  font-weight: 700;
  color: #3c3c43;
  line-height: 1.3;
}
.interview-hero-desc {
  margin: 0;
  font-size: 15px;
  line-height: 1.8;
  color: rgba(60, 60, 67, 0.78);
}
.interview-hero-update {
  margin-top: 16px;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: rgba(60, 60, 67, 0.6);
}
.interview-hero-update svg {
  width: 1em;
  height: 1em;
}
</style>
