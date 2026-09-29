package com.mashangan.blog.migration;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/** Halo 公开只读 API 客户端，返回原始 JsonNode，迁移字段映射与兼容层 DTO 解耦 */
public class HaloClient {

    private final RestClient client;
    private final int pageSize;

    public HaloClient(String baseUrl, int pageSize) {
        this.client = RestClient.builder().baseUrl(baseUrl).build();
        this.pageSize = pageSize;
    }

    private static final String CONTENT_API = "/apis/api.content.halo.run/v1alpha1";
    private static final String CORE_API = "/apis/api.halo.run/v1alpha1";

    public JsonNode categories() {
        return get(CONTENT_API + "/categories?size=200&page=1");
    }

    public List<JsonNode> allPosts() {
        return fetchAll(CONTENT_API + "/posts");
    }

    public JsonNode postDetail(String name) {
        return get(CONTENT_API + "/posts/" + name);
    }

    public List<JsonNode> allSinglePages() {
        return fetchAll(CONTENT_API + "/singlepages");
    }

    public JsonNode singlePageDetail(String name) {
        return get(CONTENT_API + "/singlepages/" + name);
    }

    public JsonNode primaryMenu() {
        return get(CORE_API + "/menus/-");
    }

    private List<JsonNode> fetchAll(String resourcePath) {
        List<JsonNode> all = new ArrayList<>();
        int page = 1;
        while (true) {
            String sep = resourcePath.contains("?") ? "&" : "?";
            JsonNode envelope = get(resourcePath + sep + "size=" + pageSize + "&page=" + page);
            JsonNode items = envelope.path("items");
            items.forEach(all::add);
            int totalPages = envelope.path("totalPages").asInt(0);
            if (page >= totalPages || items.isEmpty()) {
                return all;
            }
            page++;
        }
    }

    private JsonNode get(String path) {
        return client.get()
                .uri(path)
                .retrieve()
                .body(JsonNode.class);
    }

    // ---- JsonNode 取值辅助 ----

    public static String text(JsonNode node, String... path) {
        JsonNode cur = node;
        for (String p : path) {
            if (cur == null) {
                return null;
            }
            cur = cur.get(p);
        }
        if (cur == null || cur.isNull()) {
            return null;
        }
        return cur.asText();
    }

    public static boolean bool(JsonNode node, boolean fallback, String... path) {
        JsonNode cur = node;
        for (String p : path) {
            if (cur == null) {
                return fallback;
            }
            cur = cur.get(p);
        }
        return cur == null || cur.isNull() ? fallback : cur.asBoolean(fallback);
    }

    public static int integer(JsonNode node, int fallback, String... path) {
        String v = text(node, path);
        if (v == null || v.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** Halo 时间为 UTC 的 ISO-8601（纳秒精度），无法解析时返回 null */
    public static OffsetDateTime time(String iso) {
        if (iso == null || iso.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.ofInstant(Instant.parse(iso), ZoneOffset.UTC);
        } catch (Exception e) {
            return null;
        }
    }
}
