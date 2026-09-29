<template>
  <LayoutShell>
    <div class="topbar">
      <h2>文章</h2>
      <button class="btn btn-primary" @click="goNew">写文章</button>
    </div>

    <div class="card">
      <div class="toolbar">
        <input v-model="keyword" placeholder="搜索标题…" style="max-width:240px" @keyup.enter="reload" />
        <button class="btn btn-ghost" @click="reload">搜索</button>
        <button class="btn btn-ghost" @click="trashView = !trashView; reload">
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
            <td>{{ p.title || '(无标题)' }}</td>
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
        <button class="btn btn-ghost" :disabled="page <= 1" @click="page--;reload">上一页</button>
        <span style="font-size:13px;color:#666">第 {{ page }} / {{ totalPages }} 页</span>
        <button class="btn btn-ghost" :disabled="page >= totalPages" @click="page++;reload">下一页</button>
      </div>
    </div>
  </LayoutShell>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import LayoutShell from '../../components/LayoutShell.vue'
import { postsApi, type Post } from '../../api/posts'

const router = useRouter()
const records = ref<Post[]>([])
const page = ref(1)
const size = 20
const total = ref(0)
const totalPages = ref(1)
const keyword = ref('')
const trashView = ref(false)

async function reload() {
  const r = await postsApi.list(page.value, size, keyword.value || undefined, trashView.value)
  records.value = r.records
  total.value = r.total
  totalPages.value = r.pages || 1
}

function goNew() { router.push('/admin/posts/new') }
function goEdit(id: number) { router.push(`/admin/posts/${id}`) }

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
</script>
