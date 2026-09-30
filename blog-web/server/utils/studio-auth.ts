/** /studio 端点口令校验：接受 x-studio-token，与 ANALYTICS_PASSWORD / STUDIO_PASSWORD 任一匹配 */
export function checkStudioAuth(event: import("h3").H3Event): void {
  const studioPass = (process.env.STUDIO_PASSWORD || "").trim();
  const analyticsPass = (process.env.ANALYTICS_PASSWORD || "").trim();
  const token = String(getHeader(event, "x-studio-token") || "");
  if (!token || (token !== studioPass && token !== analyticsPass)) {
    throw createError({ statusCode: 401, statusMessage: "Unauthorized" });
  }
}
