<template>
  <div class="column-page">
    <!-- Hero -->
    <header class="column-hero">
      <div class="column-hero-inner">
        <h1>项目实战</h1>
        <p>企业级项目从 0 到 1 实战讲解，渐进式拆解，保姆级带练。每个项目可免费试读前 2 章，后续章节加入知识星球解锁。</p>
      </div>
    </header>

    <!-- 项目卡片：一个卡片 = 一个系列/项目 -->
    <main v-if="series.length" class="column-list">
      <NuxtLink
        v-for="s in series"
        :key="s.slug"
        :to="`/column/${s.slug}`"
        class="project-card"
      >
        <div class="project-cover">
          <img
            v-if="s.cover"
            :src="s.cover"
            :alt="s.title"
            referrerpolicy="no-referrer"
            loading="lazy"
          />
          <span v-else class="project-cover-placeholder">
            <svg width="36" height="36" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
              <path d="M2 3h6a4 4 0 014 4v14a3 3 0 00-3-3H2z" />
              <path d="M22 3h-6a4 4 0 00-4 4v14a3 3 0 013-3h7z" />
            </svg>
          </span>
          <span class="project-status" :class="s.status === 'complete' ? 'project-status--complete' : 'project-status--updating'">
            {{ s.status === "complete" ? "已完结" : "连载中" }}
          </span>
        </div>
        <div class="project-body">
          <h2 class="project-title">{{ s.title }}</h2>
          <p class="project-excerpt">{{ s.description || "暂无简介" }}</p>
          <div class="project-meta">
            <span>{{ s.chapterCount }} 章</span>
            <span class="project-more">
              查看项目
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M5 12h14M13 6l6 6-6 6" stroke-linecap="round" stroke-linejoin="round" />
              </svg>
            </span>
          </div>
        </div>
      </NuxtLink>
    </main>

    <!-- 空状态 -->
    <div v-else-if="!pending" class="column-empty">
      <div class="column-empty-card">
        <svg width="44" height="44" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6">
          <path d="M2 3h6a4 4 0 014 4v14a3 3 0 00-3-3H2z" />
          <path d="M22 3h-6a4 4 0 00-4 4v14a3 3 0 013-3h7z" />
        </svg>
        <h2>项目实战专栏还没有项目</h2>
        <p>在后台创建系列并挂接章节文章后，这里会自动出现项目卡片。</p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { SeriesCard } from "~/types/halo";

const { getSeries } = useHaloApi();

const { data: series, pending } = await useAsyncData<SeriesCard[]>("series-cards", () =>
  getSeries(),
);

useHead({
  title: "项目实战",
  meta: [{ name: "description", content: "企业级项目从 0 到 1 实战：数字孪生、微服务、Spring AI、高并发等专栏，免费试读前2章。" }],
});
</script>

<style scoped>
.column-page {
  background: #f5f6f7;
  min-height: 100vh;
}
.column-hero {
  background: linear-gradient(135deg, #0962a9 0%, #2b8ad4 100%);
  color: #fff;
  padding: 44px 16px 40px;
}
.column-hero-inner {
  max-width: 960px;
  margin: 0 auto;
}
.column-hero h1 {
  margin: 0 0 10px;
  font-size: 28px;
  font-weight: 700;
}
.column-hero p {
  margin: 0;
  font-size: 15px;
  opacity: 0.92;
  line-height: 1.8;
}
.column-list {
  max-width: 960px;
  margin: 0 auto;
  padding: 28px 16px 56px;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 20px;
}
.project-card {
  display: flex;
  flex-direction: column;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  overflow: hidden;
  text-decoration: none;
  transition: all 0.18s;
}
.project-card:hover {
  border-color: #7cc0ec;
  box-shadow: 0 8px 22px rgba(9, 98, 169, 0.12);
  transform: translateY(-3px);
}
.project-cover {
  position: relative;
  aspect-ratio: 16 / 8;
  background: linear-gradient(135deg, #0962a9, #4d9bd8);
  overflow: hidden;
}
.project-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.3s;
}
.project-card:hover .project-cover img {
  transform: scale(1.04);
}
.project-cover-placeholder {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  color: rgba(255, 255, 255, 0.75);
}
.project-status {
  position: absolute;
  top: 12px;
  left: 12px;
  padding: 3px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 500;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
}
.project-status--complete {
  background: linear-gradient(135deg, #15803d, #16a34a);
  color: #fff;
}
.project-status--updating {
  background: linear-gradient(135deg, #6d28d9, #7c3aed);
  color: #fff;
}
.project-body {
  padding: 18px 20px 20px;
  display: flex;
  flex-direction: column;
  flex: 1;
}
.project-title {
  margin: 0 0 8px;
  font-size: 18px;
  font-weight: 700;
  color: #0f172a;
}
.project-excerpt {
  margin: 0;
  font-size: 14px;
  color: #64748b;
  line-height: 1.7;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
  flex: 1;
}
.project-meta {
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px solid #f1f5f9;
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 13px;
  color: #94a3b8;
}
.project-more {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: #0962a9;
  font-weight: 500;
}
.column-empty {
  max-width: 960px;
  margin: 0 auto;
  padding: 28px 16px 56px;
}
.column-empty-card {
  background: #fff;
  border: 1px dashed #cbd5e1;
  border-radius: 12px;
  padding: 48px 24px;
  text-align: center;
  color: #64748b;
}
.column-empty-card svg {
  color: rgba(9, 98, 169, 0.6);
  margin-bottom: 14px;
}
.column-empty-card h2 {
  margin: 0 0 10px;
  font-size: 18px;
  color: #334155;
}
.column-empty-card p {
  margin: 0;
  font-size: 14px;
  line-height: 1.9;
}
@media (max-width: 768px) {
  .column-list {
    grid-template-columns: 1fr;
  }
}
</style>
