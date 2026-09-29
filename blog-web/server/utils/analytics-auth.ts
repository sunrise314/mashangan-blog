/** /admin 看板与 analytics 接口口令校验：header x-analytics-token 必须与环境变量 ANALYTICS_PASSWORD 一致 */
export function checkAnalyticsAuth(event: import("h3").H3Event): void {
  const password = (process.env.ANALYTICS_PASSWORD || "").trim();
  const token = String(getHeader(event, "x-analytics-token") || "");
  if (!password || token !== password) {
    throw createError({ statusCode: 401, statusMessage: "Unauthorized" });
  }
}
