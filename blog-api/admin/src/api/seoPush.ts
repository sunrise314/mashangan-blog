import { api } from './client'

export interface SeoPushConfig {
  siteUrl: string
  gscResource: string
  baiduToken: string
  indexnowKey: string
}

export interface PushResult {
  ok: boolean
  message: string
}

export interface RecentUrls {
  items: Array<{ title: string; url: string }>
}

export const seoApi = {
  config: () => api<SeoPushConfig>('GET', '/api/admin/seo/push/config'),
  /** 只提交 SEO 相关 4 个字段，站点设置其余字段不受影响（后端按非空字段 patch） */
  saveConfig: (c: SeoPushConfig) => api<SeoPushConfig>('PUT', '/api/admin/site-config', {
    seoSiteUrl: c.siteUrl,
    seoGscResource: c.gscResource,
    seoBaiduToken: c.baiduToken,
    seoIndexnowKey: c.indexnowKey,
  }),
  recent: (limit: number) => api<RecentUrls>('GET', `/api/admin/seo/push/recent?limit=${limit}`),
  pushBaidu: (urls: string[]) => api<PushResult>('POST', '/api/admin/seo/push/baidu', { urls }),
  pushIndexnow: (urls: string[]) => api<PushResult>('POST', '/api/admin/seo/push/indexnow', { urls }),
}
