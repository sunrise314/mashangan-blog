<template>
  <div class="column-page">
    <!-- Hero -->
    <header class="column-hero">
      <div class="column-hero-inner">
        <h1>项目实战</h1>
        <p>{{ category?.spec.description || "企业级项目从 0 到 1 实战讲解，渐进式拆解，保姆级带练。" }}</p>
      </div>
    </header>

    <!-- 分类还没建 -->
    <div v-if="!category" class="column-empty">
      <div class="column-empty-card">
        <svg width="44" height="44" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6">
          <path d="M2 3h6a4 4 0 014 4v14a3 3 0 00-3-3H2z" />
          <path d="M22 3h-6a4 4 0 00-4 4v14a3 3 0 013-3h7z" />
        </svg>
        <h2>项目实战专栏还未创建</h2>
        <p>
          在 Halo 后台新建 slug 为 <code>project</code> 的分类，在该分类下发布的文章会自动渲染成项目卡片展示在这里。
          给文章设置封面和摘要，并可通过文章注解 <code>haloweb/series-status</code>（值为 <code>updating</code> /
          <code>complete</code>）显示「连载中 / 已完结」角标。
        </p>
      </div>
    </div>

    <!-- 项目卡片 -->
    <main v-else class="column-list">
      <NuxtLink
        v-for="post in posts"
        :key="post.metadata.name"
        :to="articleLink(post)"
        class="project-card"
      >
        <div class="project-cover">
          <img
            v-if="post.spec.cover"
            :src="post.spec.cover"
            :alt="post.spec.title"
            referrerpolicy="no-referrer"
            loading="lazy"
          />
          <span v-else class="project-cover-placeholder">
            <svg width="36" height="36" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
              <path d="M2 3h6a4 4 0 014 4v14a3 3 0 00-3-3H2z" />
              <path d="M22 3h-6a4 4 0 00-4 4v14a3 3 0 013-3h7z" />
            </svg>
          </span>
          <span v-if="statusOf(post)" class="project-status" :class="statusClass(post)">
            {{ statusOf(post) === "complete" ? "已完结" : "连载中" }}
          </span>
        </div>
        <div class="project-body">
          <h2 class="project-title">{{ post.spec.title }}</h2>
          <p class="project-excerpt">{{ post.spec.excerpt?.raw || post.status.excerpt || "暂无简介" }}</p>
          <div class="project-meta">
            <span>{{ formatDate(post.status.publishTime) }}</span>
            <span class="project-more">
              查看项目
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M5 12h14M13 6l6 6-6 6" stroke-linecap="round" stroke-linejoin="round" />
              </svg>
            </span>
          </div>
        </div>
      </NuxtLink>

      <div v-if="posts.length === 0" class="column-empty">
        <div class="column-empty-card">
          <h2>专栏下还没有项目文章</h2>
          <p>在 Halo 后台向「{{ category.spec.displayName }}」分类发布文章后，这里会自动出现项目卡片。</p>
        </div>
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
import type { HaloCategory, HaloPost } from "~/types/halo";

const PROJECT_CATEGORY_SLUG = "project";

const { getCategoryBySlug, getPostsByCategory } = useHaloApi();

const { data: category } = await useAsyncData<HaloCategory | undefined>(
  "project-category",
  () => getCategoryBySlug(PROJECT_CATEGORY_SLUG, true),
);

const { data: posts } = await useAsyncData<HaloPost[]>(
  "project-posts",
  () =>
    category.value
      ? getPostsByCategory(category.value.metadata.name)
      : Promise.resolve([] as HaloPost[]),
  { watch: [() => category.value?.metadata.name] },
);

function statusOf(post: HaloPost): "updating" | "complete" | null {
  const v = post.metadata.annotations?.["haloweb/series-status"];
  return v === "updating" || v === "complete" ? v : null;
}
function statusClass(post: HaloPost): string {
  return statusOf(post) === "complete" ? "project-status--complete" : "project-status--updating";
}

/** 项目文章仍走 Halo 原生文章详情：优先用后台 permalink，兜底 slug */
function articleLink(post: HaloPost): string {
  if (post.status.permalink) return post.status.permalink;
  return `/archives/${post.spec.slug}`;
}

function formatDate(dateStr?: string): string {
  if (!dateStr) return "";
  const d = new Date(dateStr);
  if (Number.isNaN(d.getTime())) return "";
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}

useHead({
  title: "项目实战",
  meta: [{ name: "description", content: "企业级项目从 0 到 1 实战：微服务、Spring AI、高并发等专栏。" }],
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
.column-empty-card code {
  background: #f1f5f9;
  color: #db2777;
  padding: 1px 6px;
  border-radius: 4px;
  font-size: 13px;
}
@media (max-width: 768px) {
  .column-list {
    grid-template-columns: 1fr;
  }
}
</style>
