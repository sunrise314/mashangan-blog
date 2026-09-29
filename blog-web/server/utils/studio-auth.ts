/** /studio 端点口令校验：header x-studio-token 必须与环境变量 STUDIO_PASSWORD 一致 */
export function checkStudioAuth(event: import("h3").H3Event): void {
  const password = (process.env.STUDIO_PASSWORD || "").trim();
  const token = String(getHeader(event, "x-studio-token") || "");
  if (!password || token !== password) {
    throw createError({ statusCode: 401, statusMessage: "Unauthorized" });
  }
}
