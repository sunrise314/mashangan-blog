<template>
  <LayoutShell>
    <div class="topbar">
      <h2>分类管理</h2>
      <div style="display:flex;gap:8px">
        <button v-if="!sortMode" class="btn btn-ghost" @click="enterSortMode">调整顺序</button>
        <button v-if="sortMode" class="btn btn-primary" @click="exitSortMode">完成排序</button>
        <button v-if="!sortMode" class="btn btn-primary" @click="openNew">新建分类</button>
      </div>
    </div>

    <div class="msg msg-error" v-if="error">{{ error }}</div>
    <div class="msg msg-ok" v-if="saved">{{ saved }}</div>

    <!-- 排序模式提示条 -->
    <div v-if="sortMode" class="sort-banner">
      排序模式：拖动行调整分类展示顺序，松手自动保存。当前共 {{ list.length }} 个分类。
    </div>

    <div class="card" :class="{ 'sort-mode': sortMode }">
      <table>
        <thead>
          <tr>
            <th style="width:44px"></th>
            <th>名称</th>
            <th>slug</th>
            <th>栏目分区</th>
            <th>隐藏列表</th>
            <th v-if="!sortMode" style="width:140px">操作</th>
          </tr>
        </thead>
        <tbody>
          <template v-for="c in list" :key="c.id">
            <tr
              :draggable="sortMode && !savingOrder"
              :class="{ 'drag-row': dragId === c.id, 'drag-over': overId === c.id && dragId !== c.id }"
              @dragstart="sortMode && onDragStart($event, c)"
              @dragover.prevent="sortMode && (overId = c.id!)"
              @drop.prevent="sortMode && onDrop($event, c)"
              @dragend="dragId = null; overId = null"
            >
              <td>
                <span v-if="sortMode" class="drag-handle" title="拖动调整顺序">⠿</span>
                <button v-else class="btn btn-sm btn-ghost" @click="toggleExpand(c)">
                  {{ expanded.has(c.id!) ? '▼' : '▶' }}
                </button>
              </td>
              <td>
                {{ c.displayName }}
                <span v-if="!sortMode && c.status === 'completed'" class="badge badge-on" style="margin-left:6px">已完结</span>
                <span v-if="!sortMode && postsCache[c.id!]?.length" class="badge badge-on" style="margin-left:6px">{{ postsCache[c.id!].length }} 篇</span>
              </td>
              <td style="color:#666">{{ c.slug }}</td>
              <td>
                <span v-if="c.section" class="badge badge-on">{{ c.section }}</span>
                <span v-else style="color:#aaa">通用</span>
              </td>
              <td><span class="badge" :class="c.hideFromList ? 'badge-off' : 'badge-on'">{{ c.hideFromList ? '隐藏' : '显示' }}</span></td>
              <td v-if="!sortMode">
                <button class="btn btn-sm btn-ghost" @click="openEdit(c)">编辑</button>
                <button class="btn btn-sm btn-danger" @click="doDelete(c.id!)">删除</button>
              </td>
            </tr>
            <!-- 展开子表：仅浏览模式 -->
            <tr v-if="!sortMode && expanded.has(c.id!)">
              <td :colspan="6" style="background:#fafbfc;padding:12px 12px 12px 44px">
                <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:8px">
                  <strong>分类下文章（共 {{ postsCache[c.id!]?.length ?? 0 }} 篇）</strong>
                  <button class="btn btn-sm btn-ghost" @click="loadPosts(c.id!, true)">刷新</button>
                </div>
                <div v-if="loadingCat === c.id" style="color:#999;padding:8px 0">加载中…</div>
                <div v-else-if="!postsCache[c.id!]?.length" style="color:#999;padding:8px 0">该分类下暂无文章</div>
                <table v-else>
                  <thead>
                    <tr><th>标题</th><th style="width:90px">状态</th><th style="width:150px">更新时间</th><th style="width:210px">操作</th></tr>
                  </thead>
                  <tbody>
                    <tr v-for="p in postsCache[c.id!]" :key="p.id">
                      <td>
                        {{ p.title }}
                        <span v-if="p.pinned" class="badge badge-on" style="margin-left:6px">置顶</span>
                      </td>
                      <td><span class="badge" :class="p.published ? 'badge-on' : 'badge-off'">{{ p.published ? '已发布' : '草稿' }}</span></td>
                      <td style="color:#666">{{ fmtTime(p.updatedAt) }}</td>
                      <td>
                        <button class="btn btn-sm btn-ghost" @click="editPost(p)">编辑</button>
                        <button class="btn btn-sm btn-ghost" @click="togglePublish(p, c.id!)">{{ p.published ? '下架' : '发布' }}</button>
                        <button class="btn btn-sm btn-danger" @click="deletePost(p, c.id!)">删除</button>
                      </td>
                    </tr>
                  </tbody>
                </table>
              </td>
            </tr>
          </template>
        </tbody>
      </table>

      <!-- 分页控件：仅浏览模式 -->
      <div v-if="!sortMode" class="toolbar" style="margin-top:12px;justify-content:flex-end">
        <span style="color:#888;font-size:13px;margin-right:auto">共 {{ total }} 个分类</span>
        <button class="btn btn-ghost" :disabled="page <= 1" @click="page--; reload()">上一页</button>
        <span style="font-size:13px;color:#666">第 {{ page }} / {{ totalPages }} 页</span>
        <button class="btn btn-ghost" :disabled="page >= totalPages" @click="page++; reload()">下一页</button>
      </div>
    </div>

    <!-- 编辑弹窗 -->
    <div v-if="editing" style="position:fixed;inset:0;background:rgba(0,0,0,.4);display:flex;align-items:center;justify-content:center;z-index:100" @click.self="editing=null">
      <div class="card" style="width:480px;max-height:90vh;overflow:auto">
        <h3 style="margin-bottom:12px">{{ editing.id ? '编辑分类' : '新建分类' }}</h3>
        <div class="form-row"><label>显示名称</label><input v-model="form.displayName"></div>
        <div class="form-row"><label>slug（URL 用）</label><input v-model="form.slug"></div>
        <div class="form-row"><label>封面图 URL</label><input v-model="form.cover"></div>
        <div class="form-row"><label>描述</label><textarea v-model="form.description" rows="2"></textarea></div>
        <div class="grid-2">
          <div class="form-row"><label>排序（越大越靠前）</label><input type="number" v-model.number="form.priority"></div>
          <div class="form-row">
            <label>连载状态</label>
            <select v-model="form.status">
              <option value="updating">连载中</option>
              <option value="completed">已完结</option>
            </select>
          </div>
        </div>
        <div class="grid-2">
          <div class="form-row">
            <label>栏目分区</label>
            <select v-model="form.section">
              <option value="">通用（首页/归档可见）</option>
              <option value="interview">interview（八股题库，首页隐藏）</option>
            </select>
          </div>
        </div>
        <div class="form-row" style="display:flex;gap:16px">
          <label style="display:flex;align-items:center;gap:6px;font-weight:normal">
            <input type="checkbox" style="width:auto" v-model="form.hideFromList"> 从列表隐藏
          </label>
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
import { useRouter } from 'vue-router'
import LayoutShell from '../../components/LayoutShell.vue'
import { categoriesApi, type Category, type CategoryPostSummary } from '../../api/categories'
import { postsApi } from '../../api/posts'

