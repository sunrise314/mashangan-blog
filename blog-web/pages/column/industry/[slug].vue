<template>
  <div v-if="industry" class="industry-page">
    <!-- Hero -->
    <header class="industry-hero">
      <div class="industry-hero-inner">
        <nav class="page-crumb">
          <NuxtLink to="/">首页</NuxtLink>
          <span>/</span>
          <NuxtLink to="/column">项目实战</NuxtLink>
          <span>/</span>
          <span>{{ industry.name }}</span>
        </nav>
        <div class="industry-hero-head">
          <span class="industry-hero-icon"><IndustryIcon :slug="industry.slug" :size="34" /></span>
          <div>
            <h1>{{ industry.name }}行业项目实战</h1>
            <p>{{ industry.intro }}</p>
            <div class="industry-stats">
              <span>{{ industry.projects.length }} 个高频项目</span>
              <span class="stat-dot">·</span>
              <span>{{ openCount }} 个已开更连载</span>
              <span class="stat-dot">·</span>
              <span>筹备中项目附面试考点清单</span>
            </div>
          </div>
        </div>
      </div>
    </header>

    <!-- 项目卡片 -->
    <main class="industry-main">
      <div class="column-list">
        <ProjectMapCard
          v-for="p in industry.projects"
          :key="p.slug"
          :project="p"
          :industry-slug="industry.slug"
          :cover="p.open ? seriesMap.get(p.seriesSlug || '')?.cover || '' : ''"
          :chapter-count="p.open ? seriesMap.get(p.seriesSlug || '')?.chapterCount || 0 : 0"
          :series-status="p.open ? seriesMap.get(p.seriesSlug || '')?.status || 'updating' : 'updating'"
        />
      </div>

      <!-- 其他行业 -->
      <section class="other-industries">
        <h2>浏览其他行业</h2>
        <div class="other-chips">
          <NuxtLink
            v-for="ind in otherIndustries"
            :key="ind.slug"
            :to="`/column/industry/${ind.slug}`"
            class="other-chip"
          >
            <IndustryIcon :slug="ind.slug" :size="15" />
            {{ ind.name }}
          </NuxtLink>
        </div>
      </section>
    </main>
  </div>
</template>

<script setup lang="ts">
import type { IndustryDetail, SeriesCard } from "~/types/halo";
import { jsonLdSafe } from "~/utils/seo";

const route = useRoute();
// slug 经路由参数进来，只能读一次存局部变量（避免客户端导航后路由对象变化）
const industrySlug = route.params.slug as string;
const siteUrl = ((useRuntimeConfig().public.siteUrl as string) || "").replace(/\/+$/, "");

const { getIndustryBySlug, getIndustries, getSeries } = useHaloApi();

const { data: industry } = await useAsyncData<IndustryDetail>(`industry-${industrySlug}`, () =>
  getIndustryBySlug(industrySlug),
);
if (!industry.value) {
  throw createError({ statusCode: 404, statusMessage: "行业不存在" });
}

// 顶部菜单在别的页面统一拉，这里只补：其他行业 chips + 系列封面合并
const { data: allIndustries } = await useAsyncData<IndustryDetail[]>("industry-map", () =>
  getIndustries().catch(() => [] as IndustryDetail[]),
);
const { data: seriesList } = await useAsyncData<SeriesCard[]>("series-cards", () =>
  getSeries().catch(() => [] as SeriesCard[]),
);

const seriesMap = computed(
  () =>
    new Map(
      (seriesList.value ?? [])
        .filter((s) => !s.hidden)
        .map((s) => [s.slug, s] as const),
    ),
);
const openCount = computed(
  () => industry.value?.projects.filter((p) => p.open).length ?? 0,
);
const otherIndustries = computed(() =>
  (allIndustries.value ?? []).filter((i) => i.slug !== industrySlug),
);

useHead(() => ({
  title: `${industry.value?.name}行业项目实战地图`,
  meta: [
    {
      name: "description",
      content:
        `${industry.value?.intro} 共 ${industry.value?.projects.length} 个高频项目，` +
        `${openCount.value} 个已开更连载，其余筹备中项目附面试考点清单。`,
    },
  ],
  script: [
    {
      type: "application/ld+json",
      innerHTML: jsonLdSafe({
        "@context": "https://schema.org",
        "@type": "BreadcrumbList",
        itemListElement: [
          { "@type": "ListItem", position: 1, name: "首页", item: `${siteUrl}/` },
          { "@type": "ListItem", position: 2, name: "项目实战", item: `${siteUrl}/column` },
          {
            "@type": "ListItem",
            position: 3,
            name: `${industry.value?.name}项目实战地图`,
            item: `${siteUrl}/column/industry/${industrySlug}`,
          },
        ],
      }),
    },
  ],
}));
</script>

<style scoped>
.industry-page {
  background: #f5f6f7;
  min-height: 100vh;
}
.industry-hero {
  background: linear-gradient(135deg, var(--color-primary) 0%, #2b8ad4 100%);
  color: #fff;
  padding: 24px 16px 36px;
}
.industry-hero-inner {
  max-width: 1024px;
  margin: 0 auto;
}
.page-crumb {
  font-size: 13px;
  opacity: 0.85;
  display: flex;
  gap: 8px;
  margin-bottom: 20px;
}
.page-crumb a {
  color: #fff;
  text-decoration: none;
}
.page-crumb a:hover {
  text-decoration: underline;
}
.industry-hero-head {
  display: flex;
  gap: 18px;
  align-items: flex-start;
}
.industry-hero-icon {
  width: 64px;
  height: 64px;
  flex-shrink: 0;
  border-radius: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(255, 255, 255, 0.16);
  color: #fff;
}
.industry-hero-head h1 {
  margin: 0 0 8px;
  font-size: 26px;
  font-weight: 700;
}
.industry-hero-head p {
  margin: 0 0 10px;
  font-size: 14px;
  line-height: 1.8;
  opacity: 0.92;
}
.industry-stats {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  opacity: 0.85;
}
.stat-dot {
  opacity: 0.6;
}
.industry-main {
  max-width: 1024px;
  margin: -14px auto 0;
  padding: 0 16px 56px;
}
.column-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 20px;
}
.other-industries {
  margin-top: 34px;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  padding: 20px 22px;
}
.other-industries h2 {
  margin: 0 0 12px;
  font-size: 15px;
  font-weight: 700;
  color: #0f172a;
}
.other-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}
.other-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 13px;
  border-radius: 999px;
  border: 1px solid #e2e8f0;
  background: #f8fafc;
  color: #475569;
  font-size: 13px;
  text-decoration: none;
  transition: all 0.15s;
}
.other-chip:hover {
  border-color: #7cc0ec;
  color: var(--color-primary);
}
@media (max-width: 768px) {
  .column-list {
    grid-template-columns: 1fr;
  }
  .industry-hero-head {
    flex-direction: column;
  }
}
</style>
