<template>
  <div class="interview-accordion">
    <div
      v-for="topic in topics"
      :key="topic.category.metadata.name"
      class="interview-accordion-item"
      :class="{ 'interview-accordion-item--open': isOpen(topic.category.metadata.name) }"
    >
      <button
        type="button"
        class="interview-accordion-header"
        :aria-expanded="isOpen(topic.category.metadata.name)"
        @click="toggle(topic.category.metadata.name)"
      >
        <div class="interview-accordion-header-left">
          <img
            v-if="topic.category.spec.cover"
            :src="topic.category.spec.cover"
            :alt="topic.category.spec.displayName"
            width="24"
            height="24"
            loading="lazy"
            class="interview-category-img"
            referrerpolicy="no-referrer"
          />
          <span v-else class="interview-category-icon" aria-hidden="true">
            <svg
              width="14"
              height="14"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
            >
              <path d="M4 19.5A2.5 2.5 0 016.5 17H20" />
              <path d="M6.5 2H20v20H6.5A2.5 2.5 0 014 19.5v-15A2.5 2.5 0 016.5 2z" />
            </svg>
          </span>
          <h2 class="interview-category-title">{{ topic.category.spec.displayName }}</h2>
        </div>
        <div class="interview-accordion-header-right">
          <span class="interview-count-badge">{{ topic.posts.length }}</span>
          <svg
            class="interview-chevron"
            width="16"
            height="16"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="2"
          >
            <path d="M19 9l-7 7-7-7" />
          </svg>
        </div>
      </button>

      <div class="interview-accordion-body" role="region">
        <ul class="interview-question-list">
          <li
            v-for="(post, index) in topic.posts"
            :key="post.metadata.name"
            class="interview-question-item"
          >
            <NuxtLink
              class="interview-question-link"
              :title="post.spec.title"
              :to="`/java-interview/${post.spec.slug}`"
            >
              <span class="interview-question-index">{{ index + 1 }}</span>
              <h3 class="interview-question-title">{{ post.spec.title }}</h3>
            </NuxtLink>
          </li>
          <li v-if="topic.posts.length === 0" class="interview-question-empty">
            该专题题目更新中，敬请期待…
          </li>
        </ul>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { InterviewTopic } from "~/composables/useInterviewBank";

defineProps<{
  topics: InterviewTopic[];
}>();

// 与目标站一致：默认全部折叠，各自独立展开
const opened = ref<Set<string>>(new Set());

function isOpen(name: string): boolean {
  return opened.value.has(name);
}
function toggle(name: string) {
  const next = new Set(opened.value);
  if (next.has(name)) {
    next.delete(name);
  } else {
    next.add(name);
  }
  opened.value = next;
}
</script>

<style scoped>
.interview-accordion {
  display: flex;
  flex-direction: column;
}
.interview-accordion-item {
  border-bottom: 1px solid rgba(60, 60, 67, 0.1);
}
.interview-accordion-item:first-child {
  border-top: 1px solid rgba(60, 60, 67, 0.1);
}
.interview-accordion-header {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 18px 4px;
  background: transparent;
  border: none;
  cursor: pointer;
  text-align: left;
}
.interview-accordion-header-left {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}
.interview-category-img,
.interview-category-icon {
  width: 24px;
  height: 24px;
  border-radius: 6px;
  flex-shrink: 0;
}
.interview-category-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: rgba(20, 111, 184, 0.12);
  color: #0962a9;
}
.interview-category-title {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: #3c3c43;
}
.interview-accordion-header-right {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}
.interview-count-badge {
  min-width: 24px;
  height: 22px;
  padding: 0 7px;
  border-radius: 999px;
  background: rgba(20, 111, 184, 0.14);
  color: #0962a9;
  font-size: 12px;
  font-weight: 600;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}
.interview-chevron {
  color: rgba(60, 60, 67, 0.45);
  transition: transform 0.2s ease;
}
.interview-accordion-item--open .interview-chevron {
  transform: rotate(180deg);
}
.interview-accordion-body {
  display: grid;
  grid-template-rows: 0fr;
  transition: grid-template-rows 0.22s ease;
}
.interview-accordion-item--open .interview-accordion-body {
  grid-template-rows: 1fr;
}
.interview-question-list {
  list-style: none;
  margin: 0;
  padding: 0 0 8px;
  overflow: hidden;
  min-height: 0;
}
.interview-question-link {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 11px 4px;
  text-decoration: none;
}
.interview-question-index {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: #f6f6f7;
  color: rgba(60, 60, 67, 0.78);
  font-size: 12px;
  font-weight: 600;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.interview-question-title {
  margin: 0;
  font-size: 14.5px;
  font-weight: 400;
  color: #3c3c43;
  line-height: 1.5;
  transition: color 0.15s;
}
.interview-question-link:hover .interview-question-title {
  color: #0962a9;
}
.interview-question-empty {
  list-style: none;
  padding: 4px 4px 14px;
  font-size: 13px;
  color: rgba(60, 60, 67, 0.5);
}
</style>
