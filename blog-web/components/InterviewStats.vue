<template>
  <section class="interview-stats">
    <div class="interview-stats-inner">
      <div class="interview-stat-card">
        <div class="interview-stat-icon interview-stat-icon--blue">
          <svg
            width="20"
            height="20"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="2"
          >
            <path
              d="M19 11H5m14 0a2 2 0 012 2v6a2 2 0 01-2 2H5a2 2 0 01-2-2v-6a2 2 0 012-2m14 0V9a2 2 0 00-2-2M5 11V9a2 2 0 012-2m0 0V5a2 2 0 012-2h6a2 2 0 012 2v2M7 7h10"
            />
          </svg>
        </div>
        <div class="interview-stat-text">
          <span class="interview-stat-number">{{ topicCount }}</span>
          <span class="interview-stat-label">专题分类</span>
        </div>
      </div>

      <div class="interview-stat-card">
        <div class="interview-stat-icon interview-stat-icon--green">
          <svg
            width="20"
            height="20"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="2"
          >
            <path
              d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2m-6 9l2 2 4-4"
            />
          </svg>
        </div>
        <div class="interview-stat-text">
          <span class="interview-stat-number">{{ questionCount }}</span>
          <span class="interview-stat-label">题目总数</span>
        </div>
      </div>

      <div class="interview-stat-card">
        <div class="interview-stat-icon interview-stat-icon--teal">
          <svg
            width="20"
            height="20"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="2"
          >
            <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" />
          </svg>
        </div>
        <div class="interview-stat-text">
          <span class="interview-stat-number">免费</span>
          <span class="interview-stat-label">完全开放</span>
        </div>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
defineProps<{
  topicCount: number;
  questionCount: number;
}>();
</script>

<style scoped>
.interview-stats {
  padding: 8px 16px 20px;
  background: #fff;
}
.interview-stats-inner {
  max-width: 800px;
  margin: 0 auto;
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}
.interview-stat-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.15);
}
.interview-stat-icon {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.interview-stat-icon--blue {
  background: rgba(33, 150, 243, 0.1);
  color: #0962a9;
}
.interview-stat-icon--green {
  background: rgba(24, 121, 78, 0.1);
  color: #18794e;
}
.interview-stat-icon--teal {
  background: rgba(42, 157, 153, 0.1);
  color: #2888a7;
}
.interview-stat-text {
  display: flex;
  flex-direction: column;
  min-width: 0;
}
.interview-stat-number {
  font-size: 20px;
  font-weight: 700;
  color: #3c3c43;
  line-height: 1.2;
}
.interview-stat-label {
  font-size: 12px;
  color: rgba(60, 60, 67, 0.62);
  margin-top: 2px;
  white-space: nowrap;
}
@media (max-width: 640px) {
  .interview-stat-card {
    padding: 12px;
    gap: 8px;
  }
  .interview-stat-icon {
    width: 34px;
    height: 34px;
  }
  .interview-stat-number {
    font-size: 17px;
  }
}
</style>
