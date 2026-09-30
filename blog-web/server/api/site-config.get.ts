/**
 * 站点配置（同源公开接口）：转发 blog-api 的 /api/public/site-config。
 * 后台「站点设置」保存的数据（标题/SEO/备案号/footer/社交链接等）经此到达前台。
 * 60s SWR 缓存：保存后最多 60s 生效；改代码重新部署后容器重建即清缓存。
 */
import { defineCachedEventHandler } from "#imports";
import { cdnizeDeep } from "../../utils/cdnize";

export default defineCachedEventHandler(
  async () => {
    const empty = { config: null, socialLinks: [] };
    // 缓存处理器的后台刷新会丢失请求上下文，useRuntimeConfig 不可靠；
    // 直接读容器环境变量（compose 注入，node 运行时真实可用）
    const base =
      process.env.BLOG_API_BASE || process.env.NUXT_PUBLIC_HALO_API_BASE || "";
    if (!base) return empty;

    try {
      const data = await $fetch(`${base}/api/public/site-config`, {
        headers: { Accept: "application/json" },
      });
      // logoUrl 等站内图片字段同样改写为 CDN（外链不受影响）
      return cdnizeDeep(data, (process.env.IMG_CDN_BASE || "").replace(/\/$/, ""));
    } catch {
      return empty;
    }
  },
  { maxAge: 60, swr: true, getKey: () => "site-config" },
);
