import { api } from './client'

export interface SocialLink {
  id?: number
  platform: string
  label: string
  url: string
  icon?: string
  sortOrder: number
  visible: boolean
  createdAt?: string
  updatedAt?: string
}

export const socialLinksApi = {
  list: () => api<SocialLink[]>('GET', '/api/admin/social-links'),
  create: (s: SocialLink) => api<SocialLink>('POST', '/api/admin/social-links', s),
  update: (id: number, s: SocialLink) => api<SocialLink>('PUT', `/api/admin/social-links/${id}`, s),
  delete: (id: number) => api('DELETE', `/api/admin/social-links/${id}`),
}
