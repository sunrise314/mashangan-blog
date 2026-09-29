/**
 * 服务端侧：把首次请求的 Referer 头写入 payload 传给客户端。
 * 站点 meta 带 no-referrer，浏览器端 document.referrer 恒为空，
 * 搜索引擎来源只能靠 SSR 捕获请求头。
 */
export default defineNuxtPlugin((nuxtApp) => {
  if (import.meta.server) {
    const headers = useRequestHeaders(["referer"]);
    (nuxtApp.payload as Record<string, unknown>).ssrRef = headers.referer || "";
  }
});
