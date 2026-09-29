/**
 * 白名单式 HTML 消毒器（零依赖，可在 SSR 与浏览器同构运行）。
 *
 * Halo 正文以 HTML 存储并通过 v-html 渲染，渲染前必须剥离脚本、事件属性与
 * 危险协议，防止存储型 XSS。采用「标签 + 属性」双白名单的状态机解析，
 * 不依赖浏览器 DOM；配套单测见 test/sanitizeHtml.test.ts。
 */

const ALLOWED_TAGS = new Set([
  "a",
  "img",
  "p",
  "br",
  "hr",
  "span",
  "div",
  "blockquote",
  "pre",
  "code",
  "h1",
  "h2",
  "h3",
  "h4",
  "h5",
  "h6",
  "ul",
  "ol",
  "li",
  "dl",
  "dt",
  "dd",
  "table",
  "thead",
  "tbody",
  "tr",
  "th",
  "td",
  "strong",
  "b",
  "em",
  "i",
  "u",
  "s",
  "del",
  "sub",
  "sup",
  "mark",
  "small",
  "figure",
  "figcaption",
  "kbd",
  "abbr",
  "cite",
  "q",
]);

/** 标签及其内容整体移除（防止残留脚本文本） */
const RAW_TEXT_TAGS = new Set([
  "script",
  "style",
  "iframe",
  "object",
  "embed",
  "noscript",
  "template",
]);

const VOID_TAGS = new Set(["br", "hr", "img"]);

const ALLOWED_ATTRS = new Set([
  "href",
  "src",
  "alt",
  "title",
  "class",
  "id",
  "target",
  "rel",
  "colspan",
  "rowspan",
  "loading",
  "referrerpolicy",
]);

/** 承载 URI 的属性需要做协议白名单校验 */
const URI_ATTRS = new Set(["href", "src"]);

const NAMED_ENTITIES: Record<string, string> = {
  amp: "&",
  lt: "<",
  gt: ">",
  quot: '"',
  apos: "'",
  colon: ":",
  semi: ";",
  backslash: "\\",
  tab: "\t",
  newline: "\n",
  sol: "/",
  equals: "=",
};

function decodeEntities(value: string): string {
  return value
    .replace(/&#x([0-9a-f]+);?/gi, (_, hex) => String.fromCodePoint(parseInt(hex, 16)))
    .replace(/&#(\d+);?/g, (_, dec) => String.fromCodePoint(parseInt(dec, 10)))
    .replace(/&([a-z]+);?/gi, (m, name) =>
      Object.prototype.hasOwnProperty.call(NAMED_ENTITIES, name.toLowerCase())
        ? NAMED_ENTITIES[name.toLowerCase()]
        : m,
    );
}

function isSafeUri(raw: string): boolean {
  const decoded = decodeEntities(raw)
    .trim()
    .replace(/[\x00-\x20]+/g, "");
  if (decoded.startsWith("/") || decoded.startsWith("#") || decoded === "") return true;
  return /^(https?:|mailto:)/i.test(decoded);
}

const ATTR_RE = /([a-zA-Z_:][-a-zA-Z0-9_:.]*)(?:\s*=\s*("([^"]*)"|'([^']*)'|([^\s"'`<]+)))?/g;

function filterAttrs(tagInner: string, tagName: string): string {
  const out: string[] = [];
  for (const m of tagInner.matchAll(ATTR_RE)) {
    let name = m[1].toLowerCase();
    // 声明在标签名之后的闭合斜杠不是属性
    if (name === "/") continue;
    const value = m[3] ?? m[4] ?? m[5] ?? "";
    // 事件处理器、style 等一律拒绝
    if (name.startsWith("on")) continue;
    if (!ALLOWED_ATTRS.has(name)) continue;
    if (URI_ATTRS.has(name) && !isSafeUri(value)) continue;

    if (value === "" && m[2] === undefined) {
      out.push(name);
    } else {
      const safe = value.replace(/"/g, "&quot;");
      out.push(`${name}="${safe}"`);
    }
  }
  // 外链新窗口强制 noopener
  if (
    tagName === "a" &&
    out.some((a) => a === 'target="_blank"') &&
    !out.some((a) => a.startsWith("rel="))
  ) {
    out.push('rel="noopener noreferrer"');
  }
  return out.length ? " " + out.join(" ") : "";
}

/** 核心消毒：返回安全 HTML 字符串 */
export function sanitizeHtml(html: string, allowed: Set<string> = ALLOWED_TAGS): string {
  if (!html) return "";
  let out = "";
  let i = 0;
  const n = html.length;

  while (i < n) {
    const lt = html.indexOf("<", i);
    if (lt === -1) {
      out += html.slice(i);
      break;
    }
    out += html.slice(i, lt);
    const next = html[lt + 1];

    // 注释 / DOCTYPE / CDATA 整体丢弃
    if (html.startsWith("<!--", lt)) {
      const end = html.indexOf("-->", lt + 4);
      i = end === -1 ? n : end + 3;
      continue;
    }
    if (next === "!" || next === "?") {
      const end = html.indexOf(">", lt);
      i = end === -1 ? n : end + 1;
      continue;
    }
    if (next !== "/" && !/[a-zA-Z]/.test(next ?? "")) {
      // 非标签的裸 < 原样保留
      out += "<";
      i = lt + 1;
      continue;
    }

    // 扫描到第一个 >；中途若再遇 < 说明是畸形/嵌套逃逸，整体当文本处理
    let j = lt + 1;
    let inQuote: string | null = null;
    let malformed = false;
    while (j < n) {
      const ch = html[j];
      if (inQuote) {
        if (ch === inQuote) inQuote = null;
      } else if (ch === '"' || ch === "'") {
        inQuote = ch;
      } else if (ch === ">") {
        break;
      } else if (ch === "<") {
        malformed = true;
        break;
      }
      j++;
    }
    if (malformed || j >= n) {
      out += "&lt;";
      i = lt + 1;
      continue;
    }

    const rawTag = html.slice(lt + 1, j);
    const isClose = rawTag.startsWith("/");
    const body = isClose ? rawTag.slice(1) : rawTag;
    const nameMatch = body.match(/^([a-zA-Z][a-zA-Z0-9-]*)/);
    const name = nameMatch ? nameMatch[1].toLowerCase() : "";
    i = j + 1;

    if (!name) {
      continue;
    }

    // 危险原始文本标签：连带内容删除
    if (RAW_TEXT_TAGS.has(name) && !isClose) {
      const close = new RegExp(`</${name}\\s*>`, "i").exec(html.slice(i));
      i = close ? i + close.index + close[0].length : n;
      continue;
    }

    if (!allowed.has(name)) {
      continue;
    }

    if (isClose) {
      out += `</${name}>`;
    } else {
      const selfClosing = /\/\s*$/.test(body);
      out += `<${name}${filterAttrs(body.slice(name.length), name)}${
        selfClosing || VOID_TAGS.has(name) ? " />" : ">"
      }`;
    }
  }
  return out;
}

/**
 * 搜索结果高亮字段消毒：服务端只注入 <B> 高亮标签，其余标签一律剔除。
 */
export function sanitizeHighlight(html: string): string {
  return sanitizeHtml(html, new Set(["b"]));
}
