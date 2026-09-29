<template>
  <LayoutShell>
    <div class="topbar">
      <h2>AI 一键配图发布</h2>
      <button class="btn btn-primary" :disabled="running" @click="submit">{{ running ? '已提交…' : '分析并配图发布' }}</button>
    </div>
    <div class="msg msg-error" v-if="error">{{ error }}</div>

    <div style="display:grid;grid-template-columns:1fr 300px;gap:16px;align-items:start">
      <div class="card">
        <div class="form-row"><label>文章 Markdown</label>
          <textarea v-model="markdown" rows="20" placeholder="粘贴文章 markdown 正文（不要带 frontmatter）"
            style="font-family:monospace;font-size:13px"></textarea>
        </div>
      </div>
      <div>
        <div class="card">
          <div class="form-row"><label>标题（留空自动取 H1）</label><input v-model="title"></div>
          <div class="form-row"><label>slug（留空自动从标题生成）</label><input v-model="slug"></div>
          <div class="form-row"><label>分类</label>
            <select v-model="categorySlug">
              <option value="">通用</option>
              <option v-for="c in categories" :key="c.haloName" :value="c.slug">{{ c.displayName }}</option>
            </select>
          </div>
          <div class="grid-2">
            <div class="form-row"><label>配图数量</label><input type="number" v-model.number="imageCount" min="1" max="10"></div>
            <div class="form-row"><label>风格</label><input v-model="style" placeholder="简约通用"></div>
          </div>
        </div>

        <div class="card" v-if="task">
          <h3 style="margin-bottom:8px">进度</h3>
          <div style="background:#eee;height:8px;border-radius:4px;overflow:hidden">
            <div :style="{width: task.percent + '%', background:'#2563eb', height:'100%', transition:'width .3s'}"></div>
          </div>
          <div style="margin-top:8px;color:#555;font-size:14px">{{ task.message }}</div>
          <div v-if="task.status === 'done'" style="margin-top:12px">
            <div class="msg msg-ok">发布成功：{{ task.title }}</div>
            <a class="btn btn-ghost" :href="task.permalink" target="_blank">查看文章</a>
          </div>
          <div v-if="task.status === 'error'" class="msg msg-error" style="margin-top:12px">{{ task.error }}</div>
          <ul v-if="task.warnings?.length" style="margin-top:8px;color:#b45309;font-size:13px">
            <li v-for="w in task.warnings">{{ w }}</li>
          </ul>
        </div>
      </div>
    </div>
  </LayoutShell>
</template>

<script setup lang="ts">
import { ref, onBeforeUnmount } from 'vue'
import LayoutShell from '../../components/LayoutShell.vue'
import { api } from '../../api/client'
import { categoriesApi, type Category } from '../../api/categories'

const markdown = ref('')
const title = ref('')
const slug = ref('')
const categorySlug = ref('')
const imageCount = ref(3)
const style = ref('简约通用')
const categories = ref<Category[]>([])
const running = ref(false)
const task = ref<any>(null)
const error = ref('')
let timer: any = null

async function submit() {
  error.value = ''
  if (!markdown.value.trim()) { error.value = '请粘贴文章内容'; return }
  running.value = true
  try {
    const t = await api<any>('POST', '/api/admin/studio/import', {
      markdown: markdown.value, title: title.value, slug: slug.value,
      categorySlug: categorySlug.value, imageCount: imageCount.value, style: style.value,
    })
    task.value = t
    poll(t.id)
  } catch (e: any) { error.value = e.message; running.value = false }
}

function poll(id: string) {
  timer = setInterval(async () => {
    try {
      const t = await api<any>('GET', `/api/admin/studio/status/${id}`)
      task.value = t
      if (t.status === 'done' || t.status === 'error') { clearInterval(timer); running.value = false }
    } catch (e: any) { error.value = e.message; clearInterval(timer); running.value = false }
  }, 1500)
}
onBeforeUnmount(() => timer && clearInterval(timer))

;(async () => { categories.value = await categoriesApi.list() })()
</script>
