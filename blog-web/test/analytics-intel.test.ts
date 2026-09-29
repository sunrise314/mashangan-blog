import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { join } from "node:path";
import { searchXdbRaw, formatRegion, parseUa, parseKw } from "../server/utils/analytics-intel.ts";

const xdb = readFileSync(join(import.meta.dirname, "../server/assets/ip2region.xdb"));

test("xdb 头部格式校验（v2, version=2）", () => {
  assert.equal(xdb.readUInt16LE(0), 2, "version 应为 2");
  assert.ok(xdb.byteLength > 10_000_000, "完整 xdb 应大于 10MB");
});

test("xdb 归属地查找：国内 IP", () => {
  const raw = searchXdbRaw(xdb, "114.114.114.114");
  assert.ok(raw.includes("中国"), `114.114.114.114 应解析为中国，实际: ${raw}`);
  const { region } = formatRegion(raw);
  assert.ok(region.startsWith("中国"));
  // 223.5.5.5 阿里 DNS 应带 ISP 信息
  const raw2 = searchXdbRaw(xdb, "223.5.5.5");
  assert.ok(raw2.length > 0, `223.5.5.5 应有解析结果，实际: ${raw2}`);
});

test("xdb 归属地查找：海外 IP", () => {
  const raw = searchXdbRaw(xdb, "8.8.8.8");
  assert.ok(raw.includes("美国"), `8.8.8.8 应解析为美国，实际: ${raw}`);
});

test("xdb 归属地查找：边界与非法输入", () => {
  assert.equal(searchXdbRaw(xdb, "1.2.3"), "");
  assert.equal(searchXdbRaw(xdb, "a.b.c.d"), "");
  assert.equal(searchXdbRaw(xdb, ""), "");
  assert.equal(searchXdbRaw(xdb, "256.1.1.1"), "");
  assert.equal(searchXdbRaw(xdb, "::1"), "");
});

test("formatRegion 过滤 0 段", () => {
  assert.deepEqual(formatRegion("中国|0|广东省|深圳市|电信"), {
    region: "中国 广东省 深圳市",
    isp: "电信",
  });
  assert.deepEqual(formatRegion("美国|0|0|0|"), { region: "美国", isp: "" });
  assert.deepEqual(formatRegion(""), { region: "", isp: "" });
});

test("UA 解析：桌面 Chrome", () => {
  const ua =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36";
  assert.deepEqual(parseUa(ua), { browser: "Chrome", os: "Windows 10/11", device: "PC" });
});

test("UA 解析：iPhone Safari", () => {
  const ua =
    "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1";
  const r = parseUa(ua);
  assert.equal(r.browser, "Safari");
  assert.equal(r.os, "iOS");
  assert.equal(r.device, "手机");
});

test("UA 解析：安卓微信内置", () => {
  const ua =
    "Mozilla/5.0 (Linux; Android 14; PJD110) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36 MicroMessenger/8.0.47";
  const r = parseUa(ua);
  assert.equal(r.browser, "微信内置");
  assert.equal(r.os, "Android 14");
  assert.equal(r.device, "手机");
});

test("UA 解析：爬虫识别", () => {
  assert.equal(parseUa("Mozilla/5.0 (compatible; Baiduspider/2.0)").device, "爬虫/程序");
  assert.equal(parseUa("curl/8.4.0").device, "爬虫/程序");
  assert.equal(parseUa("python-requests/2.31").device, "爬虫/程序");
});

test("搜索引擎关键词提取", () => {
  assert.equal(
    parseKw("https://www.baidu.com/s?wd=halo%20%E5%8D%9A%E5%AE%A2&ie=utf-8"),
    "halo 博客",
  );
  assert.equal(parseKw("https://www.bing.com/search?q=nuxt3+%E6%95%99%E7%A8%8B"), "nuxt3 教程");
  assert.equal(parseKw("https://www.google.com/search?q=spring+boot"), "spring boot");
  assert.equal(parseKw("https://example.com/page?wd=not-engine"), "");
  assert.equal(parseKw("not a url"), "");
  assert.equal(parseKw(""), "");
});
