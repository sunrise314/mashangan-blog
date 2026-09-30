/**
 * 图片 CDN 改写器：把数据里的站内图片地址改写为 CDN 绝对地址。
 *
 * 背景：cdn.mashangan.com 已接入腾讯云 CDN（回源本站 nginx，见 _md2html/CDN配置指南.md）。
 * 数据出口（useHaloApi / site-config / rss）返回前统一改写，覆盖 SSR 首屏与
 * 客户端 SPA 导航两条取数路径；改写发生在渲染之前，sanitizeHtml 白名单放行 https:。
 *
 * 开关：环境变量 IMG_CDN_BASE（生产 compose 注入 https://cdn.mashangan.com，
 * 经 runtimeConfig.public.imgCdnBase 到达客户端；服务端直接读容器 env）。
 * 为空时全部函数原样返回（no-op），回退 = 删除 env 并重建容器，不动数据库不改代码。
 *
 * 注意：base 的解析由调用方在 setup 同步阶段完成（await 之后 nuxt 上下文会丢失），
 * 本文件只提供纯改写函数，保持同构可用。
 */

/**
 * 改写单个字符串里的图片地址。三条规则按序执行、幂等：
 * 1. 旧绝对域名形态（api.mashangan.com）
 * 2. 旧 IP:8090 形态（早期迁移数据）
 * 3. 相对路径形态 —— 前一字符不能是字母数字或 : / . ，避免二次命中
 *    已带域名的 URL（cdn.mashangan.com[/]upload）与 data:/https: 协议
 */
export function cdnizeText(s: string, base: string): string {
  if (!base || !s || !s.includes("/upload/")) return s;
  return s
    .replace(/https?:\/\/api\.mashangan\.com\/upload\//g, `${base}/upload/`)
    .replace(/https?:\/\/49\.235\.136\.65(?::\d+)?\/upload\//g, `${base}/upload/`)
    .replace(/(^|[^A-Za-z0-9:./])\/upload\//g, `$1${base}/upload/`);
}

/**
 * 深度遍历 JSON 数据，对所有字符串值做图片地址改写。
 * /upload/ 仅用于静态图片路径，站内链接/API 路径均不含此前缀，误伤面为零。
 * 输入为 $fetch 反序列化的纯 JSON，无循环引用风险。
 */
export function cdnizeDeep<T>(value: T, base: string): T {
  if (!base) return value;
  if (typeof value === "string") return cdnizeText(value, base) as unknown as T;
  if (Array.isArray(value)) {
    return value.map((v) => cdnizeDeep(v, base)) as unknown as T;
  }
  if (value && typeof value === "object") {
    const out: Record<string, unknown> = {};
    for (const [k, v] of Object.entries(value)) {
      out[k] = cdnizeDeep(v, base);
    }
    return out as T;
  }
  return value;
}
