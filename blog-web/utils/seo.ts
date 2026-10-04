/**
 * SEO 公共工具：sitemap / RSS / 推送端点共用的 URL 拼接逻辑。
 *
 * 背景：/archives/{slug} 只是 Halo 原生固定链接的 301 跳转入口
 * （系列章节 → /column/{series}/{slug}，有分类 → /categories/{cat}/{slug}，
 * 无分类 → 本页直接渲染）。sitemap/RSS 若输出 /archives/ 路径，
 * 爬虫每个 URL 都要多跟一跳 301，权重传递打折——因此出口一律给最终 URL。
 */

/** 列表接口（/posts）返回的文章项：足够拼最终 URL 的最小字段 */
export interface SeoPostItem {
  metadata: { name: string; creationTimestamp: string };
  spec: { slug: string; publishTime?: string };
  status?: { publishTime?: string; lastModifyTime?: string };
  /** 顶层 categories 为 CategoryVo 数组（与 spec.categories 的 name 列表不同） */
  categories?: Array<{ spec?: { slug?: string } }>;
}

/** 时间规范化为 ISO（秒精度），无效值返回 undefined */
export function isoTime(v: string | undefined | null): string | undefined {
  if (!v) return undefined;
  const d = new Date(v);
  return Number.isNaN(d.getTime()) ? undefined : d.toISOString();
}

/** 相对站内路径 → 绝对 URL（og:image 等协议要求绝对地址；已是 http(s) 原样返回） */
export function absolutizeUrl(url: string | undefined | null, siteUrl: string): string {
  if (!url) return "";
  if (/^https?:\/\//i.test(url)) return url;
  return `${siteUrl}${url.startsWith("/") ? "" : "/"}${url}`;
}

/**
 * 文章最终展示路径：有分类 → /categories/{cat}/{slug}，无分类 →
 * /archives/{slug}（该情形下就是最终页）。系列章节不在全局 /posts
 * 列表中，由 /series 接口单独输出 /column/{series}/{chapter}。
 */
export function postPath(post: SeoPostItem): string {
  const cat = post.categories?.[0]?.spec?.slug;
  const slug = encodeURIComponent(post.spec.slug);
  return cat
    ? `/categories/${encodeURIComponent(cat)}/${slug}`
    : `/archives/${slug}`;
}

/** JSON-LD 字符串安全内联：转义 < 防止 </script> 提前闭合（站点名等后台字段受控但仍防御） */
export function jsonLdSafe(data: unknown): string {
  return JSON.stringify(data).replace(/</g, "\\u003c");
}
