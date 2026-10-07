<template>
  <LayoutShell>
    <div class="topbar">
      <h2>项目专栏</h2>
      <button class="btn btn-primary" @click="openNew">新建专栏</button>
    </div>

    <div class="msg msg-error" v-if="error">{{ error }}</div>
    <div class="msg msg-ok" v-if="saved">{{ saved }}</div>

    <div class="card">
      <table>
        <thead>
          <tr>
            <th>标题</th>
            <th>slug</th>
            <th style="width:100px">连载状态</th>
            <th style="width:90px">免费章节</th>
            <th style="width:70px">排序</th>
            <th style="width:150px">更新时间</th>
            <th style="width:80px">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="s in list" :key="s.id">
            <td>
              {{ s.title }}
              <a v-if="s.slug" :href="`/column/${s.slug}`" target="_blank" style="color:#3b82f6;font-size:12px;margin-left:6px">前台查看</a>
            </td>
            <td style="color:#666">{{ s.slug }}</td>
            <td>
              <span class="badge" :class="s.status === 'complete' ? 'badge-on' : 'badge-off'"
                    :style="s.status === 'complete' ? 'background:#15803d' : ''">
                {{ s.status === 'complete' ? '已完结' : '连载中' }}
              </span>
            </td>
            <td style="color:#666">{{ s.freeChapterCount ?? 0 }}</td>
            <td style="color:#666">{{ s.sortOrder ?? 0 }}</td>
            <td style="color:#666">{{ fmtTime(s.updatedAt) }}</td>
            <td><button class="btn btn-sm btn-ghost" @click="openEdit(s)">编辑</button></td>
          </tr>
          <tr v-if="!list.length && !error">
            <td colspan="7" style="color:#999;padding:16px 0">暂无专栏，点击右上角「新建专栏」创建。</td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- 编辑弹窗 -->
    <div v-if="editing" style="position:fixed;inset:0;background:rgba(0,0,0,.4);display:flex;align-items:center;justify-content:center;z-index:100" @click.self="editing=null">
      <div class="card" style="width:480px;max-height:90vh;overflow:auto">
        <h3 style="margin-bottom:12px">{{ editing.id ? '编辑专栏' : '新建专栏' }}</h3>
        <div class="form-row"><label>标题</label><input v-model="form.title" placeholder="如：StarLab：LEO 卫星互联网仿真实战"></div>
        <div class="form-row"><label>slug（URL 用，/column/{slug}）</label><input v-model="form.slug"></div>
        <div class="form-row"><label>封面图 URL</label><input v-model="form.cover"></div>
        <div class="form-row"><label>简介</label><textarea v-model="form.description" rows="3"></textarea></div>
        <div class="grid-2">
          <div class="form-row">
            <label>连载状态</label>
            <select v-model="form.status">
              <option value="updating">连载中</option>
              <option value="complete">已完结</option>
            </select>
          </div>
          <div class="form-row"><label>免费章节数</label><input type="number" v-model.number="form.freeChapterCount"></div>
        </div>
        <div class="grid-2">
          <div class="form-row"><label>排序（升序）</label><input type="number" v-model.number="form.sortOrder"></div>
        </div>
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
import { seriesApi, type Series } from '../../api/series'

const list = ref<Series[]>([])
const editing = ref<Series | null>(null)
const form = ref<Partial<Series>>({})
const error = ref('')
const saved = ref('')

async function reload() {
  error.value = ''
  try { list.value = await seriesApi.list() }
  catch (e: any) { error.value = e.message }
}

function openNew() {
  form.value = { title: '', slug: '', cover: '', description: '', status: 'updating', freeChapterCount: 2, sortOrder: 0 }
  editing.value = form.value as Series
}

function openEdit(s: Series) {
  form.value = { ...s }
  editing.value = s
}

async function save() {
  error.value = ''; saved.value = ''
  try {
    if (form.value.id) await seriesApi.update(form.value.id, form.value)
    else await seriesApi.create(form.value)
    saved.value = '已保存'
    editing.value = null
    reload()
  } catch (e: any) { error.value = e.message }
}

function fmtTime(s?: string) {
  if (!s) return '—'
  return s.replace('T', ' ').slice(0, 16)
}

reload()
</script>
