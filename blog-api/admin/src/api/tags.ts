import { api } from './client'

export interface Tag {
  id?: number
  name: string
  slug: string
  postCount?: number
  createdAt?: string
  updatedAt?: string
}

export const tagsApi = {
  list: () => api<Tag[]>('GET', '/api/admin/tags'),
  create: (t: Tag) => api<Tag>('POST', '/api/admin/tags', t),
  update: (id: number, t: Tag) => api<Tag>('PUT', `/api/admin/tags/${id}`, t),
  delete: (id: number) => api('DELETE', `/api/admin/tags/${id}`),
}
