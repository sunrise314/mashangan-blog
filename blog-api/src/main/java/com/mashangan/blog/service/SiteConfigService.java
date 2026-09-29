package com.mashangan.blog.service;

import com.mashangan.blog.domain.entity.SiteConfig;
import com.mashangan.blog.mapper.SiteConfigMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

/**
 * 站点配置服务：操作单行表 site_config（id 恒为 1）。
 */
@Service
@RequiredArgsConstructor
public class SiteConfigService {

    private final SiteConfigMapper siteConfigMapper;

    /** 读取唯一配置行；若表为空则兜底返回空对象（不写库，避免与迁移默认行冲突）。 */
    public SiteConfig getConfig() {
        SiteConfig c = siteConfigMapper.selectById(1L);
        if (c == null) {
            // 迁移已 INSERT 默认行，理论上不会走到；兜底返回空对象避免 NPE
            c = new SiteConfig();
            c.setId(1L);
        }
        return c;
    }

    /** 更新唯一配置行；若不存在则插入。 */
    public SiteConfig update(SiteConfig patch) {
        SiteConfig c = getConfig();
        if (patch.getTitle() != null) c.setTitle(patch.getTitle());
        if (patch.getSubtitle() != null) c.setSubtitle(patch.getSubtitle());
        if (patch.getLogoUrl() != null) c.setLogoUrl(patch.getLogoUrl());
        if (patch.getFaviconUrl() != null) c.setFaviconUrl(patch.getFaviconUrl());
        if (patch.getFooterText() != null) c.setFooterText(patch.getFooterText());
        if (patch.getBeianIcp() != null) c.setBeianIcp(patch.getBeianIcp());
        if (patch.getBeianPublicSecurity() != null) c.setBeianPublicSecurity(patch.getBeianPublicSecurity());
        if (patch.getSeoDescription() != null) c.setSeoDescription(patch.getSeoDescription());
        if (patch.getSeoKeywords() != null) c.setSeoKeywords(patch.getSeoKeywords());
        if (patch.getHomepageTitle() != null) c.setHomepageTitle(patch.getHomepageTitle());
        if (patch.getHomepageSubtitle() != null) c.setHomepageSubtitle(patch.getHomepageSubtitle());
        if (patch.getAnalyticsHeadCode() != null) c.setAnalyticsHeadCode(patch.getAnalyticsHeadCode());
        if (patch.getStudioZhipuKey() != null) c.setStudioZhipuKey(patch.getStudioZhipuKey());
        if (patch.getStudioPexelsKey() != null) c.setStudioPexelsKey(patch.getStudioPexelsKey());
        if (patch.getStudioSiliconflowKey() != null) c.setStudioSiliconflowKey(patch.getStudioSiliconflowKey());
        if (patch.getPlanetQrcodeUrl() != null) c.setPlanetQrcodeUrl(patch.getPlanetQrcodeUrl());
        if (patch.getPlanetIntroHtml() != null) c.setPlanetIntroHtml(patch.getPlanetIntroHtml());
        if (patch.getPlanetCtaText() != null) c.setPlanetCtaText(patch.getPlanetCtaText());
        c.setUpdatedAt(OffsetDateTime.now());
        c.setId(1L);
        if (siteConfigMapper.selectById(1L) == null) {
            siteConfigMapper.insert(c);
        } else {
            siteConfigMapper.updateById(c);
        }
        return c;
    }
}
