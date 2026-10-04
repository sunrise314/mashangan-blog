import { isoTime, postPath, type SeoPostItem } from "../../utils/seo";

interface HaloCategoryItem {
  metadata: { creationTimestamp: string };
  spec: { slug: string };
}

interface HaloSinglePageItem {
  metadata: { creationTimestamp: string };
  spec: { slug: string };
}

interface SeriesCardItem {
  slug: string;
}

interface SeriesDetailItem {
  slug: string;
  chapters: Array<{ slug: string }>;
}

interface HaloPageResult {
  items: Array<Record<string, unknown>>;
  totalPages: number;
}

/** 翻页拉全量列表（与前台 useHaloApi.fetchAllPages 同逻辑，server 端独立实现） */
async function fetchAllPages<T>(
  apiBase: string,
  path: string,
  pageSize = 100,
): Promise<T[]> {
  const sep = path.includes("?") ? "&" : "?";
  const first = await $fetch<HaloPageResult>(
    `${apiBase}${path}${sep}size=${pageSize}&page=1`,
  );
  const items = [...(first.items as T[])];
  for (let page = 2; page <= first.totalPages; page++) {
    const next = await $fetch<HaloPageResult>(
      `${apiBase}${path}${sep}size=${pageSize}&page=${page}`,
    );
    items.push(...(next.items as T[]));
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
    { loc: `${siteUrl}/column`, lastmod: now },
  ];

  try {
    const [posts, categories, pages, seriesCards] = await Promise.all([
      fetchAllPages<SeoPostItem>(apiBase, `${contentBase}/posts`),
      fetchAllPages<HaloCategoryItem>(apiBase, `${contentBase}/categories`, 200),
      fetchAllPages<HaloSinglePageItem>(apiBase, `${contentBase}/singlepages`),
      // 系列章节不在全局 /posts 列表中，须单独拉取（失败不影响其余部分）
      $fetch<SeriesCardItem[]>(`${apiBase}${contentBase}/series`).catch(() => [] as SeriesCardItem[]),
    ]);

    // 系列章节 → /column/{series}/{chapter}（付费墙页面有摘要，可被收录引导订阅）
    for (const s of seriesCards) {
      urls.push({ loc: `${siteUrl}/column/${encodeURIComponent(s.slug)}`, lastmod: now });
      try {
        const detail = await $fetch<SeriesDetailItem>(
          `${apiBase}${contentBase}/series/${encodeURIComponent(s.slug)}`,
        );
        for (const ch of detail.chapters ?? []) {
          urls.push({
            loc: `${siteUrl}/column/${encodeURIComponent(s.slug)}/${encodeURIComponent(ch.slug)}`,
            lastmod: now,
          });
        }
      } catch (e) {
        console.error(`[sitemap] fetch series ${s.slug} failed:`, e);
      }
    }

    // 普通文章 → 最终 URL（分类页 / 无分类的 /archives 本身就是最终页）
    for (const p of posts) {
      urls.push({
        loc: `${siteUrl}${postPath(p)}`,
        lastmod: isoTime(p.status?.lastModifyTime || p.status?.publishTime || p.metadata.creationTimestamp),
      });
    }
    for (const c of categories) {
      urls.push({
        loc: `${siteUrl}/categories/${encodeURIComponent(c.spec.slug)}`,
        lastmod: isoTime(c.metadata.creationTimestamp),
      });
    }
    for (const p of pages) {
      urls.push({
        loc: `${siteUrl}/${encodeURIComponent(p.spec.slug)}`,
        lastmod: isoTime(p.metadata.creationTimestamp),
      });
    }
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
