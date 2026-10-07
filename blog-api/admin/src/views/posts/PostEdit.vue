<template>
  <LayoutShell>
    <div class="topbar">
      <h2>{{ isNew ? '写文章' : '编辑文章' }}</h2>
      <div style="display:flex;gap:8px">
        <button class="btn btn-ghost" @click="router.back()">返回</button>
        <button class="btn btn-primary" :disabled="saving" @click="save(false)">
          {{ saving ? '保存中…' : form.published ? '保存' : '保存为草稿' }}
        </button>
        <button v-if="!isNew" class="btn btn-primary" :disabled="saving" @click="save(true)">
          {{ form.published ? '更新并发布' : '发布' }}
        </button>
      </div>
    </div>

    <div class="msg msg-error" v-if="error">{{ error }}</div>
    <div class="msg msg-ok" v-if="saved">已保存</div>

    <div style="display:grid;grid-template-columns:1fr 280px;gap:16px;align-items:start">
      <!-- 左：标题 + 编辑器 -->
      <div>
        <div class="card">
          <div class="form-row">
            <input v-model="form.title" placeholder="文章标题" style="font-size:18px;font-weight:600;border:none" />
          </div>
          <div class="editor-toolbar">
            <button @click="toggH2">H2</button>
            <button @click="toggH3">H3</button>
            <span class="divider"></span>
            <button @click="togg('bold')"><b>B</b></button>
            <button @click="togg('italic')"><i>i</i></button>
            <button @click="togg('underline')">U</button>
            <button @click="togg('strike')"><s>S</s></button>
            <span class="divider"></span>
            <button @click="togg('bulletList')">• 列表</button>
            <button @click="togg('orderedList')">1. 列表</button>
            <button @click="togg('blockquote')">引用</button>
            <button @click="togg('codeBlock')">代码块</button>
            <span class="divider"></span>
            <button @click="addLink">链接</button>
            <button @click="addImage">图片</button>
            <button @click="addTable">表格</button>
            <span v-if="editorUploading" class="upload-hint">图片上传中…</span>
          </div>
          <EditorContent :editor="editor" />
        </div>
      </div>

      <!-- 右：元信息 -->
      <div>
        <div class="card">
          <div class="form-row">
            <label>固定链接 slug</label>
            <input v-model="form.slug" placeholder="my-post" />
          </div>
          <div class="form-row">
            <label>封面图</label>
            <div class="cover-picker">
              <div class="cover-preview" v-if="form.cover" @dragover.prevent @drop.prevent="onCoverDrop">
                <img :src="form.cover" @error="onCoverError" :class="{ broken: coverBroken }" />
                <div class="cover-uploading" v-if="coverUploading">封面上传中…</div>
              </div>
              <div class="cover-empty" v-else :class="{ dragover: coverDragover }" @click="coverFile?.click()" @dragover.prevent="coverDragover=true" @dragleave="coverDragover=false" @drop.prevent="coverDragover=false; onCoverDrop($event)">
                <span v-if="!coverUploading">＋ 点击上传 / Ctrl+V 粘贴 / 拖拽图片</span>
                <span v-else>封面上传中…</span>
              </div>
              <div class="cover-toolbar">
                <button class="btn btn-sm btn-primary" @click="coverFile?.click()" :disabled="coverUploading">
                  {{ coverUploading ? '上传中…' : '上传' }}
                </button>
                <button class="btn btn-sm btn-ghost" @click="openPicker" :disabled="picker.loading">从附件库选</button>
                <button class="btn btn-sm btn-ghost" v-if="form.cover" @click="clearCover">移除</button>
              </div>
              <input v-model="form.cover" placeholder="或粘贴图片 URL https://…" class="cover-url-input" @input="coverBroken=false" />
              <div class="cover-hint">编辑器外按 Ctrl+V 即可把剪贴板图片设为封面；不设置时保存后自动采用文章第一张图</div>
              <input ref="coverFile" type="file" accept="image/*" style="display:none" @change="onCoverUpload" />
            </div>
          </div>
          <div class="form-row">
            <label>摘要</label>
            <textarea v-model="form.excerpt" rows="3" placeholder="留空自动生成"></textarea>
          </div>
        </div>

        <div class="card">
          <div class="form-row">
            <label>分类</label>
            <label v-for="c in categories" :key="c.haloName" style="display:flex;align-items:center;gap:6px;font-weight:normal;margin-bottom:4px">
              <input type="checkbox" style="width:auto" :value="c.haloName" v-model="formCategories" />
              {{ c.displayName }}
            </label>
          </div>
        </div>

        <div class="card">
          <div class="form-row">
            <label>标签（点击选择，可多选）</label>
            <div style="display:flex;flex-wrap:wrap;gap:6px">
              <button v-for="t in allTags" :key="t.haloName" type="button"
                      class="tag-pick" :class="{ on: formTags.includes(t.haloName) }"
                      @click="toggleTag(t.haloName)">{{ t.displayName }}</button>
              <span v-if="!allTags.length" style="color:#999;font-size:12px">暂无标签，可到「标签管理」新建</span>
            </div>
          </div>
        </div>

        <div class="card">
          <div class="form-row">
            <label>可见性</label>
            <select v-model="form.visible">
              <option value="PUBLIC">公开</option>
              <option value="PRIVATE">私密</option>
            </select>
          </div>
          <div class="form-row" style="display:flex;gap:16px">
            <label style="display:flex;align-items:center;gap:6px;font-weight:normal">
              <input type="checkbox" style="width:auto" v-model="form.published"> 已发布
            </label>
            <label style="display:flex;align-items:center;gap:6px;font-weight:normal">
              <input type="checkbox" style="width:auto" v-model="form.pinned"> 置顶
            </label>
          </div>
        </div>
      </div>
    </div>

    <!-- 附件库选择器 -->
    <div v-if="picker.show" class="modal-overlay" @click.self="picker.show=false">
      <div class="modal">
        <div class="modal-header">
          <h3>从附件库选择图片</h3>
          <button class="modal-close" @click="picker.show=false">×</button>
        </div>
        <div class="modal-body">
          <div v-if="picker.loading" class="modal-empty">加载中…</div>
          <div v-else-if="picker.list.length" class="attachment-grid">
            <div v-for="a in picker.list" :key="a.id" class="attachment-item" @click="pickFromLib(a)">
              <img v-if="isImage(a)" :src="a.urlPath" loading="lazy" />
              <div v-else class="attachment-noimg">{{ a.contentType || '文件' }}</div>
              <div class="attachment-name">{{ a.originalName }}</div>
            </div>
          </div>
          <div v-else class="modal-empty">附件库为空，请先到「附件库」页面上传图片</div>
        </div>
      </div>
    </div>
  </LayoutShell>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useEditor, EditorContent } from '@tiptap/vue-3'
