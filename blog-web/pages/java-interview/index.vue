<template>
  <div class="interview-page">
    <InterviewHero :updated-at="bank?.latestDate ?? null" />
    <InterviewStats
      :topic-count="bank?.topics.length ?? 0"
      :question-count="bank?.posts.length ?? 0"
    />

    <!-- 视图切换：按分类 / 按发布时间 -->
    <nav class="interview-view-switch">
      <NuxtLink
        class="interview-switch-btn"
        :class="{ 'interview-switch-btn--active': !isTimeline }"
        to="/java-interview"
      >
        按分类
      </NuxtLink>
      <NuxtLink
        class="interview-switch-btn"
        :class="{ 'interview-switch-btn--active': isTimeline }"
        to="/java-interview?sort=time"
      >
        按发布时间
      </NuxtLink>
    </nav>

    <!-- 题库尚未在 Halo 后台创建 -->
    <section v-if="!bank?.root" class="interview-empty">
      <div class="interview-empty-inner">
        <svg
          width="44"
          height="44"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="1.6"
        >
          <path
            d="M12 6.253v13m0-13C10.832 5.477 9.246 5 7.5 5S4.168 5.477 3 6.253v13C4.168 18.477 5.754 18 7.5 18s3.332.477 4.5 1.253m0-13C13.168 5.477 14.754 5 16.5 5c1.747 0 3.332.477 4.5 1.253v13C19.832 18.477 18.247 18 16.5 18c-1.746 0-3.332.477-4.5 1.253"
          />
        </svg>
        <h2>题库还未初始化</h2>
        <p>
          需要在 Halo 后台创建 slug 为 <code>java-interview</code> 的父分类，并为其挂载专题子分类；
          也可以运行项目自带的种子脚本 <code>web/scripts/seed_interview.py</code>
          一键创建 24 个专题和示例题。
        </p>
      </div>
    </section>

    <!-- 按分类：手风琴 -->
    <section v-else-if="!isTimeline" class="interview-categories">
      <div class="interview-categories-inner">
        <InterviewAccordion :topics="bank.topics" />
      </div>
    </section>

    <!-- 按发布时间：时间线列表 -->
    <section v-else class="interview-time-view">
      <div class="interview-time-inner">
        <ul class="interview-time-list">
          <li
            v-for="(item, index) in timeline"
            :key="item.post.metadata.name"
            class="interview-time-item"
          >
            <span class="interview-time-index">{{ index + 1 }}</span>
            <NuxtLink
              class="interview-time-title"
              :title="item.post.spec.title"
              :to="`/java-interview/${item.post.spec.slug}`"
            >
              {{ item.post.spec.title }}
            </NuxtLink>
            <span class="interview-time-cat-badge">{{ item.topic.category.spec.displayName }}</span>
            <span class="interview-time-date">{{ formatDate(item.post.status.publishTime) }}</span>
          </li>
          <li v-if="timeline.length === 0" class="interview-time-empty">还没有发布任何题目</li>
        </ul>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import type { HaloPost } from "~/types/halo";
import type { InterviewBank, InterviewTopic } from "~/composables/useInterviewBank";
import { useInterviewBank } from "~/composables/useInterviewBank";

const route = useRoute();
const { fetchBank } = useInterviewBank();

const { data: bank } = await useAsyncData<InterviewBank>("interview-bank", () => fetchBank());

const isTimeline = computed(() => route.query.sort === "time");

const timeline = computed(() => {
  if (!bank.value) return [];
  const topicByCategory = new Map(
    bank.value.topics.flatMap((t) => t.posts.map((p) => [p.metadata.name, t] as const)),
  );
  return bank.value.posts
    .slice()
    .sort(
      (a, b) =>
        new Date(
          b.status.publishTime || b.spec.publishTime || b.metadata.creationTimestamp,
        ).getTime() -
        new Date(
          a.status.publishTime || a.spec.publishTime || a.metadata.creationTimestamp,
        ).getTime(),
    )
    .map((post) => ({ post, topic: topicByCategory.get(post.metadata.name) }))
    .filter((x): x is { post: HaloPost; topic: InterviewTopic } => Boolean(x.topic));
});

