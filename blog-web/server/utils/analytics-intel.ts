/**
 * 访客情报解析：ip2region v2 xdb 离线归属地 + 手写 UA 解析 + 搜索引擎关键词提取。
 * 纯 JS 实现，无原生依赖，xdb 二进制格式（与官方 ip2region-ts 实现对齐）：
 *   头部 16B(version u16, policy u16, create_time u32, start_ptr u32, end_ptr u32)
 *   向量索引 offset 256 起，256×256 行 × 8B(start_ptr u32, end_ptr u32)，按 IP 前两个字节索引
 *   索引块 14B(start_ip u32, end_ip u32, data_len u16, data_ptr u32)
 *   数据区 UTF-8 字符串直接从 data_ptr 读取 data_len 字节「国家|区域|省|市|ISP」
 */

const VECTOR_INDEX_OFFSET = 256;
const INDEX_BLOCK_SIZE = 14;

/** 在 xdb 字节流中二分查找 IP 归属地，返回「国家|区域|省|市|ISP」原始串 */
export function searchXdbRaw(xdb: Uint8Array, ip: string): string {
  const ipInt = ipv4ToInt(ip);
  if (ipInt === null) return "";
  const dv = new DataView(xdb.buffer, xdb.byteOffset, xdb.byteLength);
  // 向量索引共 65536 行（512KB），行号 = 首字节×256 + 次字节
  if (xdb.byteLength < VECTOR_INDEX_OFFSET + 256 * 256 * 8) return "";

  const vIdx = VECTOR_INDEX_OFFSET + (((ipInt >>> 24) & 0xff) * 256 + ((ipInt >>> 16) & 0xff)) * 8;
  const sPtr = dv.getUint32(vIdx, true);
  const ePtr = dv.getUint32(vIdx + 4, true);
  if (ePtr < sPtr || ePtr + INDEX_BLOCK_SIZE > xdb.byteLength) return "";

  let l = 0;
  let r = Math.floor((ePtr - sPtr) / INDEX_BLOCK_SIZE);
  let dataLen = 0;
  let dataPtr = 0;
  while (l <= r) {
    const m = (l + r) >> 1;
    const off = sPtr + m * INDEX_BLOCK_SIZE;
    if (off + INDEX_BLOCK_SIZE > xdb.byteLength) return "";
    const sip = dv.getUint32(off, true);
    if (ipInt < sip) {
      r = m - 1;
      continue;
    }
    const eip = dv.getUint32(off + 4, true);
    if (ipInt > eip) {
      l = m + 1;
      continue;
    }
    dataLen = dv.getUint16(off + 8, true);
    dataPtr = dv.getUint32(off + 10, true);
    break;
  }
  if (!dataPtr || dataLen <= 0 || dataPtr + dataLen > xdb.byteLength) return "";
  return new TextDecoder().decode(xdb.subarray(dataPtr, dataPtr + dataLen));
}

/** 「中国|0|广东省|深圳市|电信」→ { region: "中国 广东省 深圳市", isp: "电信" } */
export function formatRegion(raw: string): { region: string; isp: string } {
  if (!raw) return { region: "", isp: "" };
  const seg = raw.split("|");
  const parts = [seg[0], seg[2], seg[3]].filter((s) => s && s !== "0");
  const region = [...new Set(parts)].join(" ");
  const isp = seg[4] && seg[4] !== "0" ? seg[4] : "";
  return { region, isp };
}

function ipv4ToInt(ip: string): number | null {
  const parts = ip.split(".");
  if (parts.length !== 4) return null;
  let n = 0;
  for (const p of parts) {
    const v = Number(p);
    if (!Number.isInteger(v) || v < 0 || v > 255) return null;
    n = ((n << 8) | v) >>> 0;
  }
  return n;
}

let _xdb: Uint8Array | null = null;

/** 加载内置 ip2region.xdb（Nitro serverAssets，失败时回退进程工作目录） */
export async function loadXdb(): Promise<Uint8Array | null> {
  if (_xdb) return _xdb;
  try {
    const raw = await useStorage("assets:data").getItemRaw("ip2region.xdb");
    if (raw && raw.byteLength > 0) {
      _xdb = raw;
      return _xdb;
    }
  } catch {
    // dev 环境无 serverAssets 时走 fs 回退
  }
  try {
    const fs = await import("node:fs");
    const path = await import("node:path");
    _xdb = fs.readFileSync(path.resolve(process.cwd(), "server/assets/ip2region.xdb"));
    return _xdb;
  } catch {
    return null;
  }
}

