import { api } from './client'
import type { PageResult } from './posts'

export interface Category {
  id?: number
  haloName?: string
  displayName: string
  slug: string
  cover?: string
  description?: string
  priority?: number
  hideFromList?: boolean
  parentHaloName?: string
  section?: string
  template?: string
  preventParentCascadeQuery?: boolean
  createdAt?: string
}

export interface CategoryPostSummary {
  id: number
  title: string
  slug: string
  published?: boolean
  pinned?: boolean
  updatedAt?: string
}

export const categoriesApi = {
  list: () => api<Category[]>('GET', '/api/admin/categories'),
  page: (page: number, size: number) =>
    api<PageResult<Category>>('GET', `/api/admin/categories/page?page=${page}&size=${size}`),
  get: (id: number) => api<Category>('GET', `/api/admin/categories/${id}`),
  posts: (id: number) => api<CategoryPostSummary[]>('GET', `/api/admin/categories/${id}/posts`),
  create: (c: Partial<Category>) => api<Category>('POST', '/api/admin/categories', c),
  update: (id: number, c: Partial<Category>) => api<Category>('PUT', `/api/admin/categories/${id}`, c),
  reorder: (ids: number[]) => api<void>('PUT', '/api/admin/categories/reorder', { ids }),
  delete: (id: number) => api('DELETE', `/api/admin/categories/${id}`),
}
