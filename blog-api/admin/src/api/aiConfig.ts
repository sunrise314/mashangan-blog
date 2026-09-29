import { api } from './client'

export interface ProviderStatus {
  name: string
  displayName: string
  configured: boolean
  keyMasked: string
  balance: string | null
  rateLimit: string | null
  error: string | null
}

export interface AiKeyPatch {
  zhipuKey?: string
  pexelsKey?: string
  siliconflowKey?: string
}

export const aiConfigApi = {
  list: () => api<ProviderStatus[]>('GET', '/api/admin/studio/ai-config'),
  test: (name: string) => api<ProviderStatus>('POST', `/api/admin/studio/ai-config/test/${name}`),
  updateKeys: (patch: AiKeyPatch) => api<ProviderStatus[]>('PUT', '/api/admin/studio/ai-config', patch),
}
