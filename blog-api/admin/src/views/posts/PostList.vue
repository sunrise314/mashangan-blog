<template>
  <LayoutShell>
    <div class="topbar">
      <h2>文章</h2>
      <div style="display:flex;gap:8px">
        <button class="btn btn-ghost" @click="openUploadModal">上传MD文档</button>
        <button class="btn btn-primary" @click="goNew">写文章</button>
      </div>
    </div>

    <div class="card">
      <div class="toolbar">
        <input v-model="keyword" placeholder="搜索标题…" style="max-width:240px" @keyup.enter="reload" />
        <button class="btn btn-ghost" @click="reload">搜索</button>
        <button class="btn btn-ghost" @click="trashView = !trashView; reload()">
          {{ trashView ? '返回列表' : '回收站' }}
        </button>
        <span style="color:#888;font-size:13px">共 {{ total }} 篇</span>
      </div>

      <table v-if="records.length">
        <thead>
          <tr>
            <th>标题</th>
            <th>状态</th>
            <th>分类</th>
            <th>更新时间</th>
            <th style="width:160px">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="p in records" :key="p.id">
            <td>
              <a
                v-if="!trashView"
                class="post-title-link"
                :href="siteUrl + '/archives/' + p.slug"
                target="_blank"
                rel="noopener"
                :title="'打开文章：' + (p.title || '(无标题)')"
              >{{ p.title || '(无标题)' }}</a>
              <span v-else style="color:#94a3b8">{{ p.title || '(无标题)' }}</span>
            </td>
            <td>
              <span class="badge" :class="p.published ? 'badge-pub' : 'badge-draft'">
                {{ p.published ? '已发布' : '草稿' }}
              </span>
              <span v-if="p.pinned" class="badge badge-on" style="margin-left:4px">置顶</span>
            </td>
            <td style="color:#666">{{ (p.categories || []).join(', ') }}</td>
            <td style="color:#888;font-size:13px">{{ fmt(p.updatedAt) }}</td>
            <td>
              <button class="btn btn-sm btn-ghost" @click="goEdit(p.id!)">编辑</button>
              <template v-if="!trashView">
                <button class="btn btn-sm btn-danger" @click="doDelete(p.id!)">删除</button>
              </template>
              <template v-else>
                <button class="btn btn-sm btn-primary" @click="doRestore(p.id!)">恢复</button>
                <button class="btn btn-sm btn-danger" @click="doPurge(p.id!)">彻底删除</button>
              </template>
            </td>
          </tr>
        </tbody>
      </table>
      <div v-else style="text-align:center;color:#999;padding:40px 0">暂无文章</div>

      <div class="toolbar" style="margin-top:12px;justify-content:flex-end">
        <button class="btn btn-ghost" :disabled="page <= 1" @click="page--; reload()">上一页</button>
        <span style="font-size:13px;color:#666">第 {{ page }} / {{ totalPages }} 页</span>
        <button class="btn btn-ghost" :disabled="page >= totalPages" @click="page++; reload()">下一页</button>
      </div>
    </div>

    <!-- 上传MD文档弹窗 -->
    <div v-if="upload.show" class="modal-overlay" @click.self="closeUpload">
      <div class="modal">
        <div class="modal-header">
          <h3>上传 MD 文档</h3>
          <button class="modal-close" @click="closeUpload">×</button>
        </div>
        <div class="modal-body">
          <div class="msg msg-error" v-if="upload.error">{{ upload.error }}</div>

          <!-- 文件选择区 -->
          <div
            class="upload-zone"
            :class="{ dragover: upload.dragover }"
            @click="!upload.file && mdFile?.click()"
            @dragover.prevent="upload.dragover = true"
            @dragleave="upload.dragover = false"
            @drop.prevent="upload.dragover = false; onMdFile($event.dataTransfer?.files?.[0])"
          >
            <template v-if="!upload.file">
              <div>点击选择 或 拖拽 .md 文件到此处</div>
              <div style="font-size:12px;color:#94a3b8;margin-top:4px">支持 frontmatter（自动识别 title / slug）</div>
            </template>
            <template v-else>
              <div style="font-weight:600;color:#0f172a">{{ upload.file.name }}</div>
              <div style="font-size:12px;color:#64748b;margin-top:4px">
                {{ (upload.file.size / 1024).toFixed(1) }} KB · {{ upload.lineCount }} 行
                <a style="color:#2563eb;margin-left:8px" @click.stop="mdFile?.click()">重新选择</a>
              </div>
            </template>
          </div>
          <input ref="mdFile" type="file" accept=".md,.markdown,text/markdown,text/plain" style="display:none" @change="onMdInputChange" />

          <div class="grid-2" style="margin-top:12px">
            <div class="form-row"><label>标题</label><input v-model="upload.title" placeholder="自动取首个 # 标题"></div>
            <div class="form-row"><label>slug</label><input v-model="upload.slug" placeholder="自动从文件名生成"></div>
          </div>
          <div class="form-row"><label>分类</label>
            <select v-model="upload.categorySlug">
              <option value="">通用</option>
              <option v-for="c in categories" :key="c.haloName" :value="c.slug">{{ c.displayName }}</option>
            </select>
          </div>

          <div class="form-row ai-toggle-row">
            <label style="display:flex;align-items:center;gap:8px;font-weight:600;color:#0f172a;font-size:14px;cursor:pointer">
              <input type="checkbox" style="width:auto" v-model="upload.aiIllustrate">
              AI 智能配图
            </label>
            <span style="font-size:12px;color:#94a3b8">由 AI 分析文章并自动插入配图</span>
          </div>
          <template v-if="upload.aiIllustrate">
            <div class="grid-2">
              <div class="form-row"><label>配图数量</label><input type="number" v-model.number="upload.imageCount" min="1" max="10"></div>
              <div class="form-row"><label>风格</label><input v-model="upload.style" placeholder="简约通用"></div>
            </div>
            <div class="msg" style="background:#eff6ff;color:#1d4ed8;font-size:13px">
              AI 配图模式完成后将<b>直接发布</b>；配图密钥请先到「AI 配图设置」页配置。若已存在相同 slug 的文章，内容会被覆盖。
            </div>
            <!-- AI 配图进度 -->
            <div v-if="upload.task" class="card" style="margin-top:12px;padding:12px">
              <div style="background:#e2e8f0;height:8px;border-radius:4px;overflow:hidden">
                <div :style="{ width: (upload.task.percent || 0) + '%', background: '#2563eb', height: '100%', transition: 'width .3s' }"></div>
              </div>
              <div style="margin-top:8px;color:#555;font-size:13px">{{ upload.task.message }}</div>
              <ul v-if="upload.task.warnings?.length" style="margin:8px 0 0;color:#b45309;font-size:12px">
                <li v-for="w in upload.task.warnings">{{ w }}</li>
              </ul>
              <div v-if="upload.task.status === 'done'" style="margin-top:12px;display:flex;gap:8px">
                <a class="btn btn-sm btn-ghost" :href="siteUrl + (upload.task.permalink || '')" target="_blank">查看文章</a>
                <button class="btn btn-sm btn-primary" @click="goEdit(upload.task.postId)">前往编辑</button>
              </div>
            </div>
          </template>
        </div>
        <div class="modal-footer">
          <button class="btn btn-ghost" @click="closeUpload">{{ upload.task?.status === 'done' ? '关闭' : '取消' }}</button>
          <button class="btn btn-primary" :disabled="!upload.file || upload.importing" @click="doImport">
            {{ upload.importing ? '导入中…' : (upload.aiIllustrate ? '配图并发布' : '导入为草稿') }}
          </button>
        </div>
      </div>
    </div>
  </LayoutShell>
