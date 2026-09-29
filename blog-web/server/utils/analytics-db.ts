/**
 * 访客分析存储层：postgres.js 直连复用服务器 halo-db 容器中的独立 analytics 库。
 * 不触碰 Halo 自身数据；ANALYTICS_PG 未配置时所有接口静默降级（不采集、看板提示未启用）。
 */
import postgres from "postgres";

type Sql = ReturnType<typeof postgres>;

let _sql: Sql | null = null;
let _init: Promise<Sql> | null = null;

const DDL_STATEMENTS = [
  `CREATE TABLE IF NOT EXISTS visits (
    id BIGSERIAL PRIMARY KEY,
    vid TEXT NOT NULL DEFAULT '',
    sid TEXT NOT NULL DEFAULT '',
    ts BIGINT NOT NULL,
    type TEXT NOT NULL DEFAULT 'pv',
    path TEXT NOT NULL DEFAULT '',
    title TEXT NOT NULL DEFAULT '',
    ref TEXT NOT NULL DEFAULT '',
    kw TEXT NOT NULL DEFAULT '',
    dur INT NOT NULL DEFAULT 0,
    ip TEXT NOT NULL DEFAULT '',
    region TEXT NOT NULL DEFAULT '',
    isp TEXT NOT NULL DEFAULT '',
    browser TEXT NOT NULL DEFAULT '',
    os TEXT NOT NULL DEFAULT '',
    device TEXT NOT NULL DEFAULT '',
    screen TEXT NOT NULL DEFAULT '',
    lang TEXT NOT NULL DEFAULT '',
    tz TEXT NOT NULL DEFAULT '',
    extra JSONB,
    ua TEXT NOT NULL DEFAULT ''
  )`,
  `CREATE INDEX IF NOT EXISTS idx_visits_ts ON visits(ts)`,
  `CREATE INDEX IF NOT EXISTS idx_visits_vid ON visits(vid)`,
  `CREATE INDEX IF NOT EXISTS idx_visits_sid ON visits(sid)`,
  `CREATE INDEX IF NOT EXISTS idx_visits_type_ts ON visits(type, ts)`,
];

/** 取（并懒初始化）analytics 库连接；未配置 ANALYTICS_PG 或建表失败返回 null */
export async function getAnalyticsSql(): Promise<Sql | null> {
  const url = (process.env.ANALYTICS_PG || "").trim();
  if (!url) return null;
  if (!_sql) {
    _sql = postgres(url, {
      max: 5,
      idle_timeout: 30,
      connect_timeout: 5,
      prepare: false,
    });
  }
  if (!_init) {
    _init = (async () => {
      for (const ddl of DDL_STATEMENTS) {
        await _sql!.unsafe(ddl);
      }
      return _sql!;
    })().catch((err) => {
      _init = null;
      _sql = null;
      throw err;
    });
  }
  try {
    return await _init;
  } catch (err) {
    console.error("[analytics] db init failed:", err);
    return null;
  }
}

/** 停留时长回写：leave 事件更新同访客同会话同路径最新一条 PV 的 dur */
export async function applyLeaveDuration(
  sql: Sql,
  payload: { vid: string; sid: string; path: string; dur: number }
): Promise<void> {
  const dur = Math.max(0, Math.min(Math.floor(payload.dur) || 0, 24 * 60 * 60));
  if (!payload.vid || !payload.sid || !payload.path) return;
  await sql.unsafe(
    `UPDATE visits SET dur = $1
     WHERE id = (
       SELECT id FROM visits
       WHERE vid = $2 AND sid = $3 AND path = $4 AND type = 'pv'
       ORDER BY ts DESC LIMIT 1
     )`,
    [dur, payload.vid, payload.sid, payload.path]
  );
}
