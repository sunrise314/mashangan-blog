package com.mashangan.blog.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 访客分析（数据看板）查询服务：直连 halo-db 中的独立 analytics 库（与采集端共用 ANALYTICS_PG）。
 * 未配置 ANALYTICS_PG 时所有接口返回 {enabled:false}（与原 halo-web 实现行为一致）。
 * 原生 SQL 移植自 blog-web/server/api/analytics/*（2026-10-13 迁入 JWT 体系，删除 ANALYTICS_PASSWORD 口令）。
 */
@Service
public class AnalyticsService {

    private static final Pattern PG_URL = Pattern.compile("^postgres(?:ql)?://([^:]+):([^@]*)@(.+)$");

    /** 搜索引擎识别（移植自 blog-web analytics-intel.parseEngine）：referrer 只剩 origin，按引擎聚合 */
    private static final List<Map.Entry<Pattern, String>> ENGINES = List.of(
            Map.entry(Pattern.compile("baidu\\.com$", Pattern.CASE_INSENSITIVE), "百度"),
            Map.entry(Pattern.compile("bing\\.com$", Pattern.CASE_INSENSITIVE), "必应"),
            Map.entry(Pattern.compile("google\\.", Pattern.CASE_INSENSITIVE), "谷歌"),
            Map.entry(Pattern.compile("sogou\\.com$", Pattern.CASE_INSENSITIVE), "搜狗"),
            Map.entry(Pattern.compile("so\\.com$", Pattern.CASE_INSENSITIVE), "360搜索"),
            Map.entry(Pattern.compile("sm\\.cn$", Pattern.CASE_INSENSITIVE), "神马"),
            Map.entry(Pattern.compile("yandex\\.", Pattern.CASE_INSENSITIVE), "Yandex"),
            Map.entry(Pattern.compile("duckduckgo\\.", Pattern.CASE_INSENSITIVE), "DuckDuckGo"));

    private final String pgUrl;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private volatile JdbcTemplate jdbc;

    public AnalyticsService(@Value("${app.analytics.pg:}") String pgUrl) {
        this.pgUrl = pgUrl == null ? "" : pgUrl.trim();
    }

    public boolean enabled() {
        return !pgUrl.isEmpty();
    }

    private synchronized JdbcTemplate jdbc() {
        if (jdbc == null) {
            Matcher m = PG_URL.matcher(pgUrl);
            String jdbcUrl;
            String user;
            String pass;
            if (m.matches()) {
                user = m.group(1);
                pass = m.group(2);
                jdbcUrl = "jdbc:postgresql://" + m.group(3);
            } else {
                user = "";
                pass = "";
                jdbcUrl = "jdbc:postgresql://" + pgUrl.replaceFirst("^postgres(?:ql)?://", "");
            }
            DriverManagerDataSource ds = new DriverManagerDataSource(jdbcUrl, user, pass);
            ds.setDriverClassName("org.postgresql.Driver");
            jdbc = new JdbcTemplate(ds);
        }
        return jdbc;
    }

    /** 上海时区当天 0 点的 epoch 毫秒（固定 UTC+8 无夏令时，与原 JS shDayStart 等价） */
    private static long shDayStart(int offsetDays) {
        var sh = java.time.ZoneId.of("Asia/Shanghai");
        return java.time.LocalDate.now(sh).atStartOfDay(sh).toInstant().toEpochMilli() - offsetDays * 86_400_000L;
    }

    private Map<String, Object> pvUv(long from, Long to) {
        List<Map<String, Object>> rows = to == null
                ? jdbc().queryForList("SELECT count(*)::int AS pv, count(DISTINCT vid)::int AS uv FROM visits WHERE type = 'pv' AND ts >= ?", from)
                : jdbc().queryForList("SELECT count(*)::int AS pv, count(DISTINCT vid)::int AS uv FROM visits WHERE type = 'pv' AND ts >= ? AND ts < ?", from, to);
        Map<String, Object> r = rows.isEmpty() ? Map.of() : rows.get(0);
        return Map.of("pv", Objects.requireNonNullElse(r.get("pv"), 0), "uv", Objects.requireNonNullElse(r.get("uv"), 0));
    }

    private List<Map<String, Object>> topBy(String col, long weekStart) {
        // col 来自内部固定白名单，非用户输入
        String sql = "SELECT " + col + " AS k, count(*)::int AS c FROM visits "
                + "WHERE type = 'pv' AND ts >= ? AND " + col + " <> '' "
                + "GROUP BY k ORDER BY c DESC LIMIT 10";
        return jdbc().queryForList(sql, weekStart);
    }

