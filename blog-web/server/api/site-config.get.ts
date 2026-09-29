/**
 * 站点配置（同源公开接口）：服务端用 PAT 读 Halo 系统设置 ConfigMap，
 * 转成前台 SiteConfigResponse 形状。
 * 用户在 Halo Console「系统 → 设置 / 代码注入」的改动经此生效（60s 缓存）。
 */
import { defineCachedEventHandler } from "#imports";

interface HaloConfigMap {
  data?: Record<string, string>;
  metadata?: { version?: number };
}

function safeParse(raw: string | undefined): Record<string, any> {
  if (!raw) return {};
  try {
    const v = JSON.parse(raw);
    return v && typeof v === "object" ? v : {};
  } catch {
    return {};
  }
}

export default defineCachedEventHandler(
  async () => {
    const empty = { config: null, socialLinks: [] };
    // 缓存处理器的后台刷新会丢失请求上下文，useRuntimeConfig 不可靠；
    // 直接读容器环境变量（compose 注入，node 运行时真实可用）
    const base = process.env.NUXT_PUBLIC_HALO_API_BASE || "http://49.235.136.65:8090";
    const pat = process.env.HALO_PAT || "";
    if (!base || !pat) return empty;

    try {
      const cm = await $fetch<HaloConfigMap>(`${base}/api/v1alpha1/configmaps/system`, {
        headers: { Authorization: `Bearer ${pat}` },
      });
      const data = cm?.data ?? {};
      const basic = safeParse(data.basic);
      const seo = safeParse(data.seo);
      const injection = safeParse(data.codeInjection);
      return {
        config: {
          title: basic.title ?? "",
          subtitle: basic.subtitle ?? "",
          logoUrl: basic.logo ?? "",
          faviconUrl: basic.favicon ?? "",
          footerText: "",
          beianIcp: "",
          beianPublicSecurity: "",
          seoDescription: seo.description ?? "",
          seoKeywords: seo.keywords ?? "",
          homepageTitle: "",
          homepageSubtitle: "",
          analyticsHeadCode: injection.globalHead ?? "",
          updatedAt: cm?.metadata?.version ? new Date().toISOString() : "",
        },
        socialLinks: [],
      };
    } catch {
      return empty;
    }
  },
  { maxAge: 60, swr: true, getKey: () => "site-config" },
);
