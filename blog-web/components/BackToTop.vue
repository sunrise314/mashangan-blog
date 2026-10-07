<template>
  <Transition name="btt">
    <button
      v-if="visible"
      type="button"
      class="back-to-top"
      aria-label="返回顶部"
      @click="toTop"
    >
      <svg
        class="w-5 h-5"
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        stroke-width="2"
      >
        <path d="M12 19V5M5 12l7-7 7 7" stroke-linecap="round" stroke-linejoin="round" />
      </svg>
    </button>
  </Transition>
</template>

<script setup lang="ts">
// 返回顶部：下滑超过 600px 淡入，点击平滑回顶（尊重减弱动效偏好）
const visible = ref(false);

function onScroll() {
  visible.value = window.scrollY > 600;
}

function toTop() {
  const reduce = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
  window.scrollTo({ top: 0, behavior: reduce ? "auto" : "smooth" });
}

onMounted(() => {
  window.addEventListener("scroll", onScroll, { passive: true });
  onScroll();
});

onUnmounted(() => {
  window.removeEventListener("scroll", onScroll);
});
</script>

<style scoped>
.back-to-top {
  position: fixed;
  right: 1.25rem;
  bottom: 1.25rem;
  z-index: 50;
  width: 40px;
  height: 40px;
  border-radius: 9999px;
  background: #fff;
  border: 1px solid #e2e8f0;
  color: #475569;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4px 12px rgba(15, 23, 42, 0.1);
  cursor: pointer;
  transition: color 0.2s, border-color 0.2s, transform 0.2s;
}
.back-to-top:hover {
  color: var(--color-primary);
  border-color: #7cc0ec;
  transform: translateY(-2px);
}
.btt-enter-active,
.btt-leave-active {
  transition: opacity 0.2s, transform 0.2s;
}
.btt-enter-from,
.btt-leave-to {
  opacity: 0;
  transform: translateY(8px);
}
@media (max-width: 767px) {
  .back-to-top {
    right: 1rem;
    bottom: 1rem;
  }
}
</style>
