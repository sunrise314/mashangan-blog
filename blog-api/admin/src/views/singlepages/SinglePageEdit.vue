<template>
  <LayoutShell>
    <div class="topbar">
      <h2>{{ isNew ? '新建页面' : '编辑页面' }}</h2>
      <div style="display:flex;gap:8px">
        <button class="btn btn-ghost" @click="$router.back()">返回</button>
        <button class="btn btn-primary" :disabled="saving" @click="save">{{ saving ? '保存中…' : '保存' }}</button>
      </div>
    </div>
    <div class="msg msg-error" v-if="error">{{ error }}</div>
    <div class="msg msg-ok" v-if="saved">已保存</div>

    <div class="card">
      <div class="form-row"><label>标题</label><input v-model="form.title" style="font-size:18px;font-weight:600"></div>
      <div class="form-row"><label>slug</label><input v-model="form.slug"></div>
      <div class="form-row"><label>内容（Markdown）</label>
        <textarea v-model="form.content" rows="18" style="font-family:monospace;font-size:13px"></textarea>
      </div>
      <div class="form-row"><label style="display:flex;align-items:center;gap:6px;font-weight:normal">
        <input type="checkbox" style="width:auto" v-model="form.published"> 发布
      </label></div>
    </div>
  </LayoutShell>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import LayoutShell from '../../components/LayoutShell.vue'
import { api } from '../../api/client'

const route = useRoute()
const router = useRouter()
const id = route.params.id ? Number(route.params.id) : null
const isNew = !id

const form = ref({ title: '', slug: '', content: '', published: false })
const saving = ref(false)
const error = ref('')
const saved = ref(false)

async function save() {
  error.value = ''; saved.value = false; saving.value = true
  try {
    if (isNew) await api('POST', '/api/admin/singlepages', form.value)
    else await api('PUT', `/api/admin/singlepages/${id}`, form.value)
    saved.value = true
    router.push('/admin/singlepages')
  } catch (e: any) { error.value = e.message }
  finally { saving.value = false }
}
;(async () => {
  if (id) {
    const p = await api<any>('GET', `/api/admin/singlepages/${id}`)
    form.value = { title: p.title, slug: p.slug, content: p.content || '', published: !!p.published }
  }
})()
</script>
