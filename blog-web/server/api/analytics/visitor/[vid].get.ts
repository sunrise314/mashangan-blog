/**
 * 单访客轨迹接口：按 vid 返回全部事件（按时间升序）。
 * 鉴权：header x-analytics-token 与环境变量 ANALYTICS_PASSWORD 一致。
 */
export default defineEventHandler(async (event) => {
  checkAnalyticsAuth(event);

  const sql = await getAnalyticsSql();
  if (!sql) {
    return { enabled: false };
  }

  const vid = String(getRouterParam(event, "vid") || "").trim();
  if (!vid) {
    throw createError({ statusCode: 400, statusMessage: "vid is required" });
  }

  const visits = await sql`
    SELECT id, vid, sid, ts, type, path, title, ref, kw, dur, ip, region, isp,
           browser, os, device, screen, lang, tz, extra, ua
    FROM visits WHERE vid = ${vid} ORDER BY ts ASC LIMIT 500`;

  return { enabled: true, vid, visits };
});
