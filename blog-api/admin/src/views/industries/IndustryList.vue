<template>
  <div>
    <div class="topbar">
      <h2>行业</h2>
      <button class="btn btn-primary" @click="openNew">新建行业</button>
    </div>

    <div class="msg msg-error" v-if="error">{{ error }}</div>
    <div class="msg msg-ok" v-if="saved">{{ saved }}</div>

    <div class="card">
      <table>
        <thead>
          <tr>
            <th>名称</th>
            <th>slug</th>
            <th>简介</th>
            <th style="width:80px">卡片数</th>
            <th style="width:60px">排序</th>
            <th style="width:90px">展示</th>
            <th style="width:120px">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="i in list" :key="i.id">
            <td>
              {{ i.name }}
              <a v-if="i.slug" :href="`/column/industry/${i.slug}`" target="_blank"
                 style="color:#3b82f6;font-size:12px;margin-left:6px">前台查看</a>
            </td>
            <td style="color:#666">{{ i.slug }}</td>
            <td style="color:#666;max-width:360px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap">{{ i.intro }}</td>
            <td style="color:#666">{{ i.projectCount }}</td>
            <td style="color:#666">{{ i.sort ?? 0 }}</td>
            <td>
              <button class="badge" :style="{
                background: (i.visible ?? true) ? '#dcfce7' : '#f1f5f9',
                color: (i.visible ?? true) ? '#15803d' : '#64748b',
                border: '1px solid ' + ((i.visible ?? true) ? '#86efac' : '#e2e8f0'),
                cursor: 'pointer'
              }" @click="toggleVisible(i)" :title="(i.visible ?? true) ? '点击隐藏，前台不再展示' : '点击展示'">
                {{ (i.visible ?? true) ? '展示中' : '已隐藏' }}
              </button>
            </td>
            <td style="white-space:nowrap">
              <button class="btn btn-sm btn-ghost" @click="openEdit(i)">编辑</button>
              <button class="btn btn-sm btn-ghost" style="color:#dc2626" @click="remove(i)">删除</button>
            </td>
          </tr>
          <tr v-if="!list.length && !error">
            <td colspan="7" style="color:#999;padding:16px 0">暂无行业。</td>
          </tr>
        </tbody>
      </table>
    </div>

    <div v-if="editing" style="position:fixed;inset:0;background:rgba(0,0,0,.4);display:flex;align-items:center;justify-content:center;z-index:100" @click.self="editing=null">
      <div class="card" style="width:440px">
        <h3 style="margin-bottom:12px">{{ editing.id ? '编辑行业' : '新建行业' }}</h3>
        <div class="form-row"><label>名称</label><input v-model="form.name" placeholder="如：人工智能"></div>
        <div class="form-row"><label>slug（前台 /column/industry/{slug}）</label><input v-model="form.slug"></div>
        <div class="form-row"><label>简介</label><textarea v-model="form.intro" rows="2"></textarea></div>
        <div class="form-row"><label>排序（升序）</label><input type="number" v-model.number="form.sort"></div>
        <label style="display:flex;gap:6px;align-items:center;font-size:13px;color:#555">
          <input type="checkbox" v-model="form.visible"> 前台展示（取消则不在 /column 行业列表与详情页出现）
        </label>
        <div style="display:flex;gap:8px;justify-content:flex-end">
          <button class="btn btn-ghost" @click="editing=null">取消</button>
          <button class="btn btn-primary" @click="save">保存</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { industriesApi, type Industry } from '../../api/projects'

const list = ref<Industry[]>([])
const editing = ref<Industry | null>(null)
const form = ref<Partial<Industry>>({})
const error = ref('')
const saved = ref('')

async function reload() {
  error.value = ''
  try { list.value = await industriesApi.list() }
  catch (e: any) { error.value = e.message }
}

function openNew() {
  form.value = { name: '', slug: '', intro: '', sort: 0, visible: true }
  editing.value = form.value as Industry
}

function openEdit(i: Industry) {
  form.value = { ...i, visible: i.visible ?? true }
  editing.value = i
}

async function toggleVisible(i: Industry) {
  error.value = ''; saved.value = ''
  if (!i.id) return
  const next = !(i.visible ?? true)
  try {
    await industriesApi.update(i.id, { ...i, visible: next })
    i.visible = next
    saved.value = next ? '已展示' : '已隐藏'
  } catch (e: any) { error.value = e.message }
}

async function save() {
  error.value = ''; saved.value = ''
  try {
    if (form.value.id) await industriesApi.update(form.value.id, form.value)
    else await industriesApi.create(form.value)
    saved.value = '已保存'
    editing.value = null
    reload()
  } catch (e: any) { error.value = e.message }
}

async function remove(i: Industry) {
  error.value = ''; saved.value = ''
  if (!i.id) return
  if (!confirm(`确定删除行业「${i.name}」？`)) return
  try {
    await industriesApi.remove(i.id)
    saved.value = '已删除'
    reload()
  } catch (e: any) { error.value = e.message }  // 409：行业下有卡片，后端消息已说明
}

reload()
</script>
