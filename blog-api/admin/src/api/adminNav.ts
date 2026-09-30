import { api } from './client'

export interface AdminNav {
  id: number
  parentId?: number | null
  menuName: string
  path: string
  icon: string
  sortOrder: number
  visible: boolean
  createdAt?: string
}

export interface AdminNavRequest {
  parentId?: number | null
  menuName: string
  path: string
  icon?: string
  sortOrder?: number
  visible?: boolean
}

export const adminNavApi = {
  listVisible: () => api<AdminNav[]>('GET', '/api/admin/admin-nav'),
  listAll: () => api<AdminNav[]>('GET', '/api/admin/admin-nav/all'),
  create: (req: AdminNavRequest) => api<AdminNav>('POST', '/api/admin/admin-nav', req),
  update: (id: number, req: AdminNavRequest) => api<AdminNav>('PUT', `/api/admin/admin-nav/${id}`, req),
  delete: (id: number) => api('DELETE', `/api/admin/admin-nav/${id}`),
}