const router = useRouter()
const list = ref<Category[]>([])
const page = ref(1)
const size = 20
const total = ref(0)
const totalPages = ref(1)
const editing = ref<any>(null)
const form = ref<Partial<Category>>({})
const error = ref('')
const saved = ref('')

const expanded = ref(new Set<number>())
const postsCache = ref<Record<number, CategoryPostSummary[]>>({})
const loadingCat = ref<number | null>(null)

// 排序模式
const sortMode = ref(false)
const dragId = ref<number | null>(null)
const overId = ref<number | null>(null)
const savingOrder = ref(false)

async function reload() {
  const r = await categoriesApi.page(page.value, size)
  list.value = r.records
  total.value = r.total
  totalPages.value = r.pages || 1
  if (!r.records.length && page.value > 1) { page.value--; await reload() }
}

/** 进入排序模式：加载全量分类，不分页 */
async function enterSortMode() {
  error.value = ''; saved.value = ''
  sortMode.value = true
  expanded.value = new Set()
  list.value = await categoriesApi.list()
}

/** 退出排序模式：回到分页浏览 */
function exitSortMode() {
  sortMode.value = false
  page.value = 1
  reload()
}

function onDragStart(e: DragEvent, c: Category) {
  dragId.value = c.id!
  if (e.dataTransfer) {
    e.dataTransfer.effectAllowed = 'move'
    e.dataTransfer.setData('text/plain', String(c.id))
  }
}

