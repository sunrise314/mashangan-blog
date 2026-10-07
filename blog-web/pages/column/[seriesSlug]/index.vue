<template>
  <div class="series-page" v-if="series">
    <!-- 顶部封面 -->
    <header class="series-hero">
      <div class="series-hero-inner">
        <nav class="series-crumb">
          <NuxtLink to="/">首页</NuxtLink>
          <span>/</span>
          <NuxtLink to="/column">项目实战</NuxtLink>
        </nav>
        <div class="series-head">
          <div v-if="series.cover" class="series-cover">
            <img :src="series.cover" :alt="series.title" referrerpolicy="no-referrer" />
          </div>
          <div class="series-info">
            <div class="series-tags">
              <span class="tag" :class="series.status === 'complete' ? 'tag--complete' : 'tag--updating'">
                {{ series.status === "complete" ? "已完结" : "连载中" }}
              </span>
              <span class="tag tag--chapters">{{ series.totalChapters }} 章</span>
              <span class="tag tag--free">前 {{ series.freeChapterCount }} 章免费</span>
            </div>
            <h1>{{ series.title }}</h1>
            <p>{{ series.description }}</p>
          </div>
        </div>
      </div>
    </header>

    <!-- 章节列表 -->
    <main class="series-main">
      <ol class="chapter-list">
        <li v-for="ch in series.chapters" :key="ch.slug">
          <NuxtLink :to="`/column/${series.slug}/${ch.slug}`" class="chapter-row">
            <span class="chapter-no" :class="{ 'chapter-no--free': ch.free }">
              {{ String(ch.order).padStart(2, "0") }}
            </span>
            <span class="chapter-text">
              <span class="chapter-title">{{ ch.title }}</span>
              <span v-if="ch.free" class="chapter-badge chapter-badge--free">免费试读</span>
              <span v-else class="chapter-badge chapter-badge--locked">
                <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <rect x="3" y="11" width="18" height="11" rx="2" />
                  <path d="M7 11V7a5 5 0 0110 0v4" stroke-linecap="round" />
                </svg>
                星球专属
              </span>
            </span>
            <span class="chapter-arrow">
              <svg v-if="ch.free" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M5 12h14M13 6l6 6-6 6" stroke-linecap="round" stroke-linejoin="round" />
              </svg>
              <svg v-else width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <rect x="3" y="11" width="18" height="11" rx="2" />
                <path d="M7 11V7a5 5 0 0110 0v4" stroke-linecap="round" />
              </svg>
            </span>
          </NuxtLink>
        </li>
      </ol>

      <!-- 加入星球 CTA -->
      <div class="join-card">
        <div class="join-icon">
          <svg width="26" height="26" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
            <path d="M12 2l2.9 6.3 6.9.8-5.1 4.7 1.4 6.8L12 17.3 5.9 20.6l1.4-6.8L2.2 9.1l6.9-.8z" stroke-linejoin="round" />
          </svg>
        </div>
        <div class="join-text">
          <h2>前 {{ series.freeChapterCount }} 章免费，加入星球解锁全部 {{ series.totalChapters }} 章</h2>
          <p>完整连载 + 项目源码 + 答疑陪伴，持续更新不另行收费</p>
        </div>
        <a :href="joinUrl" target="_blank" rel="noopener" class="join-btn">加入知识星球</a>
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
import type { SeriesDetail } from "~/types/halo";
import { zsxqConfig } from "~/data/zsxq";

const route = useRoute();
const { getSeriesDetail } = useHaloApi();
const seriesSlug = route.params.seriesSlug as string;
const joinUrl = zsxqConfig.joinUrl;

const { data: series } = await useAsyncData<SeriesDetail>(`series-${seriesSlug}`, () =>
  getSeriesDetail(seriesSlug),
);

if (!series.value) {
  throw createError({ statusCode: 404, statusMessage: "项目不存在" });
}

useHead(() => ({
  title: `${series.value!.title} - 项目实战`,
  meta: [{ name: "description", content: series.value!.description || "" }],
}));
</script>

