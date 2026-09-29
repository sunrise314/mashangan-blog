package com.mashangan.blog.studio;

import com.mashangan.blog.domain.entity.SiteConfig;
import com.mashangan.blog.service.SiteConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** AI 配图提供商配置与额度查询。 */
@RestController
@RequestMapping("/api/admin/studio/ai-config")
@RequiredArgsConstructor
public class StudioAiConfigController {

    private final StudioQuotaService quotaService;
    private final SiteConfigService siteConfigService;

    /** 返回全部 AI 提供商的当前状态（连通性、额度、余额）。 */
    @GetMapping
    public List<ProviderStatus> all() {
        return quotaService.all();
    }

    /** 测试单个提供商连通性。 */
    @PostMapping("/test/{name}")
    public ProviderStatus test(@PathVariable String name) {
        return quotaService.test(name);
    }

    /**
     * 批量更新 AI 提供商的 API Key。
     * 请求体只需包含要更新的字段（null 或未出现的字段保持不变）：
     * { "zhipuKey": "...", "pexelsKey": "...", "siliconflowKey": "..." }
     * 空字符串视为清除。
     */
    @PutMapping
    public List<ProviderStatus> updateKeys(@RequestBody Map<String, String> body) {
        SiteConfig patch = new SiteConfig();
        patch.setId(1L);
        boolean any = false;
        if (body.containsKey("zhipuKey")) {
            patch.setStudioZhipuKey(body.get("zhipuKey"));  // null/空串/有效值 都由 Service 层处理
            any = true;
        }
        if (body.containsKey("pexelsKey")) {
            patch.setStudioPexelsKey(body.get("pexelsKey"));
            any = true;
        }
        if (body.containsKey("siliconflowKey")) {
            patch.setStudioSiliconflowKey(body.get("siliconflowKey"));
            any = true;
        }
        if (any) siteConfigService.update(patch);
        return quotaService.all();
    }

    // Service 层已支持 isBlank() → null，此处无需额外转换
}
