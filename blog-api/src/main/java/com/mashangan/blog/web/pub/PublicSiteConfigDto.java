package com.mashangan.blog.web.pub;

import com.mashangan.blog.domain.entity.SiteConfig;

/** 公开站点配置 DTO：只暴露前台需要的字段，不泄露 studio 密钥等敏感信息。 */
public record PublicSiteConfigDto(
        String title,
        String subtitle,
        String logoUrl,
        String faviconUrl,
        String footerText,
        String beianIcp,
        String beianPublicSecurity,
        String seoDescription,
        String seoKeywords,
        String homepageTitle,
        String homepageSubtitle,
        String analyticsHeadCode,
        // 知识星球：项目实战付费解锁
        String planetQrcodeUrl,
        String planetIntroHtml,
        String planetCtaText,
        String updatedAt
) {
    public static PublicSiteConfigDto from(SiteConfig c) {
        return new PublicSiteConfigDto(
                c.getTitle(), c.getSubtitle(), c.getLogoUrl(), c.getFaviconUrl(),
                c.getFooterText(), c.getBeianIcp(), c.getBeianPublicSecurity(),
                c.getSeoDescription(), c.getSeoKeywords(),
                c.getHomepageTitle(), c.getHomepageSubtitle(), c.getAnalyticsHeadCode(),
                c.getPlanetQrcodeUrl(), c.getPlanetIntroHtml(), c.getPlanetCtaText(),
                c.getUpdatedAt() != null ? c.getUpdatedAt().toString() : null);
    }
}
