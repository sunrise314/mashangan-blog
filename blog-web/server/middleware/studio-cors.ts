/**
 * /api/studio/* 跨域放行：Halo 控制台（:8090）内嵌的 AI 配图入口需要跨源调用本服务。
 * 接口本身有 x-studio-token 口令保护且不使用 cookie 凭据，动态回显 Origin 即可。
 */
export default defineEventHandler((event) => {
  const path = event.path || "";
  if (!path.startsWith("/api/studio")) return;

  const origin = getHeader(event, "origin") || "";
  if (origin) {
    setHeader(event, "Access-Control-Allow-Origin", origin);
    setHeader(event, "Access-Control-Allow-Methods", "GET, POST, OPTIONS");
    setHeader(event, "Access-Control-Allow-Headers", "content-type, x-studio-token");
    setHeader(event, "Access-Control-Max-Age", "86400");
    setHeader(event, "Vary", "Origin");
  }
  if (event.method === "OPTIONS") {
    event.node.res.statusCode = 204;
    return "";
  }
});
