package com.mashangan.blog.studio;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mashangan.blog.service.SiteConfigService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;

/**
 * AI 配图提供商的连通性与额度查询。
 *
 * <p>额度查询策略：
 * <ul>
 *   <li><b>SiliconFlow</b> — 调 /v1/user/info 拿账户余额；该接口已于 2026-08-14 下线，
 *       若失败则降级返回提示。</li>
 *   <li><b>智谱 GLM</b> — GLM-4-Flash 长期免费；调一次最小 chat 请求，
 *       从响应头 X-RateLimit-Remaining-* 拿限流信息。</li>
 *   <li><b>Pexels</b> — 免费 200 req/h、20,000 req/月；调一次 search，
 *       从响应头 X-Ratelimit-* 拿剩余量。</li>
 * </ul>
 */
@Service
public class StudioQuotaService {

    private static final String BROWSER_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/126.0 Safari/537.36";
    private static final String ZHIPU_CHAT_URL = "https://open.bigmodel.cn/api/paas/v4/chat/completions";
    private static final String SILICONFLOW_USER_INFO_URL = "https://api.siliconflow.cn/v1/user/info";
    private static final String PEXELS_SEARCH_URL = "https://api.pexels.com/v1/search?query=test&per_page=1";

    private final RestClient http = RestClient.builder()
            .defaultHeader(HttpHeaders.USER_AGENT, BROWSER_UA)
            .build();
    private final ObjectMapper mapper = new ObjectMapper();
    private final SiteConfigService siteConfig;

    public StudioQuotaService(SiteConfigService siteConfig) {
        this.siteConfig = siteConfig;
    }

    /** 查询全部提供商状态。 */
    public List<ProviderStatus> all() {
        List<ProviderStatus> list = new ArrayList<>();
        list.add(siliconflow());
        list.add(zhipu());
        list.add(pexels());
        return list;
    }

    /** 测试单个提供商连通性（用一个轻量请求）。 */
    public ProviderStatus test(String name) {
        return switch (name.toLowerCase()) {
            case "siliconflow" -> siliconflow();
            case "zhipu", "glm" -> zhipu();
            case "pexels" -> pexels();
            default -> ProviderStatus.unknown(name);
        };
    }

    // ---------- 各提供商查询 ----------

    private ProviderStatus siliconflow() {
        String key = siteConfig.getConfig().getStudioSiliconflowKey();
        boolean configured = key != null && !key.isBlank();
        ProviderStatus status = ProviderStatus.of("siliconflow", "SiliconFlow (Kolors AI 生图)",
                configured, mask(key));
        if (!configured) return status;
        try {
            String resp = http.get()
                    .uri(SILICONFLOW_USER_INFO_URL)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + key)
                    .retrieve()
                    .body(String.class);
            JsonNode data = mapper.readTree(resp).path("data");
            if (data.isMissingNode() || data.isNull()) {
                return status.withBalance("—").withError("余额查询接口暂不可用（平台下线 /user/info）");
            }
            String total = data.path("totalBalance").asText(null);
            String charge = data.path("chargeBalance").asText(null);
            if (total != null) {
                status = status.withBalance("¥" + total);
            } else if (charge != null) {
                status = status.withBalance("¥" + charge);
            } else {
                status = status.withBalance("—");
            }
        } catch (Exception e) {
            status = status.withBalance("—").withError(
                    "余额查询失败：" + (e.getMessage() == null ? "接口不可用" : e.getMessage()));
        }
        return status;
    }

    private ProviderStatus zhipu() {
        String key = siteConfig.getConfig().getStudioZhipuKey();
        boolean configured = key != null && !key.isBlank();
        ProviderStatus status = ProviderStatus.of("zhipu", "智谱 GLM (文章解析)",
                configured, mask(key))
                .withBalance("长期免费 (GLM-4-Flash)");
        if (!configured) return status;
        try {
            // 发一个最小请求，拿响应头
            org.springframework.http.ResponseEntity<String> resp = http.post()
                    .uri(ZHIPU_CHAT_URL)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + key)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("""
                            {"model":"glm-4-flash","messages":[{"role":"user","content":"ping"}],"max_tokens":4}
                            """)
                    .retrieve()
                    .toEntity(String.class);
            HttpHeaders headers = resp.getHeaders();
            String remainingReq = headers.getFirst("X-RateLimit-Remaining-Requests");
            String remainingTkn = headers.getFirst("X-RateLimit-Remaining-Tokens");
            String resetReq = headers.getFirst("X-RateLimit-Reset-Requests");
            String resetTkn = headers.getFirst("X-RateLimit-Reset-Tokens");
            StringBuilder sb = new StringBuilder();
            if (remainingReq != null) sb.append("请求剩余 ").append(remainingReq);
            if (remainingTkn != null) sb.append(" / Tokens 剩余 ").append(remainingTkn);
            if (resetReq != null) sb.append(" (重置 ").append(resetReq).append("s)");
            if (sb.length() > 0) status = status.withRateLimit(sb.toString());
            status = status.withError(null);
        } catch (Exception e) {
            status = status.withRateLimit("—").withError(
                    "连通性测试失败：" + (e.getMessage() == null ? "" : e.getMessage()));
        }
        return status;
    }

    private ProviderStatus pexels() {
        String key = siteConfig.getConfig().getStudioPexelsKey();
        boolean configured = key != null && !key.isBlank();
        ProviderStatus status = ProviderStatus.of("pexels", "Pexels (CC0 图库检索)",
                configured, mask(key))
                .withBalance("免费 20,000 请求/月");
        if (!configured) return status;
        try {
            org.springframework.http.ResponseEntity<String> resp = http.get()
                    .uri(PEXELS_SEARCH_URL)
                    .header(HttpHeaders.AUTHORIZATION, key)
                    .retrieve()
                    .toEntity(String.class);
            HttpHeaders headers = resp.getHeaders();
            String limit = headers.getFirst("X-Ratelimit-Limit");
            String remaining = headers.getFirst("X-Ratelimit-Remaining");
            String reset = headers.getFirst("X-Ratelimit-Reset");
            StringBuilder sb = new StringBuilder();
            if (limit != null) sb.append("总额度 ").append(limit);
            if (remaining != null) sb.append(" / 剩余 ").append(remaining);
            if (reset != null) {
                long resetEpoch;
                try { resetEpoch = Long.parseLong(reset); } catch (NumberFormatException nfe) { resetEpoch = 0; }
                if (resetEpoch > 0) {
                    long minutes = (resetEpoch - System.currentTimeMillis() / 1000) / 60;
                    sb.append(" / 重置于 ").append(minutes).append(" 分钟后");
                }
            }
            if (sb.length() > 0) status = status.withRateLimit(sb.toString());
        } catch (Exception e) {
            // Pexels 连通性有问题也不影响余额显示（余额是固定月额度）
            status = status.withRateLimit("—").withError(
                    "连通性测试失败：" + (e.getMessage() == null ? "" : e.getMessage()));
        }
        return status;
    }

    // ---------- 工具 ----------

    private static String mask(String key) {
        if (key == null || key.isBlank()) return "未配置";
        if (key.length() <= 8) return key.charAt(0) + "***" + key.charAt(key.length() - 1);
        return key.substring(0, 4) + "***" + key.substring(key.length() - 4);
    }
}
