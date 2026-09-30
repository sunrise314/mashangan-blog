export default defineEventHandler((event) => {
  const config = useRuntimeConfig(event);
  const siteUrl = trimSlash(config.public.siteUrl as string);
  const body = [
    "User-agent: *",
    "Allow: /",
    "Disallow: /admin",
    "Disallow: /api",
    "",
    "# AI 训练爬虫：只屏蔽 Bytespider（字节，抓取最凶），放行 GPTBot / ClaudeBot / PerplexityBot 等",
    "User-agent: Bytespider",
    "Disallow: /",
    "",
    `Sitemap: ${siteUrl}/sitemap.xml`,
    "",
  ].join("\n");
  setHeader(event, "Content-Type", "text/plain; charset=utf-8");
  return body;
});
