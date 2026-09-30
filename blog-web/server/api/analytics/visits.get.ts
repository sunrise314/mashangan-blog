/**
 * 最新访问分页接口：?page=1&size=20（page 1 起，size 上限 100）。
 * 返回 { enabled, total, page, size, visits }，visits 按时间倒序。
 * 鉴权：header x-analytics-token 与环境变量 ANALYTICS_PASSWORD 一致。
 */
export default defineEventHandler(async (event) => {
  checkAnalyticsAuth(event);

  const sql = await getAnalyticsSql();
  if (!sql) {
    return { enabled: false };
  }

  const q = getQuery(event);
  const size = Math.min(100, Math.max(1, Number(q.size) || 20));
  const page = Math.max(1, Number(q.page) || 1);

  const [totalRows, visits] = await Promise.all([
    sql`SELECT count(*)::int AS c FROM visits`,
    sql`
        SELECT id, ts, type, path, title, ref, kw, dur, ip, region, isp, browser, os, device, vid
        FROM visits ORDER BY id DESC LIMIT ${size} OFFSET ${(page - 1) * size}`,
  ]);

  return {
    enabled: true,
    total: totalRows[0]?.c ?? 0,
    page,
    size,
    visits,
  };
});
