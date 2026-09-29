export default defineNuxtConfig({
  // 生产构建关闭 devtools（避免暴露调试入口与多余代码）
  devtools: { enabled: process.env.NODE_ENV !== "production" },
  modules: ["@nuxtjs/tailwindcss"],
  css: ["~/assets/css/main.css"],
  runtimeConfig: {
    public: {
      haloApiBase: process.env.HALO_API_BASE || "http://49.235.136.65:8090",
      // 抬头站名与 Logo（可用环境变量 SITE_TITLE / SITE_LOGO 覆盖）
      siteTitle: process.env.SITE_TITLE || "码上岸",
      siteLogo: process.env.SITE_LOGO || "",
      // 前台对外可访问的站点根地址，用于生成 sitemap/RSS 绝对链接（SITE_URL 覆盖）
      siteUrl: process.env.SITE_URL || "http://49.235.136.65:3000",
    },
  },
  app: {
    head: {
      htmlAttrs: { lang: "zh-CN" },
      title: "码上岸",
      meta: [
        { charset: "utf-8" },
        { name: "viewport", content: "width=device-width, initial-scale=1" },
        // 文章图片托管在原站图床，其 CDN 按 Referer 防盗链（外站 Referer 返回 403）；
        // 不发送 Referer 时可正常访问，封面与正文图片均依赖此设置
        { name: "referrer", content: "no-referrer" },
        {
          name: "description",
          content:
            "码上岸：系统化的 Java 编程技术教程、Spring Boot 实战、MySQL/Redis 中间件与 Java 面试八股文汇总。",
        },
      ],
    },
  },
  nitro: {
    // 内置服务端静态资源：ip2region 离线 IP 库（访客归属地解析）
    serverAssets: [{ baseName: "data", dir: "./server/assets" }],
    routeRules: {
      // SSR + 增量静态再验证：回源失败时仍可返回旧缓存，避免 Halo 抖动导致全站 500
      "/": { swr: 60 },
      "/archives": { swr: 60 },
      "/archives/**": { swr: 3600 },
      "/categories/**": { swr: 120 },
      "/java-interview": { swr: 120 },
      "/java-interview/**": { swr: 300 },
      "/tools": { swr: 300 },
      "/column": { swr: 120 },
      "/zsxq": { swr: 300 },
      "/search": { swr: 30 },
      // 内部工具页：禁止索引
      "/studio": { headers: { "X-Robots-Tag": "noindex, nofollow" } },
      "/studio/**": { headers: { "X-Robots-Tag": "noindex, nofollow" } },
      // 自建后台（访客看板）：纯客户端渲染，禁止索引
      "/admin": { ssr: false, headers: { "X-Robots-Tag": "noindex, nofollow" } },
      "/admin/**": { ssr: false, headers: { "X-Robots-Tag": "noindex, nofollow" } },
    },
  },
});
