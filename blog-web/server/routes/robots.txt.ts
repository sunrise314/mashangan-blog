export default defineEventHandler((event) => {
  const config = useRuntimeConfig(event);
  const siteUrl = trimSlash(config.public.siteUrl as string);
  const body = [
    "User-agent: *",
    "Allow: /",
    "Disallow: /admin",
    "Disallow: /api",
    "",
    "# AI 训练爬虫已全放行（GPTBot / ClaudeBot / PerplexityBot / Bytespider 等）",
    `Sitemap: ${siteUrl}/sitemap.xml`,
    "",
  ].join("\n");
  setHeader(event, "Content-Type", "text/plain; charset=utf-8");
  return body;
});
