<template>
  <div v-if="project" class="project-exam-page">
    <!-- Hero -->
    <header class="exam-hero">
      <div class="exam-hero-inner">
        <nav class="page-crumb">
          <NuxtLink to="/">首页</NuxtLink>
          <span>/</span>
          <NuxtLink to="/column">项目实战</NuxtLink>
          <span>/</span>
          <NuxtLink :to="`/column/industry/${project.industrySlug}`">{{ project.industryName }}</NuxtLink>
          <span>/</span>
          <span>{{ project.title }}</span>
        </nav>
        <div class="exam-tags">
          <span v-if="project.open" class="exam-tag exam-tag--open">连载中</span>
          <span v-else class="exam-tag exam-tag--planned">筹备中</span>
          <span class="exam-tag exam-tag--freq">{{ project.jdFreq }}</span>
          <span class="exam-tag exam-tag--plain">{{ project.industryName }}行业</span>
        </div>
        <h1>{{ project.title }}</h1>
        <p class="exam-summary">{{ project.summary }}</p>
        <p class="exam-sub">面试官在 JD 里筛的就是这些考点 —— 本清单持续用 JD 检索与面经数据校准。</p>
      </div>
    </header>

    <!-- 考点清单 -->
    <main class="exam-main">
      <ol class="exam-list">
        <li v-for="(pt, idx) in project.examPoints" :key="pt.sort" class="exam-row">
          <span class="exam-no">{{ String(idx + 1).padStart(2, "0") }}</span>
          <span class="exam-text">
            <span class="exam-point">{{ pt.point }}</span>
            <span class="exam-detail">{{ pt.detail }}</span>
          </span>
        </li>
      </ol>

      <!-- CTA -->
      <div class="join-card">
        <template v-if="project.open">
          <div class="join-text">
            <h2>该项目已开更，{{ project.examPoints.length }} 个考点在连载中逐一击破</h2>
            <p>从 0 到 1 渐进式拆解，前 2 章免费试读，加入星球解锁全部章节。</p>
          </div>
          <NuxtLink :to="`/column/${project.seriesSlug}`" class="join-btn">查看完整连载</NuxtLink>
        </template>
        <template v-else>
          <div class="join-text">
            <h2>项目正在筹备中，考点清单持续完善</h2>
            <p>上线后将在知识星球同步连载。想看哪个项目先开更？到星球里提需求，呼声高的优先。</p>
          </div>
          <a :href="joinUrl" target="_blank" rel="noopener" class="join-btn">加入知识星球</a>
        </template>
      </div>

      <div class="back-links">
        <NuxtLink :to="`/column/industry/${project.industrySlug}`">
          ← 返回{{ project.industryName }}行业
        </NuxtLink>
        <NuxtLink to="/column">浏览全部行业 →</NuxtLink>
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
import type { IndustryProjectDetail } from "~/types/halo";
import { jsonLdSafe } from "~/utils/seo";
import { zsxqConfig } from "~/data/zsxq";

const route = useRoute();
// 路由参数只读一次存局部变量，避免客户端导航后 route 对象变化
const industrySlug = route.params.industrySlug as string;
const projectSlug = route.params.projectSlug as string;
const siteUrl = ((useRuntimeConfig().public.siteUrl as string) || "").replace(/\/+$/, "");
const joinUrl = zsxqConfig.joinUrl;

const { getIndustryProject } = useHaloApi();

const { data: project } = await useAsyncData<IndustryProjectDetail>(
  `industry-project-${industrySlug}-${projectSlug}`,
  () => getIndustryProject(industrySlug, projectSlug),
);
if (!project.value) {
  throw createError({ statusCode: 404, statusMessage: "项目不存在" });
}

const pageUrl = computed(
  () => `${siteUrl}/column/industry/${industrySlug}/${projectSlug}`,
);
const seoDescription = computed(() => {
  const points = (project.value?.examPoints ?? [])
    .slice(0, 4)
    .map((p) => p.point)
    .join("、");
  return `${project.value?.summary || ""} 面试考点：${points} 等 ${project.value?.examPoints.length ?? 0} 个高频考点。`;
});

useHead(() => ({
  title: `${project.value?.title} 面试考点清单 - ${project.value?.industryName}项目实战`,
  meta: [
    { name: "description", content: seoDescription.value },
    { property: "og:title", content: `${project.value?.title} 面试考点清单` },
    { property: "og:description", content: seoDescription.value },
    { property: "og:type", content: "article" },
    { property: "og:url", content: pageUrl.value },
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
            name: `${project.value?.industryName}行业`,
            item: `${siteUrl}/column/industry/${industrySlug}`,
          },
          { "@type": "ListItem", position: 4, name: project.value?.title, item: pageUrl.value },
        ],
      }),
    },
  ],
}));
</script>

<style scoped>
.project-exam-page {
  background: #f5f6f7;
  min-height: 100vh;
  padding-bottom: 60px;
}
.exam-hero {
  background: linear-gradient(135deg, var(--color-primary) 0%, #2b8ad4 100%);
  color: #fff;
  padding: 24px 16px 36px;
}
.exam-hero-inner {
  max-width: 860px;
  margin: 0 auto;
}
.page-crumb {
  font-size: 13px;
  opacity: 0.85;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 18px;
}
.page-crumb a {
  color: #fff;
  text-decoration: none;
}
.page-crumb a:hover {
  text-decoration: underline;
}
.exam-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 14px;
}
.exam-tag {
  padding: 3px 11px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 500;
}
.exam-tag--planned {
  background: linear-gradient(135deg, #b45309, #f59e0b);
}
.exam-tag--open {
  background: linear-gradient(135deg, #6d28d9, #7c3aed);
}
.exam-tag--freq {
  background: rgba(255, 255, 255, 0.2);
}
.exam-tag--plain {
  background: rgba(255, 255, 255, 0.2);
}
.exam-hero h1 {
  margin: 0 0 10px;
  font-size: 26px;
  font-weight: 700;
}
.exam-summary {
  margin: 0 0 8px;
  font-size: 14px;
  line-height: 1.9;
  opacity: 0.92;
}
.exam-sub {
  margin: 0;
  font-size: 13px;
  opacity: 0.75;
}
.exam-main {
  max-width: 860px;
  margin: -18px auto 0;
  padding: 0 16px;
}
.exam-list {
  list-style: none;
  margin: 0;
  padding: 0;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  overflow: hidden;
}
.exam-row {
  display: flex;
  align-items: flex-start;
  gap: 14px;
  padding: 15px 18px;
  border-bottom: 1px solid #f1f5f9;
  transition: background 0.15s;
}
.exam-row:last-child {
  border-bottom: none;
}
.exam-row:hover {
  background: #f8fbfe;
}
.exam-no {
  width: 40px;
  height: 40px;
  flex-shrink: 0;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  font-weight: 700;
  background: #fff7ed;
  color: #c2410c;
}
.exam-text {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.exam-point {
  font-size: 15px;
  color: #1e293b;
  font-weight: 600;
}
.exam-detail {
  font-size: 13px;
  color: #64748b;
  line-height: 1.7;
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
.back-links {
  margin-top: 18px;
  display: flex;
  justify-content: space-between;
  font-size: 13px;
}
.back-links a {
  color: #64748b;
  text-decoration: none;
}
.back-links a:hover {
  color: var(--color-primary);
}
@media (max-width: 768px) {
  .join-card {
    flex-direction: column;
    text-align: center;
  }
}
</style>
