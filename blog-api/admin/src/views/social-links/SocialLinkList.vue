<template>
  <LayoutShell>
    <div class="topbar">
      <h2>社交链接</h2>
      <button class="btn btn-primary" @click="openNew">新建链接</button>
    </div>
    <div class="msg msg-error" v-if="error">{{ error }}</div>

    <div class="card">
      <table>
        <thead><tr><th>平台</th><th>标签</th><th>URL</th><th>启用</th><th style="width:140px">操作</th></tr></thead>
        <tbody>
          <tr v-for="s in list" :key="s.id">
            <td>{{ s.platform }}</td>
            <td>{{ s.label }}</td>
            <td style="color:#666;word-break:break-all">{{ s.url }}</td>
            <td><span class="badge" :class="s.enabled ? 'badge-on' : 'badge-off'">{{ s.enabled ? '启用' : '停用' }}</span></td>
            <td>
              <button class="btn btn-sm btn-ghost" @click="openEdit(s)">编辑</button>
              <button class="btn btn-sm btn-danger" @click="doDelete(s.id!)">删除</button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <div v-if="editing" style="position:fixed;inset:0;background:rgba(0,0,0,.4);display:flex;align-items:center;justify-content:center;z-index:100" @click.self="editing=null">
      <div class="card" style="width:460px">
        <h3 style="margin-bottom:12px">{{ form.id ? '编辑链接' : '新建链接' }}</h3>
        <div class="grid-2">
          <div class="form-row"><label>平台标识（github/wechat/…）</label><input v-model="form.platform"></div>
          <div class="form-row"><label>显示标签</label><input v-model="form.label"></div>
        </div>
        <div class="form-row"><label>URL</label><input v-model="form.url"></div>
        <div class="grid-2">
          <div class="form-row"><label>图标 class（可选）</label><input v-model="form.iconClass"></div>
          <div class="form-row"><label>排序</label><input type="number" v-model.number="form.priority"></div>
        </div>
        <div class="form-row"><label style="display:flex;align-items:center;gap:6px;font-weight:normal">
          <input type="checkbox" style="width:auto" v-model="form.enabled"> 启用
        </label></div>
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

interface SocialLink { id?: number; platform: string; label: string; url: string; iconClass?: string; priority?: number; enabled?: boolean }
const list = ref<SocialLink[]>([])
const editing = ref<any>(null)
const form = ref<Partial<SocialLink>>({})
const error = ref('')

async function reload() { list.value = await api<SocialLink[]>('GET', '/api/admin/social-links') }
function openNew() { form.value = { priority: 0, enabled: true }; editing.value = form.value }
function openEdit(s: SocialLink) { form.value = { ...s }; editing.value = s }
async function save() {
  error.value = ''
  try {
    if (form.value.id) await api('PUT', `/api/admin/social-links/${form.value.id}`, form.value)
    else await api('POST', '/api/admin/social-links', form.value)
    editing.value = null
    reload()
  } catch (e: any) { error.value = e.message }
}
async function doDelete(id: number) {
  if (!confirm('删除？')) return
  await api('DELETE', `/api/admin/social-links/${id}`)
  reload()
}
reload()
</script>
