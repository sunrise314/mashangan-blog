/**
 * 服务端侧：把首次请求的 Referer 头写入 payload 传给客户端。
 * Referer 头由 server/middleware/referer.ts 更早捕获并存入 event.context.referer。
 * 站点 meta 带 no-referrer，浏览器 document.referrer 恒为空，
 * 搜索引擎来源只能靠 SSR 捕获请求头。
 */
export default defineNuxtPlugin((nuxtApp) => {
  if (import.meta.server) {
    const ctx = (nuxtApp as any).ssrContext;
    (nuxtApp.payload as Record<string, unknown>).ssrRef = ctx?.event?.context?.referer || "";
  }
});
