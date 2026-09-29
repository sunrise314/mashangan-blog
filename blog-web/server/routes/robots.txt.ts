export default defineEventHandler((event) => {
  const config = useRuntimeConfig(event);
  const siteUrl = trimSlash(config.public.siteUrl as string);
  const body = [
    "User-agent: *",
    "Allow: /",
    "Disallow: /studio",
    "Disallow: /admin",
    "Disallow: /api",
    `Sitemap: ${siteUrl}/sitemap.xml`,
    "",
  ].join("\n");
  setHeader(event, "Content-Type", "text/plain; charset=utf-8");
  return body;
});