<style scoped>
.series-page {
  background: #f5f6f7;
  min-height: 100vh;
  padding-bottom: 60px;
}
.series-hero {
  background: linear-gradient(135deg, var(--color-primary) 0%, #2b8ad4 100%);
  color: #fff;
  padding: 18px 16px 34px;
}
.series-hero-inner {
  max-width: 860px;
  margin: 0 auto;
}
.series-crumb {
  font-size: 13px;
  opacity: 0.85;
  display: flex;
  gap: 8px;
  margin-bottom: 18px;
}
.series-crumb a {
  color: #fff;
  text-decoration: none;
}
.series-crumb a:hover {
  text-decoration: underline;
}
.series-head {
  display: flex;
  gap: 24px;
  align-items: flex-start;
}
.series-cover {
  width: 220px;
  flex-shrink: 0;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 10px 28px rgba(0, 0, 0, 0.22);
  aspect-ratio: 16 / 10;
  background: rgba(255, 255, 255, 0.15);
}
.series-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.series-info {
  flex: 1;
  min-width: 0;
}
.series-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}
.tag {
  padding: 3px 11px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 500;
}
.tag--complete {
  background: rgba(34, 197, 94, 0.9);
}
.tag--updating {
  background: rgba(168, 85, 247, 0.9);
}
.tag--chapters {
  background: rgba(255, 255, 255, 0.2);
}
.tag--free {
  background: rgba(255, 255, 255, 0.2);
}
.series-info h1 {
  margin: 0 0 10px;
  font-size: 26px;
  font-weight: 700;
}
.series-info p {
  margin: 0;
  font-size: 14px;
  line-height: 1.9;
  opacity: 0.92;
}
.series-main {
  max-width: 860px;
  margin: -18px auto 0;
  padding: 0 16px;
}
.chapter-list {
  list-style: none;
  margin: 0;
  padding: 0;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  overflow: hidden;
}
.chapter-row {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 15px 18px;
  text-decoration: none;
  border-bottom: 1px solid #f1f5f9;
  transition: background 0.15s;
}
.chapter-row:last-child {
  border-bottom: none;
}
.chapter-row:hover {
  background: #f8fbfe;
}
.chapter-no {
  width: 40px;
  height: 40px;
  flex-shrink: 0;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  font-weight: 700;
  background: #f1f5f9;
  color: #94a3b8;
}
.chapter-no--free {
  background: #e6f3fc;
  color: var(--color-primary);
}
.chapter-text {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 10px;
}
.chapter-title {
  font-size: 15px;
  color: #1e293b;
  font-weight: 500;
}
.chapter-badge {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  gap: 3px;
  padding: 2px 9px;
  border-radius: 999px;
  font-size: 12px;
}
.chapter-badge--free {
  background: #e6f3fc;
  color: var(--color-primary);
}
.chapter-badge--locked {
  background: #fef3e8;
  color: #c2410c;
}
.chapter-arrow {
  color: #cbd5e1;
  flex-shrink: 0;
}
.chapter-row:hover .chapter-arrow {
  color: var(--color-primary);
}
.join-card {
  margin-top: 22px;
  background: linear-gradient(135deg, #0b3d66, #0e6aa8);
  border-radius: 12px;
  padding: 24px 26px;
  display: flex;
  align-items: center;
  gap: 18px;
  color: #fff;
}
.join-icon {
  width: 52px;
  height: 52px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.12);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.join-text {
  flex: 1;
  min-width: 0;
}
.join-text h2 {
  margin: 0 0 4px;
  font-size: 16px;
  font-weight: 700;
}
.join-text p {
  margin: 0;
  font-size: 13px;
  opacity: 0.85;
}
.join-btn {
  flex-shrink: 0;
  padding: 11px 24px;
  border-radius: 999px;
  background: #fff;
  color: var(--color-primary);
  font-weight: 700;
  font-size: 14px;
  text-decoration: none;
  transition: transform 0.15s;
}
.join-btn:hover {
  transform: translateY(-2px);
}
@media (max-width: 768px) {
  .series-head {
    flex-direction: column;
  }
  .series-cover {
    width: 100%;
    max-width: 320px;
  }
  .join-card {
    flex-direction: column;
    text-align: center;
  }
}
</style>
