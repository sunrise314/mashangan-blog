/** POST /api/studio/import：提交 md，创建一键配图任务，返回 taskId 供轮询 */
import { checkStudioAuth } from "../../utils/studio-auth";
import { startImport } from "../../utils/studio-task";

interface ImportBody {
  markdown?: string;
  title?: string;
  slug?: string;
  categorySlug?: string;
  imageCount?: number;
  style?: string;
}

const MAX_MD_LENGTH = 600_000; // 超长文由流水线采样分析，不在此截断

export default defineEventHandler(async (event) => {
  checkStudioAuth(event);
  const body = await readBody<ImportBody>(event);
  const markdown = (body?.markdown || "").trim();
  if (!markdown) {
    throw createError({ statusCode: 400, statusMessage: "请输入文章内容" });
  }
  if (markdown.length > MAX_MD_LENGTH) {
    throw createError({
      statusCode: 413,
      statusMessage: `文章过长（>${MAX_MD_LENGTH} 字符），请拆分后导入`,
    });
  }
  if (!/^[a-zA-Z0-9\u4e00-\u9fa5_-]+$/.test(body?.slug || "")) {
    delete body!.slug; // 非法 slug 交给服务端自动生成
  }
  const task = startImport({
    markdown,
    title: body?.title?.slice(0, 120),
    slug: body?.slug?.slice(0, 80),
    categorySlug: body?.categorySlug?.slice(0, 80),
    imageCount: Number(body?.imageCount) || 3,
    style: body?.style?.slice(0, 30),
  });
  return { taskId: task.id };
});