function formatDate(dateStr?: string): string {
  if (!dateStr) return "";
  const d = new Date(dateStr);
  if (Number.isNaN(d.getTime())) return "";
  return `${d.getFullYear()}/${String(d.getMonth() + 1).padStart(2, "0")}/${String(d.getDate()).padStart(2, "0")}`;
}

useHead({
  title: "Java 面试题 | 八股文汇总（含答案，图文讲解）",
  meta: [
    {
      name: "description",
      content:
        "汇总 Java 面试高频考题与经典八股文：Java 基础、集合、JVM、并发、Spring、MySQL、Redis、RocketMQ、分布式等专题，持续更新，完全免费。",
    },
  ],
});
</script>

<style scoped>
.interview-page {
  background: #fff;
  min-height: 100vh;
}
.interview-view-switch {
  max-width: 800px;
  margin: 0 auto;
  padding: 0 16px 20px;
  display: flex;
  gap: 8px;
  background: #fff;
}
.interview-switch-btn {
  padding: 6px 18px;
  border-radius: 999px;
  border: 1px solid rgba(60, 60, 67, 0.2);
  font-size: 14px;
  color: rgba(60, 60, 67, 0.78);
  text-decoration: none;
  transition: all 0.15s;
}
.interview-switch-btn:hover {
  color: #0962a9;
  border-color: #0962a9;
}
.interview-switch-btn--active,
.interview-switch-btn--active:hover {
  background: #0962a9;
  border-color: #0962a9;
  color: #fff;
  font-weight: 500;
}
.interview-categories,
.interview-time-view {
  background: #fff;
  padding: 0 0 48px;
}
.interview-categories-inner,
.interview-time-inner {
  max-width: 800px;
  margin: 0 auto;
  padding: 0 16px;
}
.interview-time-list {
  list-style: none;
  margin: 0;
  padding: 0;
}
.interview-time-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 4px;
  border-bottom: 1px solid rgba(60, 60, 67, 0.08);
}
.interview-time-item:first-child {
  border-top: 1px solid rgba(60, 60, 67, 0.08);
}
.interview-time-index {
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
.interview-time-title {
  flex: 1;
  min-width: 0;
  font-size: 14.5px;
  color: #3c3c43;
  text-decoration: none;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  transition: color 0.15s;
}
.interview-time-title:hover {
  color: #0962a9;
}
.interview-time-cat-badge {
  flex-shrink: 0;
  padding: 2px 10px;
  border-radius: 999px;
  background: rgba(20, 111, 184, 0.1);
  color: #0962a9;
  font-size: 12px;
  white-space: nowrap;
}
.interview-time-date {
  flex-shrink: 0;
  font-size: 13px;
  color: rgba(60, 60, 67, 0.5);
  width: 84px;
  text-align: right;
}
.interview-time-empty {
  list-style: none;
  padding: 40px 0;
  text-align: center;
  color: rgba(60, 60, 67, 0.5);
  font-size: 14px;
}
.interview-empty {
  background: #fff;
  padding: 24px 16px 64px;
}
.interview-empty-inner {
  max-width: 800px;
  margin: 0 auto;
  text-align: center;
  padding: 56px 24px;
  border: 1px dashed rgba(60, 60, 67, 0.2);
  border-radius: 12px;
  color: rgba(60, 60, 67, 0.65);
}
.interview-empty-inner svg {
  color: rgba(9, 98, 169, 0.6);
  margin-bottom: 16px;
}
.interview-empty-inner h2 {
  margin: 0 0 10px;
  font-size: 18px;
  color: #3c3c43;
}
.interview-empty-inner p {
  margin: 0;
  font-size: 14px;
  line-height: 1.9;
}
.interview-empty-inner code {
  background: #f1f5f9;
  color: #db2777;
  padding: 1px 6px;
  border-radius: 4px;
  font-size: 13px;
}
@media (max-width: 640px) {
  .interview-time-date {
    display: none;
  }
}
</style>