function onDrop(_e: DragEvent, target: Category) {
  const dragged = dragId.value
  dragId.value = null; overId.value = null
  if (!dragged || dragged === target.id) return
  const arr = [...list.value]
  const from = arr.findIndex(x => x.id === dragged)
  const to = arr.findIndex(x => x.id === target.id)
  if (from < 0 || to < 0) return
  const [moved] = arr.splice(from, 1)
  arr.splice(to, 0, moved)
  list.value = arr
  persistOrder(arr)
}

/** 排序模式下直接提交全量顺序 */
async function persistOrder(order: Category[]) {
  savingOrder.value = true
  error.value = ''
  try {
    await categoriesApi.reorder(order.map(c => c.id!))
    saved.value = '顺序已保存'
    setTimeout(() => { if (saved.value === '顺序已保存') saved.value = '' }, 2000)
  } catch (e: any) { error.value = e.message }
  finally { savingOrder.value = false }
}

async function loadPosts(id: number, force = false) {
  if (!force && postsCache.value[id]) return
  loadingCat.value = id
  try { postsCache.value[id] = await categoriesApi.posts(id) }
  catch (e: any) { error.value = e.message }
  finally { loadingCat.value = null }
}

function toggleExpand(c: Category) {
  const id = c.id!
  const next = new Set(expanded.value)
  if (next.has(id)) next.delete(id)
  else { next.add(id); loadPosts(id) }
  expanded.value = next
}

function editPost(p: CategoryPostSummary) {
  router.push(`/admin/posts/${p.id}`)
}

async function togglePublish(p: CategoryPostSummary, catId: number) {
  error.value = ''
  try {
    const post = await postsApi.get(p.id)
    post.published = !post.published
    await postsApi.update(p.id, post)
    await loadPosts(catId, true)
  } catch (e: any) { error.value = e.message }
}

async function deletePost(p: CategoryPostSummary, catId: number) {
  if (!confirm(`删除文章「${p.title}」？可在文章管理回收站恢复。`)) return
  error.value = ''
  try {
    await postsApi.delete(p.id)
    await loadPosts(catId, true)
  } catch (e: any) { error.value = e.message }
}

function fmtTime(s?: string) {
  if (!s) return '—'
  return s.replace('T', ' ').slice(0, 16)
}

function openNew() {
  form.value = { displayName: '', slug: '', priority: 0, hideFromList: false, section: '', status: 'updating' }
  editing.value = form.value
}
function openEdit(c: Category) {
  form.value = { ...c }
  editing.value = c
}
async function save() {
  error.value = ''; saved.value = ''
  try {
    if (form.value.id) await categoriesApi.update(form.value.id, form.value)
    else await categoriesApi.create(form.value)
    saved.value = '已保存'
    editing.value = null
    reload()
  } catch (e: any) { error.value = e.message }
}
async function doDelete(id: number) {
  if (!confirm('删除分类？其下文章不会删除。')) return
  await categoriesApi.delete(id)
  const next = new Set(expanded.value)
  next.delete(id)
  expanded.value = next
  delete postsCache.value[id]
  reload()
}
reload()
</script>

<style scoped>
tbody tr[draggable="true"] { cursor: grab; }
.drag-row { opacity: .35; }
.drag-over td { border-top: 2px solid #3b82f6; }
.drag-handle { color: #bbb; margin-right: 4px; user-select: none; }
.sort-banner {
  background: #eff6ff; border: 1px solid #bfdbfe; border-radius: 6px;
  padding: 8px 14px; margin-bottom: 12px; color: #1e40af; font-size: 13px;
}
.sort-mode { max-height: 70vh; overflow-y: auto; }
</style>
