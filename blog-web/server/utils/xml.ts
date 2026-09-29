/** XML 特殊字符转义，用于 sitemap / RSS 生成 */
export function escapeXml(s: string | undefined | null): string {
  if (!s) return "";
  return String(s)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&apos;");
}

/** 去掉尾部斜杠，便于拼接 URL */
export function trimSlash(s: string): string {
  return s.replace(/\/+$/, "");
}
