/**
 * 解析文章 HTML 中的标题，生成 TOC 数据并给标题注入 id。
 * 用于详情页左侧目录锚点导航。
 */
export interface TocItem {
  id: string;
  text: string;
  level: number; // 1-4
}

/** 简易 slug：中文/数字/字母保留，其余转 - */
function slugify(text: string): string {
  return text
    .trim()
    .toLowerCase()
    .replace(/\s+/g, "-")
    .replace(/[^\w\u4e00-\u9fa5-]/g, "")
    .replace(/-+/g, "-")
    .replace(/^-|-$/g, "");
}

/**
 * 给 HTML 中所有 h1-h4 注入唯一 id，并返回 TOC 列表。
 * 纯字符串操作，兼容 SSR（无需 DOM）。
 */
export function extractToc(html: string): { html: string; toc: TocItem[] } {
  const toc: TocItem[] = [];
  const used = new Set<string>();
  let counter = 0;

  const newHtml = html.replace(
    /<(h[1-4])([^>]*)>([\s\S]*?)<\/\1>/gi,
    (_match, tag: string, attrs: string, inner: string) => {
      // 提取纯文本作为标题文字
      const text = inner.replace(/<[^>]+>/g, "").trim();
      if (!text) return `<${tag}${attrs}>${inner}</${tag}>`;

      let id = slugify(text);
      if (!id) id = `heading-${counter}`;
      // 去重
      let finalId = id;
      let n = 1;
      while (used.has(finalId)) {
        finalId = `${id}-${n}`;
        n++;
      }
      used.add(finalId);
      counter++;

      const level = parseInt(tag.charAt(1), 10);
      toc.push({ id: finalId, text, level });

      // 注入 id（若已有 id 则覆盖）
      const newAttrs = attrs.replace(/\sid="[^"]*"/i, "").replace(/\sid='[^']*'/i, "");
      return `<${tag}${newAttrs} id="${finalId}">${inner}</${tag}>`;
    },
  );

  return { html: newHtml, toc };
}
