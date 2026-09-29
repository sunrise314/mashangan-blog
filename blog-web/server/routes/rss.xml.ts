interface RssPost {
  spec: { title: string; slug: string };
  status?: { excerpt?: string; publishTime?: string };
}

interface RssPageResult {
  items: RssPost[];
}

export default defineEventHandler(async (event) => {
  const config = useRuntimeConfig(event);
  const siteUrl = trimSlash(config.public.siteUrl as string);
  const apiBase = trimSlash(config.public.haloApiBase as string);
  const siteTitle = (config.public.siteTitle as string) || "码上岸";
  const contentBase = "/apis/api.content.halo.run/v1alpha1";

  let posts: RssPost[] = [];
  try {
    const result = await $fetch<RssPageResult>(
      `${apiBase}${contentBase}/posts?size=50&page=1`,
    );
    posts = result.items ?? [];
  } catch (e) {
    console.error("[rss] fetch posts failed:", e);
  }

  const items = posts
    .map((p) => {
      const link = `${siteUrl}/archives/${encodeURIComponent(p.spec.slug)}`;
      const pubDate = p.status?.publishTime
        ? new Date(p.status.publishTime).toUTCString()
        : "";
      return [
        "    <item>",
        `      <title>${escapeXml(p.spec.title)}</title>`,
        `      <link>${escapeXml(link)}</link>`,
        `      <guid isPermaLink="true">${escapeXml(link)}</guid>`,
        `      <description>${escapeXml(p.status?.excerpt || "")}</description>`,
        pubDate ? `      <pubDate>${pubDate}</pubDate>` : "",
        "    </item>",
      ]
        .filter(Boolean)
        .join("\n");
    })
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
