<template>
  <NuxtLink :to="link" class="project-card">
    <div class="project-cover">
      <img
        v-if="cover"
        :src="cover"
        :alt="project.title"
        referrerpolicy="no-referrer"
        loading="lazy"
      />
      <span v-else class="project-cover-placeholder">
        <IndustryIcon :slug="industrySlug" :size="44" />
      </span>
      <span
        v-if="project.open"
        class="project-status"
        :class="seriesStatus === 'complete' ? 'project-status--complete' : 'project-status--updating'"
      >
        {{ seriesStatus === "complete" ? "已完结" : "连载中" }}
      </span>
      <span v-else class="project-status project-status--planned">筹备中</span>
    </div>
    <div class="project-body">
      <h2 class="project-title">{{ project.title }}</h2>
      <p class="project-excerpt">{{ project.summary || "暂无简介" }}</p>
      <div class="project-meta">
        <template v-if="project.open">
          <span>{{ chapterCount }} 章</span>
          <span class="project-more">
            查看项目
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M5 12h14M13 6l6 6-6 6" stroke-linecap="round" stroke-linejoin="round" />
            </svg>
          </span>
        </template>
        <template v-else>
          <span class="meta-tags">
            <span class="jd-tag">{{ project.jdFreq }}</span>
            <span>{{ project.examPointCount }} 个考点</span>
          </span>
          <span class="project-more">
            查看考点
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M5 12h14M13 6l6 6-6 6" stroke-linecap="round" stroke-linejoin="round" />
            </svg>
          </span>
        </template>
      </div>
    </div>
  </NuxtLink>
</template>

<script setup lang="ts">
import type { IndustryProjectCard } from "~/types/halo";

const props = withDefaults(
  defineProps<{
    project: IndustryProjectCard;
    industrySlug: string;
    /** 已开更项目由 /column 页传入真实系列封面（来自 /series 卡片数据） */
    cover?: string;
    chapterCount?: number;
    seriesStatus?: string;
  }>(),
  { cover: "", chapterCount: 0, seriesStatus: "updating" },
);

// 已开更 → 真实系列页；筹备中 → 考点清单落地页
const link = computed(() =>
  props.project.open && props.project.seriesSlug
    ? `/column/${props.project.seriesSlug}`
    : `/column/industry/${props.industrySlug}/${props.project.slug}`,
);
</script>

<style scoped>
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
  color: rgba(255, 255, 255, 0.72);
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
.project-status--planned {
  background: linear-gradient(135deg, #b45309, #f59e0b);
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
.meta-tags {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}
.jd-tag {
  padding: 1px 8px;
  border-radius: 999px;
  font-size: 12px;
  background: #fff7ed;
  color: #c2410c;
}
.project-more {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: var(--color-primary);
  font-weight: 500;
}
</style>
