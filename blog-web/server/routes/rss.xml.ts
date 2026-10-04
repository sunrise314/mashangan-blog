import { cdnizeText } from "../../utils/cdnize";
import { postPath, type SeoPostItem } from "../../utils/seo";

interface SeriesCardItem {
  slug: string;
}

interface SeriesDetailItem {
  slug: string;
  chapters: Array<{ slug: string; free: boolean }>;
}

export default defineEventHandler(async (event) => {
  const config = useRuntimeConfig(event);
  const siteUrl = trimSlash(config.public.siteUrl as string);
  const apiBase = trimSlash(config.public.haloApiBase as string);
  const siteTitle = (config.public.siteTitle as string) || "码上岸";
  const contentBase = "/apis/api.content.halo.run/v1alpha1";

  type RssEntry = { title: string; link: string; excerpt: string; pubDate: string };

  let entries: RssEntry[] = [];
  try {
    const result = await $fetch<{ items: SeoPostItem[] }>(
      `${apiBase}${contentBase}/posts?size=50&page=1`,
    );
    entries = (result.items ?? []).map((p) => ({
      title: p.spec.title,
      link: `${siteUrl}${postPath(p)}`,
      excerpt: p.status?.excerpt || "",
      pubDate: p.status?.publishTime
        ? new Date(p.status.publishTime).toUTCString()
        : "",
    }));

    // 免费章节并入 RSS（锁定章节不进公开订阅流）：
    // 系列章节不在全局 /posts 列表，须经 /series → /series/{slug} → by-slug 逐个补数据
    const seriesCards = await $fetch<SeriesCardItem[]>(`${apiBase}${contentBase}/series`);
    for (const s of seriesCards) {
      try {
        const detail = await $fetch<SeriesDetailItem>(
          `${apiBase}${contentBase}/series/${encodeURIComponent(s.slug)}`,
        );
        for (const ch of (detail.chapters ?? []).filter((c) => c.free)) {
          try {
            const post = await $fetch<SeoPostItem & { status?: { excerpt?: string; publishTime?: string } }>(
              `${apiBase}${contentBase}/posts/by-slug/${encodeURIComponent(ch.slug)}`,
            );
            entries.push({
              title: post.spec.title,
              link: `${siteUrl}/column/${encodeURIComponent(s.slug)}/${encodeURIComponent(ch.slug)}`,
              excerpt: post.status?.excerpt || "",
              // 系列章节 status.publishTime 可能为空，用 creationTimestamp 兜底参与排序
              pubDate: new Date(
                post.status?.publishTime || post.metadata?.creationTimestamp || Date.now(),
              ).toUTCString(),
            });
          } catch (e) {
            console.error(`[rss] fetch chapter ${ch.slug} failed:`, e);
          }
        }
      } catch (e) {
        console.error(`[rss] fetch series ${s.slug} failed:`, e);
      }
    }

    // 并入系列章节后按发布时间重排，仍取最新 50 条
    entries.sort((a, b) => {
      const ta = a.pubDate ? Date.parse(a.pubDate) : 0;
      const tb = b.pubDate ? Date.parse(b.pubDate) : 0;
      return tb - ta;
    });
    entries = entries.slice(0, 50);
  } catch (e) {
    console.error("[rss] fetch posts failed:", e);
  }

  const items = entries
    .map((en) =>
      [
        "    <item>",
        `      <title>${escapeXml(en.title)}</title>`,
        `      <link>${escapeXml(en.link)}</link>`,
        `      <guid isPermaLink="true">${escapeXml(en.link)}</guid>`,
        `      <description>${escapeXml(
        cdnizeText(en.excerpt, (process.env.IMG_CDN_BASE || "").replace(/\/$/, "")),
      )}</description>`,
        en.pubDate ? `      <pubDate>${en.pubDate}</pubDate>` : "",
        "    </item>",
      ]
        .filter(Boolean)
        .join("\n"),
    )
    .join("\n");

  const body = [
    '<?xml version="1.0" encoding="UTF-8"?>',
    '<rss version="2.0">',
    "  <channel>",
    `    <title>${escapeXml(siteTitle)}</title>`,
    `    <link>${escapeXml(siteUrl)}/</link>`,
    `    <description>${escapeXml(siteTitle)}最新文章</description>`,
    `    <lastBuildDate>${new Date().toUTCString()}</lastBuildDate>`,
    items,
    "  </channel>",
    "</rss>",
  ].join("\n");

  setHeader(event, "Content-Type", "application/rss+xml; charset=utf-8");
  return body;
});
