<template>
  <LayoutShell>
    <div class="topbar">
      <h2>菜单管理</h2>
      <div style="display:flex;gap:8px;align-items:center">
        <label style="font-weight:normal">菜单：</label>
        <select v-model="menuId" @change="reloadItems" style="width:200px">
          <option v-for="m in menus" :key="m.id" :value="m.id">{{ m.displayName }}{{ m.isPrimary ? '（主）' : '' }}</option>
        </select>
        <button class="btn btn-primary" @click="openNewItem">添加菜单项</button>
      </div>
    </div>
    <div class="msg msg-error" v-if="error">{{ error }}</div>

    <div class="card">
      <table>
        <thead><tr><th>名称</th><th>链接</th><th>排序</th><th>打开方式</th><th style="width:160px">操作</th></tr></thead>
        <tbody>
          <tr v-for="it in items" :key="it.id">
            <td>{{ it.displayName }}</td>
            <td style="color:#666">{{ it.href }}</td>
            <td>{{ it.priority }}</td>
            <td>{{ it.target === '_blank' ? '新窗口' : '当前页' }}</td>
            <td>
              <button class="btn btn-sm btn-ghost" @click="openEdit(it)">编辑</button>
              <button class="btn btn-sm btn-danger" @click="doDelete(it.id!)">删除</button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <div v-if="editing" style="position:fixed;inset:0;background:rgba(0,0,0,.4);display:flex;align-items:center;justify-content:center;z-index:100" @click.self="editing=null">
      <div class="card" style="width:460px">
        <h3 style="margin-bottom:12px">{{ form.id ? '编辑菜单项' : '新建菜单项' }}</h3>
        <div class="form-row"><label>显示名称</label><input v-model="form.displayName"></div>
        <div class="form-row"><label>链接（href，如 /about、/archives）</label><input v-model="form.href"></div>
        <div class="grid-2">
          <div class="form-row"><label>排序</label><input type="number" v-model.number="form.priority"></div>
          <div class="form-row"><label>打开方式</label>
            <select v-model="form.target">
              <option value="_self">当前页</option>
              <option value="_blank">新窗口</option>
            </select>
          </div>
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
import { api } from '../../api/client'

interface Menu { id?: number; displayName: string; isPrimary?: boolean }
interface Item { id?: number; menuId?: number; displayName: string; href: string; target?: string; priority?: number }

const menus = ref<Menu[]>([])
const menuId = ref<number>(0)
const items = ref<Item[]>([])
const editing = ref<any>(null)
const form = ref<Partial<Item>>({})
const error = ref('')

async function reloadMenus() {
  menus.value = await api<Menu[]>('GET', '/api/admin/menus')
  if (menus.value.length && !menuId.value) {
    menuId.value = menus.value.find(m => m.isPrimary)?.id || menus.value[0].id!
    reloadItems()
  }
}
async function reloadItems() {
  if (!menuId.value) return
  items.value = await api<Item[]>('GET', `/api/admin/menus/${menuId.value}/items`)
}
function openNewItem() {
  form.value = { menuId: menuId.value, priority: 0, target: '_self', displayName: '', href: '' }
  editing.value = form.value
}
function openEdit(it: Item) { form.value = { ...it }; editing.value = it }
async function save() {
  error.value = ''
  try {
    if (form.value.id) await api('PUT', `/api/admin/menu-items/${form.value.id}`, form.value)
    else await api('POST', '/api/admin/menu-items', form.value)
    editing.value = null; reloadItems()
  } catch (e: any) { error.value = e.message }
}
async function doDelete(id: number) {
  if (!confirm('删除该菜单项？')) return
  await api('DELETE', `/api/admin/menu-items/${id}`)
  reloadItems()
}
reloadMenus()
</script>
