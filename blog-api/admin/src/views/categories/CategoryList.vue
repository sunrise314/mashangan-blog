<template>
  <LayoutShell>
    <div class="topbar">
      <h2>分类管理</h2>
      <button class="btn btn-primary" @click="openNew">新建分类</button>
    </div>

    <div class="msg msg-error" v-if="error">{{ error }}</div>
    <div class="msg msg-ok" v-if="saved">{{ saved }}</div>

    <div class="card">
      <table>
        <thead>
          <tr><th>名称</th><th>slug</th><th>栏目分区</th><th>隐藏列表</th><th style="width:140px">操作</th></tr>
        </thead>
        <tbody>
          <tr v-for="c in list" :key="c.id">
            <td>{{ c.displayName }}</td>
            <td style="color:#666">{{ c.slug }}</td>
            <td>
              <span v-if="c.section" class="badge badge-on">{{ c.section }}</span>
              <span v-else style="color:#aaa">通用</span>
            </td>
            <td><span class="badge" :class="c.hideFromList ? 'badge-off' : 'badge-on'">{{ c.hideFromList ? '隐藏' : '显示' }}</span></td>
            <td>
              <button class="btn btn-sm btn-ghost" @click="openEdit(c)">编辑</button>
              <button class="btn btn-sm btn-danger" @click="doDelete(c.id!)">删除</button>
            </td>
          </tr>
        </tbody>
      </table>
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
import LayoutShell from '../../components/LayoutShell.vue'
import { categoriesApi, type Category } from '../../api/categories'

const list = ref<Category[]>([])
const editing = ref<any>(null)
const form = ref<Partial<Category>>({})
const error = ref('')
const saved = ref('')

async function reload() { list.value = await categoriesApi.list() }

function openNew() {
  form.value = { displayName: '', slug: '', priority: 0, hideFromList: false, section: '' }
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
  reload()
}
reload()
</script>
