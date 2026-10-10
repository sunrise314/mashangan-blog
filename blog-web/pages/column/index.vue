<template>
  <div class="column-page">
    <!-- Hero -->
    <header class="column-hero">
      <div class="column-hero-inner">
        <h1>项目实战</h1>
        <p>按 12 大行业整理的高频企业级项目地图：已开更项目从 0 到 1 渐进式拆解，筹备中项目附面试考点清单。每个项目可免费试读前 2 章，后续章节加入知识星球解锁。</p>
      </div>
    </header>

    <!-- 行业 chips 筛选 -->
    <nav v-if="hasMap" class="industry-chips-wrap">
      <div class="industry-chips">
        <button
          class="chip"
          :class="{ 'chip--active': activeIndustry === 'all' }"
          type="button"
          @click="activeIndustry = 'all'"
        >
          全部行业
          <span class="chip-count">{{ totalProjects }}</span>
        </button>
        <button
          v-for="ind in industries"
          :key="ind.slug"
          class="chip"
          :class="{ 'chip--active': activeIndustry === ind.slug }"
          type="button"
          @click="activeIndustry = ind.slug"
        >
          <IndustryIcon :slug="ind.slug" :size="15" />
          {{ ind.name }}
          <span class="chip-count">{{ ind.projects.length }}</span>
        </button>
      </div>
    </nav>

    <!-- 行业项目地图 -->
    <main v-if="hasMap" class="industry-map">
      <section v-for="ind in shownIndustries" :key="ind.slug" class="industry-section">
        <header class="industry-head">
          <span class="industry-icon"><IndustryIcon :slug="ind.slug" :size="22" /></span>
          <div class="industry-head-text">
            <h2>{{ ind.name }}</h2>
            <p>{{ ind.intro }}</p>
          </div>
          <NuxtLink :to="`/column/industry/${ind.slug}`" class="industry-link">
            {{ ind.name }}全部项目
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M5 12h14M13 6l6 6-6 6" stroke-linecap="round" stroke-linejoin="round" />
            </svg>
          </NuxtLink>
        </header>
        <div class="column-list">
          <ProjectMapCard
            v-for="p in ind.projects"
            :key="p.slug"
            :project="p"
            :industry-slug="ind.slug"
            :cover="p.open ? seriesMap.get(p.seriesSlug || '')?.cover || '' : ''"
            :chapter-count="p.open ? seriesMap.get(p.seriesSlug || '')?.chapterCount || 0 : 0"
            :series-status="p.open ? seriesMap.get(p.seriesSlug || '')?.status || 'updating' : 'updating'"
          />
        </div>
      </section>
    </main>

    <!-- 兜底：行业地图数据未就绪时退回系列卡片列表 -->
    <main v-else-if="series.length" class="column-list column-list--solo">
      <NuxtLink
        v-for="s in series"
        :key="s.slug"
        :to="`/column/${s.slug}`"
        class="project-card"
      >
        <div class="project-cover">
          <img v-if="s.cover" :src="s.cover" :alt="s.title" referrerpolicy="no-referrer" loading="lazy" />
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
            <span class="project-more">查看项目</span>
          </div>
        </div>
      </NuxtLink>
    </main>

    <!-- 空状态 -->
    <div v-else class="column-empty">
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
import type { IndustryDetail, SeriesCard } from "~/types/halo";

const { getSeries, getIndustries } = useHaloApi();

const { data: seriesList, pending } = await useAsyncData<SeriesCard[]>("series-cards", () =>
  getSeries(),
);
// 行业地图：接口未就绪（404/500）时静默回退到系列卡片列表，保证 /column 永远有内容
const { data: industryList } = await useAsyncData<IndustryDetail[]>("industry-map", () =>
  getIndustries().catch(() => [] as IndustryDetail[]),
);

// 已下架（hidden）的系列不进卡片列表；章节页与 sitemap/rss 不受影响
const series = computed(() => (seriesList.value ?? []).filter((s) => !s.hidden));
const seriesMap = computed(
  () => new Map(series.value.map((s) => [s.slug, s])),
);

