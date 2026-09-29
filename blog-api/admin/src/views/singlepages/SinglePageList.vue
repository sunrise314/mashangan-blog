<template>
  <LayoutShell>
    <div class="topbar">
      <h2>独立页面</h2>
      <button class="btn btn-primary" @click="$router.push('/admin/singlepages/new')">新建页面</button>
    </div>
    <div class="msg msg-error" v-if="error">{{ error }}</div>

    <div class="card">
      <table>
        <thead><tr><th>标题</th><th>slug</th><th>状态</th><th style="width:180px">操作</th></tr></thead>
        <tbody>
          <tr v-for="p in list" :key="p.id">
            <td>{{ p.title }}</td>
            <td style="color:#666">{{ p.slug }}</td>
            <td><span class="badge" :class="p.published ? 'badge-on' : 'badge-off'">{{ p.published ? '已发布' : '草稿' }}</span></td>
            <td>
              <button class="btn btn-sm btn-ghost" @click="$router.push(`/admin/singlepages/${p.id}`)">编辑</button>
              <button class="btn btn-sm btn-danger" @click="doDelete(p.id!)">删除</button>
            </td>
          </tr>
          <tr v-if="!list.length"><td colspan="4" style="text-align:center;color:#999;padding:20px">暂无页面</td></tr>
        </tbody>
      </table>
    </div>
  </LayoutShell>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import LayoutShell from '../../components/LayoutShell.vue'
import { api } from '../../api/client'

interface SP { id?: number; title: string; slug: string; published?: boolean }
const list = ref<SP[]>([])
const error = ref('')
async function reload() { list.value = await api<SP[]>('GET', '/api/admin/singlepages') }
async function doDelete(id: number) {
  if (!confirm('删除该页面？')) return
  await api('DELETE', `/api/admin/singlepages/${id}`)
  reload()
}
reload()
</script>
