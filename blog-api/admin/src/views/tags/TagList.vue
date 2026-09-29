<template>
  <LayoutShell>
    <div class="topbar">
      <h2>标签管理</h2>
      <button class="btn btn-primary" @click="openNew">新建标签</button>
    </div>
    <div class="msg msg-error" v-if="error">{{ error }}</div>

    <div class="card">
      <table>
        <thead><tr><th>名称</th><th>slug</th><th style="width:140px">操作</th></tr></thead>
        <tbody>
          <tr v-for="t in list" :key="t.id">
            <td>{{ t.displayName }}</td>
            <td style="color:#666">{{ t.slug }}</td>
            <td>
              <button class="btn btn-sm btn-ghost" @click="openEdit(t)">编辑</button>
              <button class="btn btn-sm btn-danger" @click="doDelete(t.id!)">删除</button>
            </td>
          </tr>
          <tr v-if="!list.length"><td colspan="3" style="text-align:center;color:#999;padding:20px">暂无标签</td></tr>
        </tbody>
      </table>
    </div>

    <div v-if="editing" style="position:fixed;inset:0;background:rgba(0,0,0,.4);display:flex;align-items:center;justify-content:center;z-index:100" @click.self="editing=null">
      <div class="card" style="width:420px">
        <h3 style="margin-bottom:12px">{{ form.id ? '编辑标签' : '新建标签' }}</h3>
        <div class="form-row"><label>显示名称</label><input v-model="form.displayName"></div>
        <div class="form-row"><label>slug</label><input v-model="form.slug"></div>
        <div style="display:flex;gap:8px;justify-content:flex-end">
          <button class="btn btn-ghost" @click="editing=null">取消</button>
          <button class="btn btn-primary" @click="save">保存</button>
        </div>
      </div>
    </div>
  </LayoutShell>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import LayoutShell from '../../components/LayoutShell.vue'
import { api } from '../../api/client'

interface Tag { id?: number; displayName: string; slug: string; haloName?: string }
const list = ref<Tag[]>([])
const editing = ref<any>(null)
const form = ref<Partial<Tag>>({})
const error = ref('')

async function reload() { list.value = await api<Tag[]>('GET', '/api/admin/tags') }
function openNew() { form.value = { displayName: '', slug: '' }; editing.value = form.value }
function openEdit(t: Tag) { form.value = { ...t }; editing.value = t }
async function save() {
  error.value = ''
  try {
    if (form.value.id) await api('PUT', `/api/admin/tags/${form.value.id}`, form.value)
    else await api('POST', '/api/admin/tags', form.value)
    editing.value = null
    reload()
  } catch (e: any) { error.value = e.message }
}
async function doDelete(id: number) {
  if (!confirm('删除标签？')) return
  await api('DELETE', `/api/admin/tags/${id}`)
  reload()
}
reload()
</script>
