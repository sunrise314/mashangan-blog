/** GET /api/studio/status/:id：轮询任务进度 */
import { checkStudioAuth } from "../../../utils/studio-auth";
import { getTask } from "../../../utils/studio-task";

export default defineEventHandler((event) => {
  checkStudioAuth(event);
  const id = getRouterParam(event, "id") || "";
  const task = getTask(id);
  if (!task) {
    throw createError({ statusCode: 404, statusMessage: "任务不存在或已过期" });
  }
  return task;
});
