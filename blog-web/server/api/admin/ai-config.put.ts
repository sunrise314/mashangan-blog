/** PUT /api/admin/ai-config → 代理 blog-api 更新 AI 提供商 Key */
import { blogApiAdmin } from "../../utils/blog-api";
import { checkAnalyticsAuth } from "../../utils/analytics-auth";

export default defineEventHandler(async (event) => {
  checkAnalyticsAuth(event);
  const body = await readBody(event);
  return await blogApiAdmin("PUT", "/api/admin/studio/ai-config", body);
});
