<template>
  <div class="card top-list">
    <h3 :class="small ? 'small-title' : ''">{{ title }}</h3>
    <ul v-if="rows.length" class="top-ul">
      <li v-for="r in rows" :key="r.k" class="top-li">
        <div class="top-row">
          <span class="top-k" :title="r.k">{{ r.k }}</span>
          <span class="top-c">{{ r.c }}</span>
        </div>
        <div class="top-bar-bg">
          <div class="top-bar-fill" :style="{ width: (r.c / max * 100) + '%' }"></div>
        </div>
      </li>
    </ul>
    <p v-else class="empty">{{ empty || '暂无数据' }}</p>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

interface TopRow { k: string; c: number }
const props = defineProps<{
  title: string
  rows: TopRow[]
  empty?: string
  small?: boolean
}>()

const max = computed(() => Math.max(1, ...props.rows.map(r => r.c)))
</script>

<style scoped>
.top-list { padding: 14px; }
.small-title { font-size: 12px; }
.top-ul { list-style: none; padding: 0; margin: 0; }
.top-li { margin-bottom: 6px; }
.top-row { display: flex; justify-content: space-between; align-items: center; gap: 8px; }
.top-k { white-space: nowrap; overflow: hidden; text-overflow: ellipsis; color: #444; font-size: 13px; }
.top-c { color: #aaa; font-size: 11px; }
.top-bar-bg { height: 3px; background: #f1f5f9; border-radius: 2px; margin-top: 2px; }
.top-bar-fill { height: 3px; background: #64748b; border-radius: 2px; }
.empty { color: #aaa; font-size: 13px; }
</style>
