/** GET /api/admin/ai-config → 代理 blog-api 获取 AI 提供商状态 */
import { blogApiAdmin } from "../../utils/blog-api";
import { checkAnalyticsAuth } from "../../utils/analytics-auth";

export default defineEventHandler(async (event) => {
  checkAnalyticsAuth(event);
  return await blogApiAdmin("GET", "/api/admin/studio/ai-config");
});
