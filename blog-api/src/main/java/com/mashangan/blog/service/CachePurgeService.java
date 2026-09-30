package com.mashangan.blog.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

/**
 * 文章写操作后异步通知 halo-web 清 SWR 缓存。
 * 用 @Async 保证不阻塞写操作主流程；失败静默，依赖 SWR 短 TTL 做兜底。
 */
@Slf4j
@Service
public class CachePurgeService {

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    @Value("${app.cache.purge-url:${HALO_PURGE_URL:}}")
    private String purgeUrl;

    @Value("${app.cache.purge-secret:${HALO_PURGE_SECRET:}}")
    private String purgeSecret;

    /** 清空全部 SWR 缓存（最稳妥，覆盖首页/归档/分类/详情等所有受影响页面） */
    @Async
    public void purgeAll() {
        doPurge(List.of());
    }

    /** 定向清除：指定需要清的路径前缀，如 /archives/my-post、/categories/golang */
    @Async
    public void purgePaths(List<String> paths) {
        if (paths == null || paths.isEmpty()) {
            purgeAll();
            return;
        }
        doPurge(paths);
    }

    private void doPurge(List<String> paths) {
        if (purgeUrl == null || purgeUrl.isBlank() || purgeSecret == null || purgeSecret.isBlank()) {
            log.debug("Cache purge skipped: HALO_PURGE_URL or HALO_PURGE_SECRET not configured");
            return;
        }
        try {
            String body = paths.isEmpty()
                    ? "{\"paths\":[]}"
                    : "{\"paths\":" + paths.stream()
                            .map(p -> "\"" + p.replace("\"", "\\\"") + "\"")
                            .reduce((a, b) -> a + "," + b)
                            .orElse("") + "}";

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(purgeUrl))
                    .header("Content-Type", "application/json")
                    .header("x-purge-token", purgeSecret)
                    .timeout(Duration.ofSeconds(5))
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                log.info("Cache purge ok: status={}, paths={}", resp.statusCode(), paths.isEmpty() ? "all" : paths);
            } else {
                log.warn("Cache purge failed: status={}, body={}", resp.statusCode(), resp.body());
            }
        } catch (Exception e) {
            log.warn("Cache purge exception: {}", e.getMessage());
        }
    }
}
