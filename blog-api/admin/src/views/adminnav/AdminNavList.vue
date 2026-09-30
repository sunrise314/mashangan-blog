<template>
  <LayoutShell>
    <div class="topbar">
      <h2>后台导航菜单</h2>
      <button class="btn btn-primary" @click="openNew">新建导航项</button>
    </div>
    <div class="msg msg-error" v-if="error">{{ error }}</div>

    <div class="card">
      <table>
        <thead>
          <tr>
            <th style="width:60px">排序</th>
            <th style="width:60px">图标</th>
            <th>名称</th>
            <th>路径</th>
            <th style="width:80px">类型</th>
            <th style="width:80px">可见</th>
            <th style="width:140px">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="n in list" :key="n.id">
            <td>{{ n.sortOrder }}</td>
            <td>{{ n.icon }}</td>
            <td>{{ n.menuName }}</td>
            <td style="color:#666;font-family:monospace;font-size:13px">{{ n.path }}</td>
            <td>
              <span :class="['tag', isSpaRoute(n.path) ? 'tag-spa' : 'tag-ext']">
                {{ isSpaRoute(n.path) ? 'SPA' : '外链' }}
              </span>
            </td>
            <td>
              <span :class="['dot', n.visible ? 'dot-on' : 'dot-off']"></span>
              {{ n.visible ? '显示' : '隐藏' }}
            </td>
            <td>
              <button class="btn btn-sm btn-ghost" @click="openEdit(n)">编辑</button>
              <button class="btn btn-sm btn-danger" @click="doDelete(n.id)">删除</button>
            </td>
          </tr>
          <tr v-if="!list.length">
            <td colspan="7" style="text-align:center;color:#999;padding:20px">暂无导航项</td>
          </tr>
        </tbody>
      </table>
    </div>

    <div v-if="editing" class="modal-mask" @click.self="editing = null">
      <div class="card modal-card">
        <h3 style="margin-bottom:12px">{{ form.id ? '编辑导航项' : '新建导航项' }}</h3>
        <div class="form-row">
          <label>显示名称 *</label>
          <input v-model="form.menuName" placeholder="如：文章管理">
        </div>
        <div class="form-row">
          <label>路由路径 *</label>
          <input v-model="form.path" placeholder="/admin/posts 或 /dashboard">
        </div>
        <div class="grid-2">
          <div class="form-row">
            <label>图标（emoji）</label>
            <input v-model="form.icon" placeholder="📝">
          </div>
          <div class="form-row">
            <label>排序（升序）</label>
            <input type="number" v-model.number="form.sortOrder">
          </div>
        </div>
        <div class="form-row">
          <label>
            <input type="checkbox" v-model="form.visible"> 在侧边栏显示
          </label>
        </div>
        <p class="hint">
          路径以 <code>/admin/</code> 开头会作为 SPA 内部路由（需要对应的 Vue 组件）；
          其他路径（如 <code>/dashboard</code>、<code>/</code>）按外部链接处理。
        </p>
        <div style="display:flex;gap:8px;justify-content:flex-end">
          <button class="btn btn-ghost" @click="editing = null">取消</button>
          <button class="btn btn-primary" @click="save">保存</button>
        </div>
      </div>
    </div>
  </LayoutShell>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import LayoutShell from '../../components/LayoutShell.vue'
import { adminNavApi, type AdminNav, type AdminNavRequest } from '../../api/adminNav'

const list = ref<AdminNav[]>([])
const editing = ref<any>(null)
const form = ref<AdminNav & AdminNavRequest>({} as any)
const error = ref('')

function isSpaRoute(path: string): boolean {
  return path.startsWith('/admin/')
}

async function reload() {
  try {
    list.value = await adminNavApi.listAll()
  } catch (e: any) {
    error.value = e.message
  }
}

function openNew() {
  form.value = { menuName: '', path: '', icon: '', sortOrder: 0, visible: true } as any
  editing.value = form.value
}

function openEdit(n: AdminNav) {
  form.value = { ...n } as any
  editing.value = n
}

async function save() {
  error.value = ''
  if (!form.value.menuName?.trim() || !form.value.path?.trim()) {
    error.value = '名称和路径不能为空'
    return
  }
  try {
    const req: AdminNavRequest = {
      parentId: form.value.parentId ?? null,
      menuName: form.value.menuName.trim(),
      path: form.value.path.trim(),
      icon: form.value.icon ?? '',
      sortOrder: form.value.sortOrder ?? 0,
      visible: form.value.visible ?? true,
    }
    if (form.value.id) {
      await adminNavApi.update(form.value.id, req)
    } else {
      await adminNavApi.create(req)
    }
    editing.value = null
    reload()
  } catch (e: any) {
    error.value = e.message
  }
}

async function doDelete(id: number) {
  if (!confirm('删除导航项？')) return
  try {
    await adminNavApi.delete(id)
    reload()
  } catch (e: any) {
    error.value = e.message
  }
}

reload()
</script>

<style scoped>
.tag {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 500;
}
.tag-spa { background: #dbeafe; color: #1e40af; }
.tag-ext { background: #f3f4f6; color: #6b7280; }
.dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  margin-right: 4px;
  vertical-align: middle;
}
.dot-on { background: #10b981; }
.dot-off { background: #d1d5db; }
.modal-mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, .4);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 100;
}
.modal-card {
  width: 480px;
  max-width: 90vw;
}
.hint {
  margin: 8px 0;
  padding: 8px 12px;
  background: #f9fafb;
  border-left: 3px solid #2563eb;
  color: #6b7280;
  font-size: 12px;
  line-height: 1.6;
}
.hint code {
  background: #e5e7eb;
  padding: 1px 4px;
  border-radius: 3px;
  font-size: 11px;
}
</style>
