import { api } from './client'

export interface Post {
  id?: number
  title: string
  slug: string
  cover: string
  excerpt: string
  content: string  // Markdown 源文
  categories: string[]  // haloName 数组
  tags: string[]
  published: boolean
  pinned: boolean
  priority: number
  visible: string
  allowComment: boolean
  updatedAt?: string
  createdAt?: string
}

export interface PageResult<T> { records: T[]; total: number; pages: number }

export const postsApi = {
  list: (page: number, size: number, keyword?: string, deleted = false) => {
    const q = keyword ? `&keyword=${encodeURIComponent(keyword)}` : ''
    return api<PageResult<Post>>(
      'GET', `/api/admin/posts?page=${page}&size=${size}${q}&deleted=${deleted}`,
    )
  },
  get: (id: number) => api<Post>('GET', `/api/admin/posts/${id}`),
  create: (p: Post) => api<Post>('POST', '/api/admin/posts', p),
  update: (id: number, p: Post) => api<Post>('PUT', `/api/admin/posts/${id}`, p),
  delete: (id: number) => api('DELETE', `/api/admin/posts/${id}`),
  restore: (id: number) => api('POST', `/api/admin/posts/${id}/restore`),
  purge: (id: number) => api('DELETE', `/api/admin/posts/${id}/purge`),
}
