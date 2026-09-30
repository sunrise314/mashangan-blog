<template>
  <div class="app-shell">
    <aside class="sidebar">
      <h1>博客后台</h1>
      <template v-for="n in navItems" :key="n.id">
        <RouterLink
          v-if="isSpaRoute(n.path)"
          :to="n.path"
          class="nav-item"
          active-class="active"
        >
          <span v-if="n.icon" class="nav-icon">{{ n.icon }}</span>
          <span>{{ n.menuName }}</span>
        </RouterLink>
        <a
          v-else
          :href="n.path"
          class="nav-item"
        >
          <span v-if="n.icon" class="nav-icon">{{ n.icon }}</span>
          <span>{{ n.menuName }}</span>
        </a>
      </template>
      <button class="nav-item logout" @click="doLogout">退出登录</button>
    </aside>
    <main class="main"><slot /></main>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuth } from '../composables/useAuth'
import { api } from '../api/client'

interface AdminNav {
  id: number
  menuName: string
  path: string
  icon: string
  sortOrder: number
  visible: boolean
}

const { logout } = useAuth()
const router = useRouter()

const navItems = ref<AdminNav[]>([])

/** 以 /admin/ 开头视为 SPA 内部路由，其他（如 /dashboard、/）按外部链接处理。 */
function isSpaRoute(path: string): boolean {
  return path.startsWith('/admin/')
}

async function loadNav() {
  try {
    navItems.value = await api<AdminNav[]>('GET', '/api/admin/admin-nav')
  } catch (e) {
    // 接口失败时回退到最小可用菜单，避免侧边栏空白
    navItems.value = [
      { id: 0, menuName: '文章管理', path: '/admin/posts', icon: '📝', sortOrder: 0, visible: true },
    ]
  }
}

onMounted(loadNav)

function doLogout() {
  logout()
  router.push('/admin/')
}
</script>

<style scoped>
.nav-icon {
  display: inline-block;
  width: 1.5em;
  text-align: center;
  margin-right: 0.25em;
}
</style>
