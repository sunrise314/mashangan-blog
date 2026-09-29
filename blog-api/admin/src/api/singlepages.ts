import { api } from './client'

export interface SinglePage {
  id?: number
  title: string
  slug: string
  content: string
  published: boolean
  visible: string
  allowComment: boolean
  createdAt?: string
  updatedAt?: string
}

export const singlePagesApi = {
  list: () => api<SinglePage[]>('GET', '/api/admin/singlepages'),
  get: (id: number) => api<SinglePage>('GET', `/api/admin/singlepages/${id}`),
  create: (p: SinglePage) => api<SinglePage>('POST', '/api/admin/singlepages', p),
  update: (id: number, p: SinglePage) => api<SinglePage>('PUT', `/api/admin/singlepages/${id}`, p),
  delete: (id: number) => api('DELETE', `/api/admin/singlepages/${id}`),
}