</template>

<script setup lang="ts">
import { ref, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import LayoutShell from '../../components/LayoutShell.vue'
import { postsApi, type Post } from '../../api/posts'
import { categoriesApi, type Category } from '../../api/categories'
import { api } from '../../api/client'

const siteUrl = 'https://www.mashangan.com'

const router = useRouter()
const records = ref<Post[]>([])
const page = ref(1)
const size = 20
const total = ref(0)
const totalPages = ref(1)
const keyword = ref('')
const trashView = ref(false)
const categories = ref<Category[]>([])

async function reload() {
  const r = await postsApi.list(page.value, size, keyword.value || undefined, trashView.value)
  records.value = r.records
  total.value = r.total
  totalPages.value = r.pages || 1
}

function goNew() { router.push('/admin/posts/new') }
function goEdit(id: number) { router.push(`/admin/posts/${id}`) }

// ---------- 上传 MD 弹窗 ----------
const mdFile = ref<HTMLInputElement | null>(null)
const upload = ref({
  show: false,
  file: null as File | null,
  text: '',
  lineCount: 0,
  title: '',
  slug: '',
  categorySlug: '',
  aiIllustrate: false,
  imageCount: 3,
  style: '简约通用',
  dragover: false,
  importing: false,
  error: '',
  task: null as any,
})
let pollTimer: any = null

function openUploadModal() {
  upload.value = {
    show: true, file: null, text: '', lineCount: 0,
    title: '', slug: '', categorySlug: '',
    aiIllustrate: false, imageCount: 3, style: '简约通用',
    dragover: false, importing: false, error: '', task: null,
  }
}

function closeUpload() {
  if (upload.value.importing && upload.value.aiIllustrate) {
    // AI 配图进行中不允许误关
    if (!confirm('AI 配图仍在进行中，确定关闭？后台任务会继续完成。')) return
  }
  if (pollTimer) { clearInterval(pollTimer); pollTimer = null }
  upload.value.show = false
}

/** 读取 MD 文件并解析默认标题 / slug */
async function onMdInputChange(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  await onMdFile(file)
}

async function onMdFile(file?: File) {
  if (!file) return
  upload.value.error = ''
  upload.value.task = null
  if (!/\.(md|markdown)$/i.test(file.name) && file.type !== 'text/markdown' && file.type !== 'text/plain') {
    upload.value.error = '请选择 .md / .markdown 文件'
    return
  }
  upload.value.file = file
  try {
    const text = await file.text()
    if (!text.trim()) {
      upload.value.error = '文件内容为空'
      upload.value.file = null
      return
    }
    upload.value.text = text
    upload.value.lineCount = text.split('\n').length
    const nameBase = file.name.replace(/\.(md|markdown)$/i, '').trim()
    const fmTitle = text.match(/^---\r?\n[\s\S]*?\r?\n---/)?.[0]?.match(/^title:\s*(.+)$/m)?.[1]?.trim().replace(/^["']|["']$/g, '')
    const heading = text.match(/^#\s+(.+)$/m)?.[1]?.trim()
    upload.value.title = fmTitle || heading || nameBase || '未命名导入'
    upload.value.slug =
      nameBase.toLowerCase().replace(/\s+/g, '-').replace(/[^\w\u4e00-\u9fa5-]/g, '') ||
      `md-${Date.now()}`
  } catch (err: any) {
    upload.value.error = '读取文件失败：' + (err?.message || err)
    upload.value.file = null
  }
}

async function doImport() {
  const u = upload.value
  u.error = ''
  if (!u.file || !u.text.trim()) { u.error = '请先选择文件'; return }
  u.importing = true
  try {
    if (u.aiIllustrate) {
      const t = await api<any>('POST', '/api/admin/studio/import', {
        markdown: u.text, title: u.title, slug: u.slug,
        categorySlug: u.categorySlug, imageCount: u.imageCount, style: u.style,
      })
      u.task = t
      pollTimer = setInterval(async () => {
        try {
          const s = await api<any>('GET', `/api/admin/studio/status/${t.id}`)
          u.task = s
          if (s.status === 'done') {
            clearInterval(pollTimer!); pollTimer = null
            u.importing = false
            reload()
          } else if (s.status === 'error') {
            clearInterval(pollTimer!); pollTimer = null
            u.importing = false
            u.error = '配图发布失败：' + (s.error || s.message)
          }
        } catch (e: any) {
          clearInterval(pollTimer!); pollTimer = null
          u.importing = false
          u.error = '查询进度失败：' + e.message
        }
      }, 1500)
    } else {
      const post = await postsApi.create({
        title: u.title || '未命名导入',
        slug: u.slug || `md-${Date.now()}`,
        cover: '',
        excerpt: '',
        content: u.text,
        categories: u.categorySlug ? categories.value.filter(c => c.slug === u.categorySlug).flatMap(c => c.haloName ? [c.haloName] : []) : [],
        tags: [],
        published: false,
        pinned: false,
        priority: 0,
        visible: 'PUBLIC',
        allowComment: true,
      })
      closeUpload()
      router.push(`/admin/posts/${post.id}`)
    }
  } catch (err: any) {
    u.error = '导入失败：' + (err?.message || err)
    u.importing = false
  }
}

onBeforeUnmount(() => pollTimer && clearInterval(pollTimer))

async function doDelete(id: number) {
  if (!confirm('移入回收站？')) return
  await postsApi.delete(id)
  reload()
}
async function doRestore(id: number) {
  await postsApi.restore(id)
  reload()
}
async function doPurge(id: number) {
  if (!confirm('彻底删除后不可恢复，确定？')) return
  await postsApi.purge(id)
  reload()
}

function fmt(s?: string) {
  if (!s) return ''
  return new Date(s).toLocaleString('zh-CN', { hour12: false })
}

reload()
categoriesApi.list().then(cs => categories.value = cs).catch(() => {})
</script>

<style scoped>
.post-title-link { color: #2563eb; text-decoration: none; }
.post-title-link:hover { text-decoration: underline; }
.upload-zone { border: 2px dashed #cbd5e1; border-radius: 8px; padding: 24px; text-align: center; cursor: pointer; color: #64748b; transition: border-color .15s, background .15s; }
.upload-zone:hover, .upload-zone.dragover { border-color: #2563eb; background: #eff6ff; }
.ai-toggle-row { display: flex; align-items: center; gap: 12px; margin-top: 4px; }
.ai-toggle-row > span { margin: 0; }
.modal-footer { padding: 12px 16px; border-top: 1px solid #eee; display: flex; justify-content: flex-end; gap: 8px; }
</style>
