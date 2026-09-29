/**
 * 站点配置：从同源 Nitro 接口 /api/site-config 拉取（SSR 友好，全站共享）。
 * 该接口在服务端用 PAT 读 Halo 系统设置（ConfigMap system），
 * 在 Halo Console → 系统 → 设置 里改动后，前台刷新即生效。
 */

export interface SocialLink {
  id: number;
  platform: string;
  label: string;
  url: string;
  iconClass: string;
  priority: number;
  enabled: boolean;
  createdAt: string;
}

export interface SiteConfig {
  title: string;
  subtitle: string;
  logoUrl: string;
  faviconUrl: string;
  footerText: string;
  beianIcp: string;
  beianPublicSecurity: string;
  seoDescription: string;
  seoKeywords: string;
  homepageTitle: string;
  homepageSubtitle: string;
  analyticsHeadCode: string;
  updatedAt: string;
}

export interface SiteConfigResponse {
  config: SiteConfig;
  socialLinks: SocialLink[];
}

export function useSiteConfig() {
  const { data } = useAsyncData<SiteConfigResponse | null>(
    "site-config",
    async () => {
      try {
        // 同源 Nitro 接口（server/api/site-config.get.ts），SSR/客户端均走相对路径
        const res = await $fetch<SiteConfigResponse>("/api/site-config", {
          headers: { Accept: "application/json" },
        });
        if (!res || typeof res !== "object" || !("config" in res)) return null;
        return res;
      } catch {
        return null;
      }
    },
    { default: () => null },
  );
  return data;
}