import StarterKit from '@tiptap/starter-kit'
import Underline from '@tiptap/extension-underline'
import Link from '@tiptap/extension-link'
import Image from '@tiptap/extension-image'
import Table from '@tiptap/extension-table'
import TableRow from '@tiptap/extension-table-row'
import TableCell from '@tiptap/extension-table-cell'
import TableHeader from '@tiptap/extension-table-header'
import CodeBlockLowlight from '@tiptap/extension-code-block-lowlight'
import { marked } from 'marked'
import LayoutShell from '../../components/LayoutShell.vue'
import lowlight from '../../components/editor/lowlight'
import { htmlToMarkdown } from '../../components/editor/htmlToMarkdown'
import { postsApi } from '../../api/posts'
import { categoriesApi, type Category } from '../../api/categories'
import { attachmentsApi, type Attachment } from '../../api/attachments'
import { api, uploadFile } from '../../api/client'

const route = useRoute()
const router = useRouter()
const id = route.params.id ? Number(route.params.id) : null
const isNew = !id

const form = ref({
  title: '', slug: '', cover: '', excerpt: '',
  visible: 'PUBLIC', published: false, pinned: false,
})
const formCategories = ref<string[]>([])
const categories = ref<Category[]>([])
// 标签：halo_name 列表（与后端 PostRequest.tags 对齐）
const formTags = ref<string[]>([])
const allTags = ref<{ id: number; haloName: string; displayName: string }[]>([])

