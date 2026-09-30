/** POST /api/admin/ai-config/test/:name → 代理 blog-api 测试单个提供商连通性 */
import { blogApiAdmin } from "../../../utils/blog-api";
import { checkAnalyticsAuth } from "../../../utils/analytics-auth";

export default defineEventHandler(async (event) => {
  checkAnalyticsAuth(event);
  const name = getRouterParam(event, "name");
  if (!name) throw createError({ statusCode: 400, statusMessage: "缺少提供商名称" });
  return await blogApiAdmin("POST", `/api/admin/studio/ai-config/test/${name}`);
});
