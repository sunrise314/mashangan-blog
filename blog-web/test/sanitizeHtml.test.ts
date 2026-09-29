import assert from "node:assert/strict";
import { test } from "node:test";
import { sanitizeHtml, sanitizeHighlight } from "../utils/sanitizeHtml.ts";

test("移除 script 标签及其内容", () => {
  assert.equal(
    sanitizeHtml('<p>正文</p><script>alert("x")</script><p>尾部</p>'),
    "<p>正文</p><p>尾部</p>",
  );
});

test("移除内联事件属性", () => {
  const out = sanitizeHtml('<img src="/a.png" onerror="alert(1)" onclick="x()" alt="a">');
  assert.ok(!out.includes("onerror"));
  assert.ok(!out.includes("onclick"));
  assert.ok(out.includes('src="/a.png"'));
  assert.ok(out.includes('alt="a"'));
});

test("拦截 javascript: 协议（含实体编码变形）", () => {
  assert.equal(sanitizeHtml('<a href="javascript:alert(1)">x</a>'), "<a>x</a>");
  assert.equal(sanitizeHtml('<a href="java&colon;script:alert(1)">x</a>'), "<a>x</a>");
  assert.equal(sanitizeHtml('<a href="  jAvAsCrIpT:alert(1)">x</a>'), "<a>x</a>");
});

test("保留安全的 http/相对路径/锚点链接与图片", () => {
  const out = sanitizeHtml(
    '<a href="https://a.com/x">x</a><a href="/archives/s">y</a><img src="#z">',
  );
  assert.ok(out.includes('href="https://a.com/x"'));
  assert.ok(out.includes('href="/archives/s"'));
  assert.ok(out.includes('src="#z"'));
});

test("外链 target=_blank 自动补 rel=noopener", () => {
  const out = sanitizeHtml('<a href="https://a.com" target="_blank">x</a>');
  assert.ok(out.includes('rel="noopener noreferrer"'));
});

test("移除注释、iframe 与未知标签但保留其文本", () => {
  assert.equal(sanitizeHtml("<p>a<!-- secret -->b</p>"), "<p>ab</p>");
  assert.equal(
    sanitizeHtml('<p>前</p><iframe src="https://evil"></iframe><p>后</p>'),
    "<p>前</p><p>后</p>",
  );
  // 未知标签脱壳，内容保留
  assert.equal(sanitizeHtml("<custom>文字</custom>"), "文字");
});

test("畸形嵌套标签不作为标签解析", () => {
  const out = sanitizeHtml("<scr<script>ipt>alert(1)</script>");
  assert.ok(!out.includes("alert(1)"));
});

test("保留 Markdown 表格结构", () => {
  const html =
    '<table><thead><tr><th>A</th></tr></thead><tbody><tr><td colspan="2">B</td></tr></tbody></table>';
  assert.equal(sanitizeHtml(html), html);
});

test("空输入安全返回", () => {
  assert.equal(sanitizeHtml(""), "");
});

test("sanitizeHighlight 只保留 b 高亮标签", () => {
  assert.equal(sanitizeHighlight("Docker <B>镜像</B><script>x</script>"), "Docker <b>镜像</b>");
});
