import { createRouter, createWebHistory } from 'vue-router'
import { getToken } from '../api/client'

const routes = [
  { path: '/admin/', component: () => import('../views/Login.vue'), meta: { public: true } },
  { path: '/admin/posts', component: () => import('../views/posts/PostList.vue') },
  { path: '/admin/posts/new', component: () => import('../views/posts/PostEdit.vue') },
  { path: '/admin/posts/:id', component: () => import('../views/posts/PostEdit.vue') },
  { path: '/admin/categories', component: () => import('../views/categories/CategoryList.vue') },
  // 项目专栏合并页：项目卡片 + 内容专栏 两个标签页（旧路径均保留可用）
  { path: '/admin/series', component: () => import('../views/column/ColumnManage.vue') },
  { path: '/admin/projects', component: () => import('../views/column/ColumnManage.vue') },
  { path: '/admin/industries', component: () => import('../views/column/ColumnManage.vue') },
  { path: '/admin/tags', component: () => import('../views/tags/TagList.vue') },
  { path: '/admin/singlepages', component: () => import('../views/singlepages/SinglePageList.vue') },
  { path: '/admin/singlepages/new', component: () => import('../views/singlepages/SinglePageEdit.vue') },
  { path: '/admin/singlepages/:id', component: () => import('../views/singlepages/SinglePageEdit.vue') },
  { path: '/admin/attachments', component: () => import('../views/attachments/AttachmentList.vue') },
  { path: '/admin/menus', component: () => import('../views/menus/MenuManager.vue') },
  { path: '/admin/site-config', component: () => import('../views/site-config/SiteConfig.vue') },
  { path: '/admin/password', component: () => import('../views/settings/PasswordChange.vue') },
  { path: '/admin/social-links', component: () => import('../views/social-links/SocialLinkList.vue') },
  { path: '/admin/studio', component: () => import('../views/studio/StudioSubmit.vue') },
  { path: '/admin/studio-config', component: () => import('../views/studio/AiConfig.vue') },
  { path: '/admin/seo-push', component: () => import('../views/seo/SeoPush.vue') },
  { path: '/admin/admin-nav', component: () => import('../views/adminnav/AdminNavList.vue') },
  { path: '/admin/dashboard', component: () => import('../views/dashboard/Dashboard.vue') },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

router.beforeEach((to, _from, next) => {
  if (to.meta.public || getToken()) next()
  else next('/admin/')
})

export default router
