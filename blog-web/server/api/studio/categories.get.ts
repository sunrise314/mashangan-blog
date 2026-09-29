/** GET /api/studio/categories：给 /studio 表单提供分类选择（走 Halo 公开 API） */
import { checkStudioAuth } from "../../utils/studio-auth";

interface CategoryItem {
  metadata: { name: string };
  spec: { displayName: string; slug: string; hideFromList?: boolean };
}

export default defineEventHandler(async (event) => {
  checkStudioAuth(event);
  const base = (process.env.HALO_API_BASE || "http://127.0.0.1:8090").replace(/\/$/, "");
  const resp = await fetch(
    `${base}/apis/api.content.halo.run/v1alpha1/categories?size=200&page=1`,
    {
      headers: { Accept: "application/json" },
      signal: AbortSignal.timeout(15_000),
    },
  );
  if (!resp.ok) {
    throw createError({ statusCode: 502, statusMessage: "获取分类失败" });
  }
  const data = (await resp.json()) as { items?: CategoryItem[] };
  const categories = (data.items || [])
    .filter((c) => c.spec && !c.spec.hideFromList)
    .map((c) => ({ name: c.metadata.name, displayName: c.spec.displayName, slug: c.spec.slug }));
  return { categories };
});
