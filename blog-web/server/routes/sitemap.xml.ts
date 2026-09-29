interface HaloListItem {
  metadata: { creationTimestamp: string };
  spec: { slug: string; publishTime?: string };
  status?: { publishTime?: string; lastModifyTime?: string };
}

interface HaloPageResult {
  items: HaloListItem[];
  totalPages: number;
}

/** 翻页拉全量列表（与前台 useHaloApi.fetchAllPages 同逻辑，server 端独立实现） */
async function fetchAllPages(
  apiBase: string,
  path: string,
  pageSize = 100,
): Promise<HaloListItem[]> {
  const sep = path.includes("?") ? "&" : "?";
  const first = await $fetch<HaloPageResult>(
    `${apiBase}${path}${sep}size=${pageSize}&page=1`,
  );
  const items = [...first.items];
  for (let page = 2; page <= first.totalPages; page++) {
    const next = await $fetch<HaloPageResult>(
      `${apiBase}${path}${sep}size=${pageSize}&page=${page}`,
    );
    items.push(...next.items);
  }
  return items;
}

export default defineEventHandler(async (event) => {
  const config = useRuntimeConfig(event);
  const siteUrl = trimSlash(config.public.siteUrl as string);
  const apiBase = trimSlash(config.public.haloApiBase as string);
  const contentBase = "/apis/api.content.halo.run/v1alpha1";
  const now = new Date().toISOString();

  const urls: Array<{ loc: string; lastmod?: string }> = [
    { loc: `${siteUrl}/`, lastmod: now },
    { loc: `${siteUrl}/archives`, lastmod: now },
  ];

  try {
    const [posts, categories, pages] = await Promise.all([
      fetchAllPages(apiBase, `${contentBase}/posts`),
      fetchAllPages(apiBase, `${contentBase}/categories`, 200),
      fetchAllPages(apiBase, `${contentBase}/singlepages`),
    ]);

    posts.forEach((p) => {
      urls.push({
        loc: `${siteUrl}/archives/${encodeURIComponent(p.spec.slug)}`,
        lastmod: p.status?.lastModifyTime || p.status?.publishTime || p.metadata.creationTimestamp,
      });
    });
    categories.forEach((c) => {
      urls.push({
        loc: `${siteUrl}/categories/${encodeURIComponent(c.spec.slug)}`,
        lastmod: c.metadata.creationTimestamp,
      });
    });
    pages.forEach((p) => {
      urls.push({
        loc: `${siteUrl}/${encodeURIComponent(p.spec.slug)}`,
        lastmod: p.metadata.creationTimestamp,
      });
    });
  } catch (e) {
    // API 不可用时仍返回至少含首页的 sitemap，避免爬虫拿到 500
    console.error("[sitemap] fetch content failed:", e);
  }

  const body = [
    '<?xml version="1.0" encoding="UTF-8"?>',
    '<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">',
    ...urls.map(
      (u) =>
        `  <url><loc>${escapeXml(u.loc)}</loc>${
          u.lastmod ? `<lastmod>${escapeXml(u.lastmod)}</lastmod>` : ""
        }</url>`,
    ),
    "</urlset>",
  ].join("\n");

  setHeader(event, "Content-Type", "application/xml; charset=utf-8");
  return body;
});
