package com.mashangan.blog.studio;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mashangan.blog.service.SiteConfigService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * /studio 配图流水线的 AI 与图源能力：
 * - GLM-4-Flash：文章解析，输出配图点位 JSON
 * - Pexels：CC0 商用图库检索（必须带浏览器 UA，Cloudflare 拦截默认 UA）
 * - SiliconFlow（Kolors）：AI 生图，watermark=false
 */
@Service
public class AiImageClient {

    private static final String BROWSER_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36";
    private static final String ZHIPU_CHAT_URL = "https://open.bigmodel.cn/api/paas/v4/chat/completions";
    private static final String SILICONFLOW_IMAGE_URL = "https://api.siliconflow.cn/v1/images/generations";
    private static final String PEXELS_SEARCH_URL = "https://api.pexels.com/v1/search";

    private final RestClient http = RestClient.builder()
            .defaultHeader(HttpHeaders.USER_AGENT, BROWSER_UA)
            .build();
    private final ObjectMapper mapper = new ObjectMapper();
    private final SiteConfigService siteConfig;

    public AiImageClient(SiteConfigService siteConfig) {
        this.siteConfig = siteConfig;
    }

    private String zhipuKey() {
        return siteConfig.getConfig().getStudioZhipuKey() == null ? "" : siteConfig.getConfig().getStudioZhipuKey();
    }
    private String pexelsKey() {
        return siteConfig.getConfig().getStudioPexelsKey() == null ? "" : siteConfig.getConfig().getStudioPexelsKey();
    }
    private String siliconflowKey() {
        return siteConfig.getConfig().getStudioSiliconflowKey() == null ? "" : siteConfig.getConfig().getStudioSiliconflowKey();
    }

    /** 调用 GLM-4-Flash 解析文章，产出配图点位。 */
    public List<ImagePoint> analyzeBlocks(String numberedBlocks, int imageCount, String style, int blockCount) throws Exception {
        if (zhipuKey().isBlank()) throw new IllegalStateException("未配置智谱 GLM API Key（站点设置页配置）");
        int contentCount = Math.max(0, imageCount - 1);
        String system = "你是技术博客配图助手。只输出一个 JSON 对象，不要输出任何解释文字或 markdown 围栏。"
                + "JSON 格式：{\"points\":[{\"type\":\"cover|content\",\"blockNo\":数字,\"position\":\"after|before\","
                + "\"scene\":\"中文画面描述\",\"caption\":\"中文图注(10字内)\",\"kind\":\"photo|illustration\","
                + "\"prompt\":\"英文AI绘图提示词\",\"keywords\":\"英文图库检索词,逗号分隔\"}]}";
        String user = "以下是文章的文本块列表，每块以 [序号] 开头（共 " + blockCount + " 块）。\n"
                + "需求：挑选 " + imageCount + " 个配图点位（含 1 个 type=cover 封面，blockNo 固定填 1；"
                + "其余 " + contentCount + " 个 type=content 分布在文中最有代表性的讲解段落）。\n"
                + "规则：\n"
                + "1. 代码块、表格、纯列表块不适合配图，禁止选择。\n"
                + "2. kind：讲解真实工具/环境/界面/写代码场景用 photo；抽象概念/架构思想/流程原理用 illustration。\n"
                + "3. prompt 用英文描述画面，" + style + " 风格，无文字无水印；keywords 用 2~4 个英文单词。\n"
                + "4. 若文章适合配图的内容不足，可以少给点位。\n"
                + "5. blockNo 必须是 1 到 " + blockCount + " 之间的整数。\n\n"
                + numberedBlocks;

        ObjectNode body = mapper.createObjectNode();
        body.put("model", "glm-4-flash");
        ArrayNode messages = body.putArray("messages");
        messages.add(obj("role", "system", "content", system));
        messages.add(obj("role", "user", "content", user));
        body.put("temperature", 0.3);
        body.put("max_tokens", 4096);

        String resp = http.post()
                .uri(ZHIPU_CHAT_URL)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + zhipuKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class);
        String content = readContent(resp);
        JsonNode parsed = extractJson(content);
        if (parsed == null || !parsed.has("points")) throw new IllegalStateException("GLM 未返回有效点位");

