<template>
  <div>
    <div class="topbar">
      <h2>项目卡片</h2>
      <div style="display:flex;gap:8px;align-items:center">
        <select v-model="filterIndustry" style="padding:6px 8px;border:1px solid #ddd;border-radius:6px">
          <option :value="0">全部行业（{{ list.length }}）</option>
          <option v-for="i in industries" :key="i.id" :value="i.id!">{{ i.name }}（{{ i.projectCount }}）</option>
        </select>
        <select v-model="filterStatus" style="padding:6px 8px;border:1px solid #ddd;border-radius:6px">
          <option value="all">全部状态</option>
          <option value="live">已连载</option>
          <option value="placeholder">占位</option>
        </select>
        <select v-model="filterVisible" style="padding:6px 8px;border:1px solid #ddd;border-radius:6px">
          <option value="all">全部展示状态</option>
          <option value="visible">展示中</option>
          <option value="hidden">已隐藏</option>
        </select>
        <button class="btn btn-primary" @click="openNew">新建项目卡</button>
      </div>
    </div>

    <div class="msg msg-error" v-if="error">{{ error }}</div>
    <div class="msg msg-ok" v-if="saved">{{ saved }}</div>

    <div class="card">
      <table>
        <thead>
          <tr>
            <th>标题</th>
            <th>slug</th>
            <th style="width:90px">行业</th>
            <th style="width:150px">状态</th>
            <th style="width:70px">频率</th>
            <th style="width:60px">考点</th>
            <th style="width:60px">排序</th>
            <th style="width:90px">展示</th>
            <th style="width:120px">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="p in filtered" :key="p.id">
            <td>
              {{ p.title }}
              <a v-if="industrySlug(p.industryId) && p.slug"
                 :href="`/column/industry/${industrySlug(p.industryId)}/${p.slug}`" target="_blank"
                 style="color:#3b82f6;font-size:12px;margin-left:6px">前台查看</a>
            </td>
            <td style="color:#666">{{ p.slug }}</td>
            <td style="color:#666">{{ p.industryName }}</td>
            <td>
              <span v-if="p.seriesId" class="badge badge-on" style="background:#15803d">已连载</span>
              <span v-else class="badge badge-off">占位</span>
              <div v-if="p.seriesTitle" style="color:#999;font-size:12px">{{ p.seriesTitle }}</div>
            </td>
            <td style="color:#666">{{ p.jdFreq }}</td>
            <td style="color:#666">{{ p.examPoints?.length ?? 0 }}</td>
            <td style="color:#666">{{ p.sort ?? 0 }}</td>
            <td>
              <button class="badge" :style="{
                background: (p.visible ?? true) ? '#dcfce7' : '#f1f5f9',
                color: (p.visible ?? true) ? '#15803d' : '#64748b',
                border: '1px solid ' + ((p.visible ?? true) ? '#86efac' : '#e2e8f0'),
                cursor: 'pointer'
              }" @click="toggleVisible(p)" :title="(p.visible ?? true) ? '点击隐藏，前台不再展示' : '点击展示'">
                {{ (p.visible ?? true) ? '展示中' : '已隐藏' }}
              </button>
            </td>
            <td style="white-space:nowrap">
              <button class="btn btn-sm btn-ghost" @click="openEdit(p)">编辑</button>
              <button class="btn btn-sm btn-ghost" style="color:#dc2626" @click="remove(p)">删除</button>
            </td>
          </tr>
          <tr v-if="!filtered.length && !error">
            <td colspan="9" style="color:#999;padding:16px 0">没有符合筛选条件的项目卡。</td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- 编辑弹窗：基本信息 + 考点编辑 -->
    <div v-if="editing" style="position:fixed;inset:0;background:rgba(0,0,0,.4);display:flex;align-items:center;justify-content:center;z-index:100" @click.self="editing=null">
      <div class="card" style="width:640px;max-height:90vh;overflow:auto">
        <h3 style="margin-bottom:12px">{{ form.id ? '编辑项目卡' : '新建项目卡' }}</h3>
        <div class="grid-2">
          <div class="form-row">
            <label>所属行业</label>
            <select v-model="form.industryId">
              <option v-for="i in industries" :key="i.id" :value="i.id">{{ i.name }}</option>
            </select>
          </div>
          <div class="form-row">
            <label>关联专栏（留空 = 占位）</label>
            <select v-model="form.seriesId">
              <option :value="null">— 占位（未开更）—</option>
              <option v-for="s in series" :key="s.id" :value="s.id">{{ s.title }}</option>
            </select>
          </div>
        </div>
        <!-- 连载设置：关联了专栏才显示，直接改系列属性，保存时同步提交 -->
        <div v-if="form.seriesId && seriesForm" class="form-row"
             style="border:1px solid #e5e7eb;border-radius:8px;padding:10px 12px;background:#f9fafb">
          <label style="font-weight:600">连载设置（{{ seriesForm.title }}）</label>
          <div class="grid-2">
            <div class="form-row">
              <label>连载状态</label>
              <select v-model="seriesForm.status">
                <option value="updating">连载中</option>
                <option value="complete">已完结</option>
              </select>
            </div>
            <div class="form-row"><label>免费章节数</label><input type="number" v-model.number="seriesForm.freeChapterCount"></div>
          </div>
          <div class="form-row"><label>专栏封面 URL</label><input v-model="seriesForm.cover"></div>
          <label style="display:flex;gap:6px;align-items:center;font-size:13px;color:#555">
            <input type="checkbox" v-model="seriesForm.hidden"> 下架专栏（前台 /column 卡片不展示）
          </label>
        </div>

        <div class="form-row"><label>标题</label><input v-model="form.title" placeholder="如：企业级 Agent 平台实战"></div>
        <div class="grid-2">
          <div class="form-row"><label>slug（前台 URL 用）</label><input v-model="form.slug"></div>
          <div class="form-row">
            <label>面试频率</label>
            <select v-model="form.jdFreq">
              <option>高频</option>
              <option>中频</option>
              <option>低频</option>
            </select>
          </div>
        </div>
        <div class="grid-2">
          <div class="form-row"><label>简介</label><textarea v-model="form.summary" rows="2"></textarea></div>
          <div class="form-row" style="max-width:120px"><label>排序</label><input type="number" v-model.number="form.sort"></div>
        </div>
        <label style="display:flex;gap:6px;align-items:center;font-size:13px;color:#555">
          <input type="checkbox" v-model="form.visible"> 前台展示（取消则不在行业详情页与项目落地页出现）
        </label>

        <div class="form-row">
          <label>面试考点（{{ form.examPoints?.length ?? 0 }} 条）</label>
        </div>
        <div v-for="(e, idx) in form.examPoints" :key="idx"
             style="display:flex;gap:6px;margin-bottom:6px;align-items:flex-start">
          <input type="number" v-model.number="e.sort" style="width:64px" title="排序">
          <input v-model="e.point" placeholder="考点，如：RAG 混合检索" style="flex:0 0 200px">
          <input v-model="e.detail" placeholder="一句话细节" style="flex:1">
          <button class="btn btn-sm btn-ghost" style="color:#dc2626" @click="form.examPoints!.splice(idx, 1)">删</button>
        </div>
        <button class="btn btn-sm btn-ghost" @click="addPoint">+ 加一条考点</button>

        <div style="display:flex;gap:8px;justify-content:flex-end;margin-top:14px">
          <button class="btn btn-ghost" @click="editing=null">取消</button>
          <button class="btn btn-primary" @click="save">保存</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { industriesApi, projectsApi, type Industry, type ProjectCard } from '../../api/projects'
