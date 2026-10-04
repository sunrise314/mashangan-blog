/**
 * SEO 主动推送端点：把 URL 列表推给 IndexNow（Bing/Yandex 等即时收录）
 * 与百度站长平台（api.indexnow.org 兜底各引擎，百度需单独 token）。
 *
 * 用法（发布流程收尾或手动触发）：
 *   curl -X POST https://www.mashangan.com/api/seo/ping \
 *     -H "Content-Type: application/json" \
 *     -H "x-ping-token: $SEO_PING_TOKEN" \
 *     -d '{"urls":["https://www.mashangan.com/column/starlab/starlab-05-handover"]}'
 *
 * 环境变量（compose 注入）：
 *   SEO_PING_TOKEN   调用方鉴权 token（未设置时端点拒绝所有请求）
 *   INDEXNOW_KEY     IndexNow key，须与 public/<key>.txt 文件名一致（public 文件已随构建部署）
 *   BAIDU_PUSH_TOKEN 百度站长平台普通收录 token（未设置时跳过百度）
 */

interface PingBody {
  urls?: string[];
}

export default defineEventHandler(async (event) => {
  const token = process.env.SEO_PING_TOKEN || "";
  if (!token || getHeader(event, "x-ping-token") !== token) {
    throw createError({ statusCode: 401, statusMessage: "unauthorized" });
  }

  const config = useRuntimeConfig(event);
  const siteUrl = trimSlash(config.public.siteUrl as string);
  const body = await readBody<PingBody>(event).catch(() => ({}) as PingBody);

  // 只接受本站 URL，防滥用为代理推送
  const urls = (body.urls ?? [])
    .filter((u): u is string => typeof u === "string" && u.startsWith(`${siteUrl}/`))
    .slice(0, 100);
  if (urls.length === 0) {
    throw createError({ statusCode: 400, statusMessage: "no valid urls" });
  }

  const result: Record<string, string> = {};

  // IndexNow：key 文件位于站点根 https://site/<key>.txt
  const indexnowKey = process.env.INDEXNOW_KEY || "";
  if (indexnowKey) {
    try {
      await $fetch("https://api.indexnow.org/indexnow", {
        method: "POST",
        headers: { "Content-Type": "application/json; charset=utf-8" },
        body: JSON.stringify({
          host: siteUrl.replace(/^https?:\/\//, ""),
          key: indexnowKey,
          keyLocation: `${siteUrl}/${indexnowKey}.txt`,
          urlList: urls,
        }),
      });
      result.indexnow = "ok";
    } catch (e: unknown) {
      result.indexnow = `failed: ${(e as Error).message}`;
    }
  } else {
    result.indexnow = "skipped (INDEXNOW_KEY not set)";
  }

  // 百度普通收录：text/plain，每行一个 URL
  const baiduToken = process.env.BAIDU_PUSH_TOKEN || "";
  if (baiduToken) {
    try {
      const baiduSite = siteUrl.replace(/^https?:\/\//, "");
      const res = await $fetch<{ success?: number }>(
        `http://data.zz.baidu.com/urls?site=${baiduSite}&token=${baiduToken}`,
        {
          method: "POST",
          headers: { "Content-Type": "text/plain" },
          body: urls.join("\n"),
        },
      );
      result.baidu = `ok (success=${res?.success ?? "?"})`;
    } catch (e: unknown) {
      result.baidu = `failed: ${(e as Error).message}`;
    }
  } else {
    result.baidu = "skipped (BAIDU_PUSH_TOKEN not set)";
  }

  return { pushed: urls.length, ...result };
});