        List<ImagePoint> points = new ArrayList<>();
        boolean coverSeen = false;
        for (JsonNode raw : parsed.withArray("points")) {
            ImagePoint p = new ImagePoint();
            p.type = "content".equals(raw.path("type").asText()) || coverSeen ? "content" : "cover";
            if ("cover".equals(p.type)) coverSeen = true;
            p.blockNo = Math.min(blockCount, Math.max(1, raw.path("blockNo").asInt(1)));
            if ("cover".equals(p.type)) p.blockNo = 1;
            p.position = "before".equals(raw.path("position").asText()) ? "before" : "after";
            p.scene = raw.path("scene").asText("");
            p.caption = raw.path("caption").asText("配图");
            p.kind = "illustration".equals(raw.path("kind").asText()) ? "illustration" : "photo";
            p.prompt = raw.path("prompt").asText(p.scene);
            p.keywords = raw.path("keywords").asText("");
            p.id = "p" + (points.size() + 1);
            points.add(p);
        }
        if (points.isEmpty()) throw new IllegalStateException("GLM 返回空点位");
        return points;
    }

    /** Pexels 图库检索，返回第一张 landscape 图 URL；无结果返回 null。 */
    public String searchPexels(String keywords) {
        if (pexelsKey().isBlank() || keywords == null || keywords.isBlank()) return null;
        try {
            String resp = http.get()
                    .uri(PEXELS_SEARCH_URL + "?query=" + keywords.trim().replace(" ", "%20")
                            + "&per_page=4&orientation=landscape")
                    .header(HttpHeaders.AUTHORIZATION, pexelsKey())
                    .retrieve()
                    .body(String.class);
            JsonNode photos = mapper.readTree(resp).path("photos");
            if (photos.isEmpty()) return null;
            JsonNode src = photos.get(0).path("src");
            return src.has("landscape") ? src.get("landscape").asText() : src.path("large").asText(null);
        } catch (Exception e) {
            return null;
        }
    }

    /** SiliconFlow Kolors 生图，返回图片 URL。 */
    public String generateAiImage(String prompt) throws Exception {
        if (siliconflowKey().isBlank()) throw new IllegalStateException("未配置 SiliconFlow API Key（站点设置页配置）");
        ObjectNode body = mapper.createObjectNode();
        body.put("model", "Kwai-Kolors/Kolors");
        body.put("prompt", prompt + ", clean modern style, no text, no watermark");
        body.put("image_size", "1440x720");
        body.put("watermark", false);
        body.put("batch_size", 1);
        String resp = http.post()
                .uri(SILICONFLOW_IMAGE_URL)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + siliconflowKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class);
        JsonNode root = mapper.readTree(resp);
        JsonNode arr = root.path("images");
        if (arr.isMissingNode() || arr.isEmpty()) arr = root.path("data");
        if (arr.isMissingNode() || arr.isEmpty()) throw new IllegalStateException("AI 生图未返回 URL");
        return arr.get(0).path("url").asText();
    }

    /** 下载外源图片为字节。 */
    public byte[] downloadImage(String url) throws Exception {
        byte[] bytes = http.get().uri(url).retrieve().body(byte[].class);
        if (bytes == null || bytes.length == 0) throw new IllegalStateException("图片为空: " + url);
        return bytes;
    }

    // ---------- 内部工具 ----------

    private static ObjectNode obj(String k1, Object v1, String k2, Object v2) {
        ObjectNode o = new ObjectMapper().createObjectNode();
        o.put(k1, String.valueOf(v1));
        o.put(k2, String.valueOf(v2));
        return o;
    }

    private String readContent(String resp) throws Exception {
        JsonNode root = mapper.readTree(resp);
        return root.path("choices").path(0).path("message").path("content").asText("");
    }

    /** 从 LLM 输出中提取 JSON（容忍 ```json 围栏与前后杂文字）。 */
    private JsonNode extractJson(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) return null;
        try {
            return mapper.readTree(text.substring(start, end + 1));
        } catch (Exception e) {
            return null;
        }
    }
}
