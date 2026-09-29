import { proxyRequest } from "h3";

/**
 * /apis/** 同源反代 → Halo。
 * nginx 80 端口已有同款 location，但用户常直接访问 :3000（直连 halo-web 容器），
 * 客户端 SPA 导航的 /apis/ 请求在 3000 上无人代理会 404（独立页/文章列表全挂）。
 * 复用 SSR 同一 base（public.haloApiBase），保证任意端口下客户端请求同源可用。
 */
export default defineEventHandler((event) => {
  const base = useRuntimeConfig(event).public.haloApiBase as string;
  return proxyRequest(event, base + event.path);
});