function toggleTag(haloName: string) {
  const i = formTags.value.indexOf(haloName)
  if (i >= 0) formTags.value.splice(i, 1)
  else formTags.value.push(haloName)
}
const saving = ref(false)
const saved = ref(false)
const error = ref('')

// 封面图上传
const coverFile = ref<HTMLInputElement>()
const coverUploading = ref(false)
const coverBroken = ref(false)
const coverDragover = ref(false)

// 编辑器图片上传
const editorUploading = ref(false)

// 附件库选择器
const picker = ref<{ show: boolean; loading: boolean; list: Attachment[] }>({
  show: false, loading: false, list: [],
})

function onCoverError() { coverBroken.value = true }
function clearCover() { form.value.cover = ''; coverBroken.value = false }

async function setCoverFromBlob(f: File) {
  coverUploading.value = true
  error.value = ''
  try {
    const att = await uploadFile('/api/admin/attachments/upload', f)
    form.value.cover = att.urlPath
    coverBroken.value = false
  } catch (err: any) {
    error.value = '封面上传失败：' + err.message
  } finally {
    coverUploading.value = false
  }
}

async function onCoverUpload(e: Event) {
  const input = e.target as HTMLInputElement
  const f = input.files?.[0]
  if (!f) return
  await setCoverFromBlob(f)
  input.value = ''
}

function onCoverDrop(e: DragEvent) {
  const f = Array.from(e.dataTransfer?.files || []).find(f => f.type.startsWith('image/'))
  if (f) setCoverFromBlob(f)
}

// 编辑器外按 Ctrl+V：剪贴板图片直接设为封面
function onGlobalPaste(e: ClipboardEvent) {
  if (editor.value?.isFocused) return
  const hasText = !!(e.clipboardData?.getData('text/plain') || '').trim()
  const target = e.target as HTMLElement | null
  const isTextField = !!target && (target.tagName === 'INPUT' || target.tagName === 'TEXTAREA')
  if (hasText && isTextField) return
  for (const it of e.clipboardData?.items || []) {
    if (it.type.startsWith('image/')) {
      const f = it.getAsFile()
      if (f) {
        e.preventDefault()
        setCoverFromBlob(f)
      }
      return
    }
  }
}
onMounted(() => document.addEventListener('paste', onGlobalPaste))

async function openPicker() {
  picker.value.show = true
  picker.value.loading = true
  try {
    picker.value.list = await attachmentsApi.list()
  } catch (err: any) {
    error.value = '附件列表加载失败：' + err.message
  } finally {
    picker.value.loading = false
  }
}

function pickFromLib(a: Attachment) {
  form.value.cover = a.urlPath
  coverBroken.value = false
  picker.value.show = false
}

function isImage(a: Attachment) { return (a.contentType || '').startsWith('image/') }

const editor = useEditor({
  extensions: [
    StarterKit,
    Underline,
    Link.configure({ openOnClick: false, autolink: true }),
    Image,
    Table.configure({ resizable: true }),
    TableRow,
    TableHeader,
    TableCell,
    CodeBlockLowlight.configure({ lowlight }),
  ],
  content: '',
  editorProps: {
    handlePaste(_view, event: ClipboardEvent) {
      return handleTransferItems(event.clipboardData?.items)
    },
    handleDrop(_view, event: DragEvent) {
      return handleTransferItems(event.dataTransfer?.items)
    },
  },
})

function handleTransferItems(items?: DataTransferItemList): boolean {
  if (!items) return false
  let handled = false
  for (const it of items) {
    if (it.type.startsWith('image/')) {
      const file = it.getAsFile()
      if (file) {
        handled = true
        uploadAndInsert(file)
      }
    }
  }
  return handled
}

