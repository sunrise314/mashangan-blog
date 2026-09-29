<template>
  <div class="app-shell">
    <aside class="sidebar">
      <h1>博客后台</h1>
      <RouterLink v-for="n in navItems" :key="n.path" :to="n.path" class="nav-item" active-class="active">{{ n.label }}</RouterLink>
      <button class="nav-item logout" @click="doLogout">退出登录</button>
    </aside>
    <main class="main"><slot /></main>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import { useAuth } from '../composables/useAuth'

const { logout } = useAuth()
const router = useRouter()

const navItems = [
  { path: '/admin/studio', label: 'AI 一键发文' },
  { path: '/admin/posts', label: '文章' },
  { path: '/admin/categories', label: '分类' },
  { path: '/admin/tags', label: '标签' },
  { path: '/admin/singlepages', label: '单页' },
  { path: '/admin/attachments', label: '附件' },
  { path: '/admin/menus', label: '导航菜单' },
  { path: '/admin/site-config', label: '站点设置' },
  { path: '/admin/social-links', label: '社交链接' },
]

function doLogout() {
  logout()
  router.push('/admin/')
}
</script>
