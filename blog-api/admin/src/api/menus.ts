import { api } from './client'

export interface Menu {
  id?: number
  name: string
  slug: string
  createdAt?: string
  updatedAt?: string
}

export interface MenuItem {
  id?: number
  menuId: number
  parentId?: number | null
  label: string
  url: string
  target?: string
  sortOrder: number
}

export const menusApi = {
  list: () => api<Menu[]>('GET', '/api/admin/menus'),
  create: (m: Menu) => api<Menu>('POST', '/api/admin/menus', m),
  update: (id: number, m: Menu) => api<Menu>('PUT', `/api/admin/menus/${id}`, m),
  delete: (id: number) => api('DELETE', `/api/admin/menus/${id}`),
  items: (menuId: number) => api<MenuItem[]>('GET', `/api/admin/menus/${menuId}/items`),
  createItem: (item: MenuItem) => api<MenuItem>('POST', '/api/admin/menu-items', item),
  updateItem: (id: number, item: MenuItem) => api<MenuItem>('PUT', `/api/admin/menu-items/${id}`, item),
  deleteItem: (id: number) => api('DELETE', `/api/admin/menu-items/${id}`),
}
