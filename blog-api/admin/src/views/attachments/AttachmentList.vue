<template>
  <LayoutShell>
    <div class="topbar">
      <h2>附件库</h2>
      <button class="btn btn-primary" @click="fileInput?.click()">上传图片</button>
      <input ref="fileInput" type="file" accept="image/*" style="display:none" @change="onUpload">
    </div>

    <div class="msg msg-error" v-if="error">{{ error }}</div>

    <div class="card">
      <div v-if="list.length" class="grid-2" style="grid-template-columns:repeat(auto-fill,minmax(160px,1fr));gap:12px">
        <div v-for="a in list" :key="a.id" style="border:1px solid #eee;border-radius:6px;padding:8px;text-align:center">
          <img v-if="isImage(a)" :src="a.urlPath" style="width:100%;height:100px;object-fit:cover;border-radius:4px;background:#f1f5f9">
          <div style="font-size:12px;color:#666;margin-top:6px;word-break:break-all">{{ a.originalName }}</div>
          <div style="font-size:11px;color:#999">{{ formatSize(a.size) }}</div>
          <div style="margin-top:6px;display:flex;gap:4px;justify-content:center">
            <button class="btn btn-sm btn-ghost" @click="copyUrl(a.urlPath)">复制链接</button>
            <button class="btn btn-sm btn-danger" @click="doDelete(a.id!)">删除</button>
          </div>
        </div>
      </div>
      <div v-else style="text-align:center;color:#999;padding:40px 0">暂无附件</div>
    </div>
  </LayoutShell>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import LayoutShell from '../../components/LayoutShell.vue'
import { attachmentsApi, type Attachment } from '../../api/attachments'

const list = ref<Attachment[]>([])
const fileInput = ref<HTMLInputElement>()
const error = ref('')

async function reload() { list.value = await attachmentsApi.list() }

async function onUpload(e: Event) {
  const f = (e.target as HTMLInputElement).files?.[0]
  if (!f) return
  error.value = ''
  try {
    await attachmentsApi.upload(f)
    reload()
  } catch (err: any) { error.value = err.message }
}

async function doDelete(id: number) {
  if (!confirm('删除该附件？若仍被文章/页面/站点配置引用，将拒绝删除。')) return
  error.value = ''
  try {
    await attachmentsApi.delete(id)
    reload()
  } catch (err: any) { error.value = err.message }
}

function copyUrl(u: string) {
  navigator.clipboard.writeText(u)
}
function isImage(a: Attachment) { return (a.contentType || '').startsWith('image/') }
function formatSize(n: number) {
  if (n < 1024) return n + ' B'
  if (n < 1024 * 1024) return (n / 1024).toFixed(1) + ' KB'
  return (n / 1024 / 1024).toFixed(1) + ' MB'
}
reload()
</script>
