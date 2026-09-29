package com.mashangan.blog.web.pub;

import com.mashangan.blog.domain.entity.SiteConfig;
import com.mashangan.blog.domain.entity.SocialLink;
import com.mashangan.blog.service.SiteConfigService;
import com.mashangan.blog.service.SocialLinkService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 公开的站点配置端点（免鉴权，由 SecurityConfig 的 anyRequest().permitAll() 放行）。
 * 前台 SSR 启动时拉取一次，用于覆盖硬编码的站点标题/footer/SEO/社交链接等。
 */
@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class PublicSiteConfigController {

    private final SiteConfigService siteConfigService;
    private final SocialLinkService socialLinkService;

    /** 返回站点配置 + 启用的社交链接。 */
    @GetMapping("/site-config")
    public SiteConfigResponse siteConfig() {
        SiteConfig config = siteConfigService.getConfig();
        List<SocialLink> links = socialLinkService.list().stream()
                .filter(SocialLink::getEnabled).toList();
        return new SiteConfigResponse(PublicSiteConfigDto.from(config), links);
    }

    public record SiteConfigResponse(PublicSiteConfigDto config, List<SocialLink> socialLinks) {
    }
}