import { seriesApi, type Series } from '../../api/series'

const list = ref<ProjectCard[]>([])
const industries = ref<Industry[]>([])
const series = ref<Series[]>([])
const seriesForm = ref<Partial<Series> | null>(null)
const filterIndustry = ref(0)
const filterStatus = ref('all')
const filterVisible = ref('all')
const editing = ref<ProjectCard | null>(null)
const form = ref<Partial<ProjectCard>>({})
const error = ref('')
const saved = ref('')

const filtered = computed(() => list.value.filter(p =>
  (filterIndustry.value === 0 || p.industryId === filterIndustry.value)
  && (filterStatus.value === 'all'
      || (filterStatus.value === 'live' && !!p.seriesId)
      || (filterStatus.value === 'placeholder' && !p.seriesId))
  && (filterVisible.value === 'all'
      || (filterVisible.value === 'visible' && (p.visible ?? true))
      || (filterVisible.value === 'hidden' && p.visible === false))))

function industrySlug(industryId?: number) {
  return industries.value.find(i => i.id === industryId)?.slug
}

async function reload() {
  error.value = ''
  try {
    const [projects, inds, sers] = await Promise.all([
      projectsApi.list(), industriesApi.list(), seriesApi.list()])
    list.value = projects
    industries.value = inds
    series.value = sers
  } catch (e: any) { error.value = e.message }
}

// 弹窗里切换关联专栏时，连载设置同步为该系列当前值（全量副本，保存时整体提交）
watch(() => form.value.seriesId, (sid) => {
  const s = series.value.find(x => x.id === sid)
  seriesForm.value = s ? { ...s } : null
})

function openNew() {
  form.value = {
    industryId: industries.value[0]?.id, seriesId: null,
    title: '', slug: '', jdFreq: '高频', summary: '', sort: 0, visible: true, examPoints: [],
  }
  editing.value = form.value as ProjectCard
}

function openEdit(p: ProjectCard) {
  form.value = { ...p, visible: p.visible ?? true, examPoints: (p.examPoints ?? []).map(e => ({ ...e })) }
  editing.value = p
}

async function toggleVisible(p: ProjectCard) {
  error.value = ''; saved.value = ''
  if (!p.id) return
  const next = !(p.visible ?? true)
  try {
    await projectsApi.update(p.id, { ...p, visible: next })
    p.visible = next
    saved.value = next ? '已展示' : '已隐藏'
  } catch (e: any) { error.value = e.message }
}

function addPoint() {
  if (!form.value.examPoints) form.value.examPoints = []
  form.value.examPoints.push({ sort: form.value.examPoints.length + 1, point: '', detail: '' })
}

async function save() {
  error.value = ''; saved.value = ''
  const points = (form.value.examPoints ?? []).filter(e => e.point.trim())
  const payload = { ...form.value, examPoints: points.map((e, i) => ({ ...e, sort: e.sort ?? i + 1 })) }
  try {
    if (form.value.id) await projectsApi.update(form.value.id, payload)
    else await projectsApi.create(payload)
    // 连载设置有变则同步提交系列（全量字段，后端 null 会抹默认值）
    if (form.value.seriesId && seriesForm.value?.id === form.value.seriesId) {
      await seriesApi.update(form.value.seriesId, { ...seriesForm.value })
    }
    saved.value = '已保存'
    editing.value = null
    reload()
  } catch (e: any) { error.value = e.message }
}

async function remove(p: ProjectCard) {
  error.value = ''; saved.value = ''
  if (!p.id) return
  if (!confirm(`确定删除项目卡「${p.title}」？其下 ${p.examPoints?.length ?? 0} 条考点将一并删除。`)) return
  try {
    await projectsApi.remove(p.id)
    saved.value = '已删除'
    reload()
  } catch (e: any) { error.value = e.message }
}

reload()
</script>
