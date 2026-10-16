<template>
  <div class="cover-picker">
    <div class="cover-preview" v-if="modelValue" @dragover.prevent @drop.prevent="onDrop">
      <img :src="modelValue" @error="broken = true" :class="{ broken }" />
      <div class="cover-uploading" v-if="uploading">上传中…</div>
    </div>
    <div
      v-else
      class="cover-empty"
      :class="{ dragover }"
      @click="fileInput?.click()"
      @dragover.prevent="dragover = true"
      @dragleave="dragover = false"
      @drop.prevent="dragover = false; onDrop($event)"
    >
      <span v-if="!uploading">＋ 点击上传 / Ctrl+V 粘贴 / 拖拽图片</span>
      <span v-else>上传中…</span>
    </div>
    <div class="cover-toolbar">
      <button type="button" class="btn btn-sm btn-primary" @click="fileInput?.click()" :disabled="uploading">
        {{ uploading ? '上传中…' : '上传' }}
      </button>
      <button type="button" class="btn btn-sm btn-ghost" @click="openPicker" :disabled="libLoading">从附件库选</button>
      <button type="button" class="btn btn-sm btn-ghost" v-if="modelValue" @click="clear">移除</button>
    </div>
    <input
      :value="modelValue"
      placeholder="或粘贴图片 URL https://…"
      class="cover-url-input"
      @input="onUrlInput($event)"
    />
    <div class="cover-hint" v-if="hint">{{ hint }}</div>
    <input ref="fileInput" type="file" accept="image/*" style="display:none" @change="onFileSelect" />

    <!-- 附件库选择器 -->
    <div v-if="pickerShow" class="modal-overlay" @click.self="pickerShow = false">
      <div class="modal">
        <div class="modal-header">
          <h3>从附件库选择图片</h3>
          <button type="button" class="modal-close" @click="pickerShow = false">×</button>
        </div>
        <div class="modal-body">
          <div v-if="libLoading" class="modal-empty">加载中…</div>
          <div v-else-if="libList.length" class="attachment-grid">
            <div v-for="a in libList" :key="a.id" class="attachment-item" @click="pickFromLib(a)">
              <img v-if="isImage(a)" :src="a.urlPath" loading="lazy" />
              <div v-else class="attachment-noimg">{{ a.contentType || '文件' }}</div>
              <div class="attachment-name">{{ a.originalName }}</div>
            </div>
          </div>
          <div v-else class="modal-empty">附件库为空，请先到「附件库」页面上传图片</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { attachmentsApi, type Attachment } from '../api/attachments'
import { uploadFile } from '../api/client'

const props = withDefaults(defineProps<{
  modelValue: string
  hint?: string
}>(), { hint: '' })

const emit = defineEmits<{
  (e: 'update:modelValue', v: string): void
  (e: 'error', msg: string): void
}>()

const fileInput = ref<HTMLInputElement>()
const uploading = ref(false)
const broken = ref(false)
const dragover = ref(false)

const pickerShow = ref(false)
const libLoading = ref(false)
const libList = ref<Attachment[]>([])

function setUrl(v: string) {
  broken.value = false
  emit('update:modelValue', v)
}

function onUrlInput(e: Event) {
  setUrl((e.target as HTMLInputElement).value)
}

function clear() {
  setUrl('')
}

async function uploadImage(f: File) {
  uploading.value = true
  try {
    const att = await uploadFile('/api/admin/attachments/upload', f)
    setUrl(att.urlPath)
  } catch (err: any) {
    emit('error', '封面上传失败：' + err.message)
  } finally {
    uploading.value = false
  }
}

async function onFileSelect(e: Event) {
  const input = e.target as HTMLInputElement
  const f = input.files?.[0]
  if (f) await uploadImage(f)
  input.value = ''
}

function onDrop(e: DragEvent) {
  const f = Array.from(e.dataTransfer?.files || []).find(x => x.type.startsWith('image/'))
  if (f) uploadImage(f)
}

/** 页面内 Ctrl+V：剪贴板图片直接设为封面；在输入框粘贴文字、在富文本编辑器内时不拦截 */
function onGlobalPaste(e: ClipboardEvent) {
  const active = document.activeElement as HTMLElement | null
  if (active && active.isContentEditable) return
  const hasText = !!(e.clipboardData?.getData('text/plain') || '').trim()
  const isTextField = !!active && (active.tagName === 'INPUT' || active.tagName === 'TEXTAREA')
  if (hasText && isTextField) return
  for (const it of e.clipboardData?.items || []) {
    if (it.type.startsWith('image/')) {
      const f = it.getAsFile()
      if (f) {
        e.preventDefault()
        uploadImage(f)
      }
      return
    }
  }
}

async function openPicker() {
  pickerShow.value = true
  libLoading.value = true
  try {
    libList.value = await attachmentsApi.list()
  } catch (err: any) {
    emit('error', '附件列表加载失败：' + err.message)
  } finally {
    libLoading.value = false
  }
}

function pickFromLib(a: Attachment) {
  setUrl(a.urlPath)
  pickerShow.value = false
}

function isImage(a: Attachment) {
  return (a.contentType || '').startsWith('image/')
}

onMounted(() => document.addEventListener('paste', onGlobalPaste))
onBeforeUnmount(() => document.removeEventListener('paste', onGlobalPaste))
</script>
