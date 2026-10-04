package com.mashangan.blog.web.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mashangan.blog.domain.entity.SiteConfig;
import com.mashangan.blog.mapper.SeoPushMapper;
import com.mashangan.blog.service.SiteConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 后台 SEO 主动推送：
 * - 百度普通收录 API（data.zz.baidu.com/urls）：token 存 site_config.seo_baidu_token
 * - IndexNow API（api.indexnow.org，Bing/Yandex 等即时收录）：key 存 site_config.seo_indexnow_key，
 *   须与站点根 https://<host>/<key>.txt 文件一致
 * - Google 无公开提交 API 且服务器网络不可达 Google，只提供配置（GSC 资源 ID）+
 *   前端深链跳转 Search Console 网址检查页
 */
@RestController
@RequestMapping("/api/admin/seo/push")
@RequiredArgsConstructor
public class SeoPushController {

    private static final int MAX_URLS = 100;

    private final SiteConfigService siteConfigService;
    private final SeoPushMapper seoPushMapper;
    private final ObjectMapper mapper = new ObjectMapper();

    private final RestClient http = RestClient.builder()
            .requestFactory(new JdkClientHttpRequestFactory(
                    HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()))
            .build();

    /** 当前推送配置（仅 admin 可见，与站点设置页同一鉴权级别）。 */
    @GetMapping("/config")
    public Map<String, Object> config() {
        SiteConfig c = siteConfigService.getConfig();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("siteUrl", siteUrl(c));
        m.put("gscResource", hasText(c.getSeoGscResource()) ? c.getSeoGscResource().trim() : "sc-domain:" + host(c));
        m.put("baiduToken", nullToEmpty(c.getSeoBaiduToken()));
        m.put("indexnowKey", nullToEmpty(c.getSeoIndexnowKey()));
        return m;
    }

    /** 最近发布的文章，按前台最终 URL 规则拼好（与 sitemap 出口一致）。 */
    @GetMapping("/recent")
    public Map<String, Object> recent(@RequestParam(defaultValue = "50") int limit) {
        int n = Math.min(Math.max(limit, 1), 200);
        String base = siteUrl(siteConfigService.getConfig());
        List<Map<String, Object>> items = new ArrayList<>();
        for (Map<String, Object> row : seoPushMapper.selectRecent(n)) {
            String slug = str(row.get("slug"));
            if (slug == null || slug.isBlank()) continue;
            String series = str(row.get("series_slug"));
            String category = str(row.get("category_slug"));
            String path;
            if (hasText(series)) {
                path = "/column/" + enc(series) + "/" + enc(slug);
            } else if (hasText(category)) {
                path = "/categories/" + enc(category) + "/" + enc(slug);
            } else {
                path = "/archives/" + enc(slug);
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("title", nullToEmpty(str(row.get("title"))));
            item.put("url", base + path);
            items.add(item);
        }
        return Map.of("items", items);
    }

    public record PushRequest(List<String> urls) {
    }

    /** 推送到百度普通收录（text/plain，每行一个 URL）。 */
    @PostMapping("/baidu")
    public Map<String, Object> pushBaidu(@RequestBody PushRequest req) {
        List<String> urls = cleanUrls(req == null ? null : req.urls());
        if (urls.isEmpty()) return fail("没有可推送的 URL（须以站点 URL 开头）");
        SiteConfig c = siteConfigService.getConfig();
        String token = c.getSeoBaiduToken();
        if (!hasText(token)) return fail("百度推送 token 未配置：先到 ziyuan.baidu.com「普通收录」获取 token，填在上方配置里保存");
        String api = UriComponentsBuilder.fromHttpUrl("http://data.zz.baidu.com/urls")
                .queryParam("site", host(c))
                .queryParam("token", token.trim())
                .encode()
                .toUriString();
        try {
            String body = http.post().uri(URI.create(api))
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(String.join("\n", urls))
                    .retrieve().body(String.class);
            JsonNode j = mapper.readTree(body == null ? "{}" : body);
            if (j.has("success")) {
                return ok("百度推送成功 " + j.path("success").asInt() + " 条，今日剩余配额 " + j.path("remain").asInt());
            }
            return fail("百度返回错误 " + j.path("error").asInt() + "：" + j.path("message").asText(""));
        } catch (RestClientResponseException e) {
            return fail("HTTP " + e.getStatusCode().value() + "：" + brief(e.getResponseBodyAsString()));
        } catch (Exception e) {
            return fail("请求失败：" + e.getMessage());
        }
    }

    /** 推送到 IndexNow（api.indexnow.org 聚合 Bing/Yandex/Seznam/Yandex 等）。 */
    @PostMapping("/indexnow")
    public Map<String, Object> pushIndexnow(@RequestBody PushRequest req) {
        List<String> urls = cleanUrls(req == null ? null : req.urls());
        if (urls.isEmpty()) return fail("没有可推送的 URL（须以站点 URL 开头）");
        SiteConfig c = siteConfigService.getConfig();
        String key = c.getSeoIndexnowKey();
        if (!hasText(key)) return fail("IndexNow key 未配置：在上方配置里填写并保存（须与站点根 /<key>.txt 文件一致）");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("host", host(c));
        body.put("key", key.trim());
        body.put("keyLocation", siteUrl(c) + "/" + key.trim() + ".txt");
        body.put("urlList", urls);
        try {
            ResponseEntity<String> resp = http.post().uri(URI.create("https://api.indexnow.org/indexnow"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve().toEntity(String.class);
            int code = resp.getStatusCode().value();
            if (code == 200 || code == 202) {
                return ok("IndexNow 已接受 " + urls.size() + " 条（HTTP " + code + "），Bing/Yandex 等引擎将尽快抓取");
            }
            return fail("HTTP " + code + "：" + brief(resp.getBody()));
        } catch (RestClientResponseException e) {
            return fail("HTTP " + e.getStatusCode().value() + "：" + brief(e.getResponseBodyAsString()));
        } catch (Exception e) {
            return fail("请求失败：" + e.getMessage());
        }
    }

    // ------------------------------------------------------------------ helpers

    /** 只接受本站 URL、去重、封顶 100 条——防止后台接口被滥用为任意 URL 的推送代理。 */
    private List<String> cleanUrls(List<String> urls) {
        if (urls == null || urls.isEmpty()) return List.of();
        String base = siteUrl(siteConfigService.getConfig());
        LinkedHashSet<String> set = new LinkedHashSet<>();
        for (String u : urls) {
            if (u == null) continue;
            String t = u.trim();
            if (!t.equals(base) && !t.startsWith(base + "/")) continue;
            set.add(t);
            if (set.size() >= MAX_URLS) break;
        }
        return new ArrayList<>(set);
    }

    private String siteUrl(SiteConfig c) {
        String s = nullToEmpty(c.getSeoSiteUrl()).trim();
        if (s.isEmpty()) return "https://www.mashangan.com";
        while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        return s;
    }

    private String host(SiteConfig c) {
        return siteUrl(c).replaceFirst("^https?://", "");
    }

    private String enc(String segment) {
        return URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }

    private static Map<String, Object> ok(String message) {
        return Map.of("ok", true, "message", message);
    }

    private static Map<String, Object> fail(String message) {
        return Map.of("ok", false, "message", message);
    }

    private static String brief(String body) {
        if (body == null) return "";
        String t = body.replaceAll("\\s+", " ").trim();
        return t.length() > 200 ? t.substring(0, 200) + "…" : t;
    }
}
