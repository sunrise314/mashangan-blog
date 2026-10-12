<template>
  <LayoutShell>
    <div class="tabbar">
      <button :class="['tab', tab === 'projects' ? 'tab-active' : '']" @click="switchTab('projects')">项目卡片</button>
      <button :class="['tab', tab === 'series' ? 'tab-active' : '']" @click="switchTab('series')">内容专栏</button>
    </div>
    <ProjectList v-if="tab === 'projects'" />
    <SeriesList v-else />
  </LayoutShell>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import LayoutShell from '../../components/LayoutShell.vue'
import ProjectList from '../projects/ProjectList.vue'
import SeriesList from '../series/SeriesList.vue'

const route = useRoute()
const router = useRouter()

/** tab 跟随路由：/admin/series = 内容专栏，其余（/admin/projects）= 项目卡片 */
const tab = computed(() => (route.path.startsWith('/admin/series') ? 'series' : 'projects'))

function switchTab(t: 'projects' | 'series') {
  router.push(t === 'series' ? '/admin/series' : '/admin/projects')
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