/** IP → 归属地 + ISP（xdb 未加载或解析失败返回空对象） */
export async function searchIpRegion(ip: string): Promise<{ region: string; isp: string }> {
  const xdb = await loadXdb();
  if (!xdb) return { region: "", isp: "" };
  return formatRegion(searchXdbRaw(xdb, ip));
}

/** 从请求头提取客户端真实 IP（nginx 反代链路优先 x-forwarded-for 首段） */
export function getClientIp(event: import("h3").H3Event): string {
  const xff = String(getHeader(event, "x-forwarded-for") || "");
  const first = xff.split(",")[0].trim();
  if (first) return first;
  const xr = String(getHeader(event, "x-real-ip") || "").trim();
  if (xr) return xr;
  const addr = String(event.node?.req?.socket?.remoteAddress || "");
  return addr.replace(/^::ffff:/, "");
}

/** 手写 UA 解析：浏览器 / 操作系统 / 设备类型（含爬虫识别） */
export function parseUa(ua: string): { browser: string; os: string; device: string } {
  const u = ua || "";
  if (
    /Bot|Crawler|Spider|slurp|bingbot|Googlebot|Baiduspider|YisouSpider|Sogou .*spider|DuckDuckBot|python-requests|curl\/|Go-http-client|Java\/|Wget|HeadlessChrome|Lighthouse|Postman/i.test(
      u,
    )
  ) {
    return { browser: "-", os: "-", device: "爬虫/程序" };
  }
  let browser = "其他";
  if (/MicroMessenger/i.test(u)) browser = "微信内置";
  else if (/Alipay/i.test(u)) browser = "支付宝内置";
  else if (/Edg(e|A|iOS)?\//i.test(u)) browser = "Edge";
  else if (/QQBrowser\//i.test(u)) browser = "QQ浏览器";
  else if (/QQ\//i.test(u)) browser = "QQ内置";
  else if (/UCBrowser\//i.test(u)) browser = "UC浏览器";
  else if (/MetaSr|SogouMobileBrowser/i.test(u)) browser = "搜狗浏览器";
  else if (/BIDUBrowser|baiduboxapp|BaiduHD/i.test(u)) browser = "百度App";
  else if (/LBBROWSER/i.test(u)) browser = "猎豹";
  else if (/Maxthon/i.test(u)) browser = "傲游";
  else if (/OPR\/|Opera/i.test(u)) browser = "Opera";
  else if (/Vivaldi/i.test(u)) browser = "Vivaldi";
  else if (/Firefox\//i.test(u)) browser = "Firefox";
  else if (/Chrome\//i.test(u)) browser = "Chrome";
  else if (/Safari\//i.test(u)) browser = "Safari";
  else if (/MSIE|Trident/i.test(u)) browser = "IE";

  let os = "其他";
  if (/HarmonyOS/i.test(u)) os = "鸿蒙";
  else if (/Windows NT 10/i.test(u)) os = "Windows 10/11";
  else if (/Windows NT 6\.1/i.test(u)) os = "Windows 7";
  else if (/Windows/i.test(u)) os = "Windows";
  else if (/iPad/i.test(u)) os = "iPadOS";
  else if (/iPhone|iPod/i.test(u)) os = "iOS";
  else if (/Android/i.test(u)) {
    const m = u.match(/Android ([\d]+)/i);
    os = m ? `Android ${m[1]}` : "Android";
  } else if (/Mac OS X/i.test(u)) os = "macOS";
  else if (/Linux/i.test(u)) os = "Linux";

  let device = "PC";
  if (/iPad|Tablet/i.test(u)) device = "平板";
  else if (/Mobi|Android|iPhone|iPod/i.test(u)) device = "手机";

  return { browser, os, device };
}

const SEARCH_ENGINES: Array<{ host: RegExp; params: string[] }> = [
  { host: /baidu\.com$/i, params: ["wd", "word", "kw", "q"] },
  { host: /bing\.com$/i, params: ["q"] },
  { host: /google\./i, params: ["q"] },
  { host: /sogou\.com$/i, params: ["query", "kw", "q"] },
  { host: /so\.com$/i, params: ["q"] },
  { host: /sm\.cn$/i, params: ["q"] },
  { host: /yandex\./i, params: ["text"] },
  { host: /duckduckgo\./i, params: ["q"] },
];

/** 来源 URL → 搜索关键词（百度 wd / 必应与谷歌 q 等），非搜索引擎返回空串 */
export function parseKw(ref: string): string {
  if (!ref) return "";
  let u: URL;
  try {
    u = new URL(ref);
  } catch {
    return "";
  }
  const hit = SEARCH_ENGINES.find((e) => e.host.test(u.hostname));
  if (!hit) return "";
  for (const p of hit.params) {
    const v = u.searchParams.get(p);
    if (v) return v.trim().slice(0, 100);
  }
  return "";
}
