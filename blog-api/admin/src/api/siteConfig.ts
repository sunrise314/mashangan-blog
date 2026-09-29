import { api } from './client'

export interface SiteConfig {
  id?: number
  title?: string
  subtitle?: string
  logoUrl?: string
  faviconUrl?: string
  footerText?: string
  beianIcp?: string
  beianPublicSecurity?: string
  seoDescription?: string
  seoKeywords?: string
  homepageTitle?: string
  homepageSubtitle?: string
  analyticsHeadCode?: string
  studioZhipuKey?: string
  studioPexelsKey?: string
  studioSiliconflowKey?: string
  updatedAt?: string
}

export const siteConfigApi = {
  get: () => api<SiteConfig>('GET', '/api/admin/site-config'),
  update: (c: SiteConfig) => api<SiteConfig>('PUT', '/api/admin/site-config', c),
}
