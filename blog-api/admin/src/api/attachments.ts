import { api, uploadFile } from './client'

export interface Attachment {
  id?: number
  haloName?: string
  storagePath: string
  urlPath: string
  originalName: string
  contentType: string
  size: number
  createdAt?: string
}

export const attachmentsApi = {
  list: () => api<Attachment[]>('GET', '/api/admin/attachments'),
  delete: (id: number) => api('DELETE', `/api/admin/attachments/${id}`),
  upload: (file: File) => uploadFile('/api/admin/attachments/upload', file),
}
