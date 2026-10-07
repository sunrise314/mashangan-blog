<template>
  <div class="reading-progress" aria-hidden="true">
    <div class="reading-progress-bar" :style="{ transform: `scaleX(${progress})` }"></div>
  </div>
</template>

<script setup lang="ts">
// 阅读进度条：随滚动更新的品牌色细条（rAF 节流，仅阅读页挂载）
const progress = ref(0);
let raf = 0;

function update() {
  raf = 0;
  const doc = document.documentElement;
  const max = doc.scrollHeight - window.innerHeight;
  progress.value = max > 0 ? Math.min(1, Math.max(0, window.scrollY / max)) : 0;
}

function onScroll() {
  if (!raf) raf = requestAnimationFrame(update);
}

onMounted(() => {
  window.addEventListener("scroll", onScroll, { passive: true });
  window.addEventListener("resize", onScroll, { passive: true });
  update();
});

onUnmounted(() => {
  window.removeEventListener("scroll", onScroll);
  window.removeEventListener("resize", onScroll);
  if (raf) cancelAnimationFrame(raf);
});
</script>

<style scoped>
.reading-progress {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  height: 3px;
  z-index: 60;
  pointer-events: none;
}
.reading-progress-bar {
  height: 100%;
  transform-origin: 0 50%;
  transform: scaleX(0);
  background: linear-gradient(90deg, var(--color-primary), var(--color-primary-light));
}
</style>
