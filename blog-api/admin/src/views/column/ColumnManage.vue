<template>
  <LayoutShell>
    <div class="tabbar">
      <button :class="['tab', tab === 'projects' ? 'tab-active' : '']" @click="switchTab('projects')">项目卡片</button>
      <button :class="['tab', tab === 'series' ? 'tab-active' : '']" @click="switchTab('series')">内容专栏</button>
      <button :class="['tab', tab === 'industries' ? 'tab-active' : '']" @click="switchTab('industries')">行业</button>
    </div>
    <ProjectList v-if="tab === 'projects'" />
    <SeriesList v-else-if="tab === 'series'" />
    <IndustryList v-else />
  </LayoutShell>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import LayoutShell from '../../components/LayoutShell.vue'
import ProjectList from '../projects/ProjectList.vue'
import SeriesList from '../series/SeriesList.vue'
import IndustryList from '../industries/IndustryList.vue'

const route = useRoute()
const router = useRouter()

/** tab 跟随路由：/admin/series = 内容专栏，/admin/industries = 行业，其余（/admin/projects）= 项目卡片 */
const tab = computed<'projects' | 'series' | 'industries'>(() => {
  if (route.path.startsWith('/admin/series')) return 'series'
  if (route.path.startsWith('/admin/industries')) return 'industries'
  return 'projects'
})

function switchTab(t: 'projects' | 'series' | 'industries') {
  if (t === 'series') router.push('/admin/series')
  else if (t === 'industries') router.push('/admin/industries')
  else router.push('/admin/projects')
}
</script>

<style scoped>
.tabbar {
  display: flex;
  gap: 4px;
  margin-bottom: 12px;
  border-bottom: 1px solid #e2e8f0;
}
.tab {
  padding: 8px 16px;
  border: none;
  background: none;
  font-size: 14px;
  color: #64748b;
  cursor: pointer;
  border-bottom: 2px solid transparent;
}
.tab:hover {
  color: #1e293b;
}
.tab-active {
  color: #1d4ed8;
  font-weight: 600;
  border-bottom-color: #1d4ed8;
}
</style>
