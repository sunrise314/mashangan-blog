import { api } from './client'

/** 项目/系列（/column 项目卡片的数据源），对应后端 Series 实体 */
export interface Series {
  id?: number
  slug: string
  title: string
  cover?: string
  description?: string
  /** updating（连载中）| complete（已完结）——注意与分类的 completed 拼写不同 */
  status?: 'updating' | 'complete'
  /** 免费章节数，0 = 全部免费 */
  freeChapterCount?: number
  sortOrder?: number
  createdAt?: string
  updatedAt?: string
}

export const seriesApi = {
  list: () => api<Series[]>('GET', '/api/admin/series'),
  get: (id: number) => api<Series>('GET', `/api/admin/series/${id}`),
  create: (s: Partial<Series>) => api<Series>('POST', '/api/admin/series', s),
  update: (id: number, s: Partial<Series>) => api<Series>('PUT', `/api/admin/series/${id}`, s),
  delete: (id: number) => api<void>('DELETE', `/api/admin/series/${id}`),
}
