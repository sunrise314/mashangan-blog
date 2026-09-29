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
            <label>封面图 URL</label>
            <input v-model="form.cover" placeholder="https://…" />
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
  </LayoutShell>
</template>

<script setup lang="ts">
import { ref, onBeforeUnmount } from 'vue'
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
import { uploadFile } from '../../api/client'

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
const saving = ref(false)
const saved = ref(false)
const error = ref('')

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
})

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
  input.onchange = async () => {
    const f = input.files?.[0]
    if (!f) return
    try {
      const att = await uploadFile('/api/admin/attachments/upload', f)
      editor.value?.chain().focus().setImage({ src: att.urlPath }).run()
    } catch (e: any) {
      error.value = '图片上传失败：' + e.message
    }
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
    const payload = {
      title: form.value.title || '无标题',
      slug: form.value.slug || form.value.title,
      cover: form.value.cover,
      excerpt: form.value.excerpt,
      content: md,
      categories: formCategories.value,
      tags: [],
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

onBeforeUnmount(() => { editor.value?.destroy() })

;(async () => {
  categories.value = await categoriesApi.list()
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
    // Markdown 源文 → HTML 给编辑器
    const html = await marked.parse(p.content || '')
    editor.value?.commands.setContent(html)
  }
})()
</script>
