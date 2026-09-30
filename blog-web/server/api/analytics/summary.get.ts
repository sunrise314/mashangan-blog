/**
 * 看板汇总接口：概览(PV/UV/在线) + 7 日趋势 + TOP 来源/搜索词/页面/设备/地区。
 * 最新访问已拆分到 /api/analytics/visits 分页接口。
 * 鉴权：header x-analytics-token 与环境变量 ANALYTICS_PASSWORD 一致。
 */
export default defineEventHandler(async (event) => {
  checkAnalyticsAuth(event);

  const sql = await getAnalyticsSql();
  if (!sql) {
    return { enabled: false };
  }

  // Asia/Shanghai 固定 UTC+8（无夏令时）
  const shDayStart = (offsetDays = 0): number => {
    const shifted = Date.now() + 8 * 3600_000;
    const midnight = Math.floor(shifted / 86_400_000) * 86_400_000;
    return midnight - offsetDays * 86_400_000 - 8 * 3600_000;
  };
  const todayStart = shDayStart(0);
  const yesterdayStart = shDayStart(1);
  const weekStart = shDayStart(6);
  const fiveMinAgo = Date.now() - 5 * 60_000;

  const pvUv = async (from: number, to?: number) => {
    const rows = to
      ? await sql`
          SELECT count(*)::int AS pv, count(DISTINCT vid)::int AS uv FROM visits
          WHERE type = 'pv' AND ts >= ${from} AND ts < ${to}`
      : await sql`
          SELECT count(*)::int AS pv, count(DISTINCT vid)::int AS uv FROM visits
          WHERE type = 'pv' AND ts >= ${from}`;
    return { pv: rows[0]?.pv ?? 0, uv: rows[0]?.uv ?? 0 };
  };

  const topBy = async (col: "path" | "browser" | "os" | "device" | "region", limit = 10) => {
    const queries = {
      path: sql`SELECT path AS k, count(*)::int AS c FROM visits
                WHERE type = 'pv' AND ts >= ${weekStart} AND path <> ''
                GROUP BY k ORDER BY c DESC LIMIT ${limit}`,
      browser: sql`SELECT browser AS k, count(*)::int AS c FROM visits
                   WHERE type = 'pv' AND ts >= ${weekStart} AND browser <> ''
                   GROUP BY k ORDER BY c DESC LIMIT ${limit}`,
      os: sql`SELECT os AS k, count(*)::int AS c FROM visits
              WHERE type = 'pv' AND ts >= ${weekStart} AND os <> ''
              GROUP BY k ORDER BY c DESC LIMIT ${limit}`,
      device: sql`SELECT device AS k, count(*)::int AS c FROM visits
                  WHERE type = 'pv' AND ts >= ${weekStart} AND device <> ''
                  GROUP BY k ORDER BY c DESC LIMIT ${limit}`,
      region: sql`SELECT region AS k, count(*)::int AS c FROM visits
                  WHERE type = 'pv' AND ts >= ${weekStart} AND region <> ''
                  GROUP BY k ORDER BY c DESC LIMIT ${limit}`,
    };
    return queries[col];
  };

  const [
    today,
    yesterday,
    online,
    trend,
    topRefs,
    topKws,
    topPages,
    topBrowsers,
    topOss,
    topDevices,
    topRegions,
  ] = await Promise.all([
    pvUv(todayStart),
    pvUv(yesterdayStart, todayStart),
    sql`SELECT count(DISTINCT vid)::int AS c FROM visits WHERE ts >= ${fiveMinAgo}`,
    sql`
        SELECT to_char(to_timestamp(ts / 1000.0) AT TIME ZONE 'Asia/Shanghai', 'YYYY-MM-DD') AS d,
               count(*) FILTER (WHERE type = 'pv')::int AS pv,
               count(DISTINCT vid) FILTER (WHERE type = 'pv')::int AS uv
        FROM visits
        WHERE ts >= ${weekStart}
        GROUP BY d ORDER BY d`,
    sql`
        SELECT substring(ref FROM '://([^/]+)') AS k, count(*)::int AS c FROM visits
        WHERE type = 'pv' AND ts >= ${weekStart} AND ref <> ''
        GROUP BY k ORDER BY c DESC LIMIT 10`,
    sql`
        SELECT kw AS k, count(*)::int AS c FROM visits
        WHERE type = 'pv' AND ts >= ${weekStart} AND kw <> ''
        GROUP BY k ORDER BY c DESC LIMIT 10`,
    topBy("path"),
    topBy("browser"),
    topBy("os"),
    topBy("device"),
    topBy("region"),
  ]);

  return {
    enabled: true,
    today,
    yesterday,
    online: online[0]?.c ?? 0,
    trend,
    topRefs,
    topKws,
    topPages,
    topBrowsers,
    topOss,
    topDevices,
    topRegions,
  };
});
