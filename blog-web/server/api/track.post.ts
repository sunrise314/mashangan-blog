/**
 * 访客上报接口（sendBeacon / fetch POST）。
 * 客户端只带 vid/sid/type/path/title/ref/dur/screen/lang/tz/extra 等原始字段，
 * IP 归属地（ip2region）与 UA 解析（浏览器/OS/设备）全部在服务端补全。
 * 未配置 ANALYTICS_PG 时静默丢弃，不影响前台。
 */
export default defineEventHandler(async (event) => {
  const sql = await getAnalyticsSql();
  if (!sql) {
    setResponseStatus(event, 204);
    return "";
  }

  // 简单防伪：带 Origin 头时必须同源（sendBeacon 同源请求通常不带 Origin）
  const origin = String(getHeader(event, "origin") || "");
  if (origin) {
    try {
      if (new URL(origin).host !== String(getHeader(event, "host") || "")) {
        throw createError({ statusCode: 403, statusMessage: "Forbidden" });
      }
    } catch {
      setResponseStatus(event, 204);
      return "";
    }
  }

  const body = await readBody<Record<string, unknown>>(event).catch(() => null);
  if (!body || typeof body !== "object") {
    setResponseStatus(event, 204);
    return "";
  }

  const str = (v: unknown, max: number): string =>
    typeof v === "string" ? v.slice(0, max) : "";

  const vid = str(body.vid, 64);
  const sid = str(body.sid, 64);
  const type = ["pv", "click"].includes(String(body.type)) ? String(body.type) : "";
  if (!vid || !sid || !type) {
    setResponseStatus(event, 204);
    return "";
  }

  const ua = getHeader(event, "user-agent") || str(body.ua, 300);
  const { browser, os, device } = parseUa(ua);
  const ref = str(body.ref, 500);
  const kw = parseKw(ref);
  const ip = getClientIp(event);
  const { region, isp } = await searchIpRegion(ip);

  const row = {
    vid,
    sid,
    ts: Date.now(),
    type,
    path: str(body.path, 500) || "/",
    title: str(body.title, 300),
    ref,
    kw,
    dur: 0,
    ip,
    region,
    isp,
    browser,
    os,
    device,
    screen: str(body.screen, 20),
    lang: str(body.lang, 20),
    tz: str(body.tz, 50),
    extra: (body.extra && typeof body.extra === "object" ? body.extra : null) as object | null,
    ua,
  };

  try {
    if (type === "click") {
      await sql`
        INSERT INTO visits
          (vid, sid, ts, type, path, title, ref, kw, dur, ip, region, isp, browser, os, device, screen, lang, tz, extra, ua)
        VALUES
          (${row.vid}, ${row.sid}, ${row.ts}, ${row.type}, ${row.path}, ${row.title}, ${row.ref},
           ${row.kw}, ${row.dur}, ${row.ip}, ${row.region}, ${row.isp}, ${row.browser}, ${row.os},
           ${row.device}, ${row.screen}, ${row.lang}, ${row.tz}, ${row.extra}, ${row.ua})`;
    } else if (body.leave === true) {
      // leave 事件：回写同访客同会话同路径最新一条 PV 的停留时长
      const dur = Number(body.dur);
      await applyLeaveDuration(sql, {
        vid,
        sid,
        path: row.path,
        dur: Number.isFinite(dur) ? dur : 0,
      });
    } else {
      await sql`
        INSERT INTO visits
          (vid, sid, ts, type, path, title, ref, kw, dur, ip, region, isp, browser, os, device, screen, lang, tz, extra, ua)
        VALUES
          (${row.vid}, ${row.sid}, ${row.ts}, ${row.type}, ${row.path}, ${row.title}, ${row.ref},
           ${row.kw}, ${row.dur}, ${row.ip}, ${row.region}, ${row.isp}, ${row.browser}, ${row.os},
           ${row.device}, ${row.screen}, ${row.lang}, ${row.tz}, ${row.extra}, ${row.ua})`;
    }
  } catch (err) {
    console.error("[analytics] track insert failed:", err);
  }

  setResponseStatus(event, 204);
  return "";
});
