import assert from "node:assert/strict";
import { test } from "node:test";
import { extractToc } from "../composables/useToc.ts";

test("为 h1-h4 注入 id 并生成目录", () => {
  const { html, toc } = extractToc(
    "<h2>第一章 入门</h2><p>正文</p><h3>1.1 安装</h3>",
  );
  assert.ok(html.includes('id="第一章-入门"'));
  assert.ok(html.includes('id="11-安装"'));
  assert.deepEqual(
    toc.map((t) => ({ level: t.level, text: t.text })),
    [
      { level: 2, text: "第一章 入门" },
      { level: 3, text: "1.1 安装" },
    ],
  );
});

test("同名标题自动去重", () => {
  const { toc } = extractToc("<h2>总结</h2><h2>总结</h2><h2>总结</h2>");
  assert.deepEqual(toc.map((t) => t.id), ["总结", "总结-1", "总结-2"]);
});

test("覆盖已存在的 id 且忽略空标题", () => {
  const { html, toc } = extractToc('<h2 id="old">新标题</h2><h2>   </h2>');
  assert.ok(html.includes('id="新标题"'));
  assert.ok(!html.includes('id="old"'));
  assert.equal(toc.length, 1);
});

test("标题内的行内标签被剥离为纯文本", () => {
  const { toc } = extractToc("<h2>使用 <code>npm</code> 安装</h2>");
  assert.equal(toc[0]?.text, "使用 npm 安装");
});