async function uploadAndInsert(file: File) {
  editorUploading.value = true
  try {
    const att = await uploadFile('/api/admin/attachments/upload', file)
    editor.value?.chain().focus().setImage({ src: att.urlPath }).run()
  } catch (e: any) {
    error.value = '图片上传失败：' + e.message
  } finally {
    editorUploading.value = false
  }
}

function togg(name: string) {
  const chain = editor.value?.chain().focus() as any
  chain[`toggle${name[0].toUpperCase()}${name.slice(1)}`]().run()
}
function toggH2() { editor.value?.chain().focus().toggleHeading({ level: 2 }).run() }
function toggH3() { editor.value?.chain().focus().toggleHeading({ level: 3 }).run() }

function addLink() {
  const url = prompt('链接地址：')
  if (url) editor.value?.chain().focus().setLink({ href: url }).run()
}

async function addImage() {
  const url = prompt('图片 URL（留空则从本地选择上传）：')
  if (url) {
    editor.value?.chain().focus().setImage({ src: url }).run()
    return
  }
  const input = document.createElement('input')
  input.type = 'file'
  input.accept = 'image/*'
  input.onchange = () => {
    const f = input.files?.[0]
    if (f) uploadAndInsert(f)
  }
  input.click()
}

function addTable() {
  editor.value?.chain().focus().insertTable({ rows: 3, cols: 3, withHeaderRow: true }).run()
}

async function save(publish: boolean) {
  error.value = ''
  saved.value = false
  saving.value = true
  try {
    const md = htmlToMarkdown(editor.value?.getHTML() || '')
    // 未手动设置封面时，自动取文章第一张图作为封面；全文无图则留空
    if (!form.value.cover) {
      const m = md.match(/!\[[^\]]*\]\(([^)\s]+)/) || md.match(/<img[^>]+src=["']([^"']+)["']/i)
      if (m?.[1]) form.value.cover = m[1]
    }
    const payload = {
      title: form.value.title || '无标题',
      slug: form.value.slug || form.value.title,
      cover: form.value.cover,
      excerpt: form.value.excerpt,
      content: md,
      categories: formCategories.value,
      tags: formTags.value,
      published: publish || form.value.published,
      pinned: form.value.pinned,
      priority: 0,
      visible: form.value.visible,
      allowComment: true,
    }
    if (isNew) {
      const p = await postsApi.create(payload)
      router.replace(`/admin/posts/${p.id}`)
    } else {
      await postsApi.update(id!, payload)
    }
    saved.value = true
    form.value.published = publish || form.value.published
  } catch (e: any) {
    error.value = e.message
  } finally {
    saving.value = false
  }
}

onBeforeUnmount(() => {
  editor.value?.destroy()
  document.removeEventListener('paste', onGlobalPaste)
})

;(async () => {
  categories.value = await categoriesApi.list()
  try { allTags.value = await api('GET', '/api/admin/tags') } catch { allTags.value = [] }
  if (id) {
    const p = await postsApi.get(id)
    form.value.title = p.title
    form.value.slug = p.slug
    form.value.cover = p.cover || ''
    form.value.excerpt = p.excerpt || ''
    form.value.visible = p.visible || 'PUBLIC'
    form.value.published = !!p.published
    form.value.pinned = !!p.pinned
    formCategories.value = p.categories || []
    formTags.value = p.tags || []
    // Markdown 源文 → HTML 给编辑器
    const html = await marked.parse(p.content || '')
    editor.value?.commands.setContent(html)
  }
})()
</script>

<style scoped>
.tag-pick {
  padding: 3px 10px;
  font-size: 12px;
  border: 1px solid #d1d5db;
  border-radius: 999px;
  background: #fff;
  color: #475569;
  cursor: pointer;
}
.tag-pick.on {
  background: #e0f0fb;
  border-color: #7cc0ec;
  color: #0562a9;
  font-weight: 600;
}
</style>
