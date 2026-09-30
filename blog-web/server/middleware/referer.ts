/**
 * Nitro server middleware：在请求最开头捕获 Referer 头存到 event.context.referer。
 * Nuxt SSR plugin 执行时机晚，H3Event.headers 还没填充；
 * Nitro middleware 先执行，node.req.headers 完整可用。
 */
export default defineEventHandler((event) => {
  (event.context as any).referer = getHeader(event, "referer") || "";
});