    /** 看板汇总：概览(PV/UV/在线) + 7 日趋势 + TOP 来源/搜索引擎/页面/设备/地区 */
    public Map<String, Object> summary() {
        if (!enabled()) {
            return Map.of("enabled", false);
        }
        long todayStart = shDayStart(0);
        long yesterdayStart = shDayStart(1);
        long weekStart = shDayStart(6);
        long fiveMinAgo = System.currentTimeMillis() - 5 * 60_000L;
        JdbcTemplate t = jdbc();

        List<Map<String, Object>> trend = t.queryForList(
                "SELECT to_char(to_timestamp(ts / 1000.0) AT TIME ZONE 'Asia/Shanghai', 'YYYY-MM-DD') AS d, "
                        + "count(*) FILTER (WHERE type = 'pv')::int AS pv, "
                        + "count(DISTINCT vid) FILTER (WHERE type = 'pv')::int AS uv "
                        + "FROM visits WHERE ts >= ? GROUP BY d ORDER BY d", weekStart);
        List<Map<String, Object>> topRefs = t.queryForList(
                "SELECT substring(ref FROM '://([^/]+)') AS k, count(*)::int AS c FROM visits "
                        + "WHERE type = 'pv' AND ts >= ? AND ref <> '' GROUP BY k ORDER BY c DESC LIMIT 10", weekStart);
        List<Map<String, Object>> refRows = t.queryForList(
                "SELECT ref, count(*)::int AS c FROM visits "
                        + "WHERE type = 'pv' AND ts >= ? AND ref <> '' GROUP BY ref ORDER BY c DESC LIMIT 200", weekStart);
        Integer online = t.queryForObject(
                "SELECT count(DISTINCT vid)::int FROM visits WHERE ts >= ?", Integer.class, fiveMinAgo);

        // 搜索引擎来源聚合
        Map<String, Integer> engineCount = new LinkedHashMap<>();
        for (Map<String, Object> r : refRows) {
            String engine = parseEngine(String.valueOf(r.get("ref")));
            if (!engine.isEmpty()) {
                engineCount.merge(engine, ((Number) r.get("c")).intValue(), Integer::sum);
            }
        }
        List<Map<String, Object>> topEngines = new ArrayList<>();
        engineCount.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(10)
                .forEach(e -> topEngines.add(Map.of("k", e.getKey(), "c", e.getValue())));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("enabled", true);
        out.put("today", pvUv(todayStart, null));
        out.put("yesterday", pvUv(yesterdayStart, todayStart));
        out.put("online", online == null ? 0 : online);
        out.put("trend", trend);
        out.put("topRefs", topRefs);
        out.put("topEngines", topEngines);
        out.put("topPages", topBy("path", weekStart));
        out.put("topBrowsers", topBy("browser", weekStart));
        out.put("topOss", topBy("os", weekStart));
        out.put("topDevices", topBy("device", weekStart));
        out.put("topRegions", topBy("region", weekStart));
        return out;
    }

    /** 最新访问分页（page 1 起，size 上限 100），按 id 倒序 */
    public Map<String, Object> visits(int page, int size) {
        if (!enabled()) {
            return Map.of("enabled", false);
        }
        int p = Math.max(1, page);
        int s = Math.min(100, Math.max(1, size));
        JdbcTemplate t = jdbc();
        Integer total = t.queryForObject("SELECT count(*)::int FROM visits", Integer.class);
        List<Map<String, Object>> rows = t.queryForList(
                "SELECT id, ts, type, path, title, ref, kw, dur, ip, region, isp, browser, os, device, vid "
                        + "FROM visits ORDER BY id DESC LIMIT ? OFFSET ?", s, (long) (p - 1) * s);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("enabled", true);
        out.put("total", total == null ? 0 : total);
        out.put("page", p);
        out.put("size", s);
        out.put("visits", rows);
        return out;
    }

    /** 单访客轨迹（时间升序，上限 500 条） */
    public Map<String, Object> visitor(String vid) {
        if (!enabled()) {
            return Map.of("enabled", false);
        }
        List<Map<String, Object>> rows = jdbc().queryForList(
                "SELECT id, vid, sid, ts, type, path, title, ref, kw, dur, ip, region, isp, "
                        + "browser, os, device, screen, lang, tz, extra::text AS extra, ua "
                        + "FROM visits WHERE vid = ? ORDER BY ts ASC LIMIT 500", vid);
        // extra JSONB 文本 → JSON 对象，前端直接 v.extra?.href 取值
        List<Map<String, Object>> visits = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            Map<String, Object> r = new HashMap<>(row);
            Object extra = r.remove("extra");
            if (extra != null) {
                try {
                    r.put("extra", objectMapper.readValue(String.valueOf(extra), Map.class));
                } catch (Exception ignored) {
                    // 非法 JSON 保持缺省
                }
            }
            visits.add(r);
        }
        return Map.of("enabled", true, "vid", vid, "visits", visits);
    }

    static String parseEngine(String ref) {
        if (ref == null || ref.isBlank()) {
            return "";
        }
        String host;
        try {
            host = new URI(ref).getHost();
        } catch (Exception e) {
            return "";
        }
        if (host == null) {
            return "";
        }
        for (Map.Entry<Pattern, String> e : ENGINES) {
            if (e.getKey().matcher(host).find()) {
                return e.getValue();
            }
        }
        return "";
    }
}
