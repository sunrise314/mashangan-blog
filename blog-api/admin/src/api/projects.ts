import { api } from './client'

/** 项目卡片面试考点 */
export interface ExamPoint {
  sort?: number
  point: string
  detail?: string
}

/** 项目卡片（/column/industry/... 页面数据源），seriesId 非空 = 已开更，null = 占位 */
export interface ProjectCard {
  id?: number
  industryId: number
  industryName?: string
  seriesId?: number | null
  seriesTitle?: string | null
  slug: string
  title: string
  jdFreq?: string
  summary?: string
  sort?: number
  visible?: boolean
  createdAt?: string
  examPoints?: ExamPoint[]
}

/** 行业（前台 /column/industry/{slug} 数据源） */
export interface Industry {
  id?: number
  slug: string
  name: string
  intro?: string
  sort?: number
  visible?: boolean
  createdAt?: string
  projectCount?: number
}

export const projectsApi = {
  list: () => api<ProjectCard[]>('GET', '/api/admin/projects'),
  create: (p: Partial<ProjectCard>) => api<ProjectCard>('POST', '/api/admin/projects', p),
  update: (id: number, p: Partial<ProjectCard>) => api<ProjectCard>('PUT', `/api/admin/projects/${id}`, p),
  remove: (id: number) => api<void>('DELETE', `/api/admin/projects/${id}`),
}

export const industriesApi = {
  list: () => api<Industry[]>('GET', '/api/admin/industries'),
  create: (i: Partial<Industry>) => api<Industry>('POST', '/api/admin/industries', i),
  update: (id: number, i: Partial<Industry>) => api<Industry>('PUT', `/api/admin/industries/${id}`, i),
  remove: (id: number) => api<void>('DELETE', `/api/admin/industries/${id}`),
}