const industries = computed(() => industryList.value ?? []);
const hasMap = computed(() => industries.value.length > 0);
const totalProjects = computed(() =>
  industries.value.reduce((sum, i) => sum + i.projects.length, 0),
);

// 行业 chips：一次拉全、纯前端筛选
const activeIndustry = ref("all");
const shownIndustries = computed(() =>
  activeIndustry.value === "all"
    ? industries.value
    : industries.value.filter((i) => i.slug === activeIndustry.value),
);

useHead({
  title: "项目实战",
  meta: [{ name: "description", content: "12 大行业的高频企业级项目实战地图：MES、秒杀、IM、网约车、储能 EMS 等项目从 0 到 1 拆解，筹备中项目附面试考点清单。" }],
});
</script>

<style scoped>
.column-page {
  background: #f5f6f7;
  min-height: 100vh;
}
.column-hero {
  background: linear-gradient(135deg, var(--color-primary) 0%, #2b8ad4 100%);
  color: #fff;
  padding: 44px 16px 40px;
}
.column-hero-inner {
  max-width: 1024px;
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

/* 行业 chips */
.industry-chips-wrap {
  max-width: 1024px;
  margin: 0 auto;
  padding: 22px 16px 0;
}
.industry-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}
.chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 7px 14px;
  border-radius: 999px;
  border: 1px solid #e2e8f0;
  background: #fff;
  color: #475569;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s;
}
.chip:hover {
  border-color: #7cc0ec;
  color: var(--color-primary);
}
.chip--active {
  background: var(--color-primary);
  border-color: var(--color-primary);
  color: #fff;
}
.chip-count {
  padding: 0 7px;
  border-radius: 999px;
  font-size: 11px;
  background: rgba(100, 116, 139, 0.12);
  color: inherit;
}
.chip--active .chip-count {
  background: rgba(255, 255, 255, 0.25);
}

/* 行业分区 */
.industry-map {
  max-width: 1024px;
  margin: 0 auto;
  padding: 20px 16px 56px;
}
.industry-section {
  margin-top: 26px;
}
.industry-section:first-child {
  margin-top: 8px;
}
.industry-head {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 14px;
}
.industry-icon {
  width: 44px;
  height: 44px;
  flex-shrink: 0;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, var(--color-primary), #4d9bd8);
  color: #fff;
}
.industry-head-text {
  flex: 1;
  min-width: 0;
}
.industry-head-text h2 {
  margin: 0;
  font-size: 19px;
  font-weight: 700;
  color: #0f172a;
}
.industry-head-text p {
  margin: 2px 0 0;
  font-size: 13px;
  color: #64748b;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.industry-link {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: var(--color-primary);
  text-decoration: none;
  font-weight: 500;
}
.industry-link:hover {
  text-decoration: underline;
}

/* 卡片网格 */
.column-list {
  max-width: 1024px;
  margin: 0 auto;
  padding: 0;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 20px;
}
.column-list--solo {
  padding: 28px 16px 56px;
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
  background: linear-gradient(135deg, var(--color-primary), #4d9bd8);
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
  color: var(--color-primary);
  font-weight: 500;
}
.column-empty {
  max-width: 1024px;
  margin: 0 auto;
  padding: 28px 16px 56px;
}
.column-empty-card {
  background: #fff;
  border: 1px dashed #cbd5e1;
  border-radius: 12px;
  padding: 48px 24px;
  text-align: center;
  color: #94a3b8;
}
.column-empty-card h2 {
  margin: 14px 0 6px;
  font-size: 17px;
  color: #475569;
}
.column-empty-card p {
  margin: 0;
  font-size: 14px;
}
@media (max-width: 768px) {
  .column-list {
    grid-template-columns: 1fr;
  }
  .industry-head {
    flex-wrap: wrap;
  }
  .industry-head-text p {
    white-space: normal;
  }
}
</style>
