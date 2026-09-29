import { api } from './client'

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

export const categoriesApi = {
  list: () => api<Category[]>('GET', '/api/admin/categories'),
  get: (id: number) => api<Category>('GET', `/api/admin/categories/${id}`),
  create: (c: Partial<Category>) => api<Category>('POST', '/api/admin/categories', c),
  update: (id: number, c: Partial<Category>) => api<Category>('PUT', `/api/admin/categories/${id}`, c),
  delete: (id: number) => api('DELETE', `/api/admin/categories/${id}`),
}
