package com.mashangan.blog.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 站点级配置，单行表（id 恒为 1，数据库 CHECK 约束保证）。
 * 取代 Halo 的 site_settings 键值表，强类型直存，便于 SQL 维护。
 */
@Data
@TableName("site_config")
public class SiteConfig {

    @TableId(type = IdType.INPUT)
    private Long id;

    private String title;
    private String subtitle;
    private String logoUrl;
    private String faviconUrl;
    private String footerText;
    private String beianIcp;
    private String beianPublicSecurity;
    private String seoDescription;
    private String seoKeywords;
    private String homepageTitle;
    private String homepageSubtitle;
    private String analyticsHeadCode;
    /** /studio AI 配图流水线密钥（页面可配，留空则对应功能不可用） */
    private String studioZhipuKey;
    private String studioPexelsKey;
    private String studioSiliconflowKey;
    /** 知识星球配置：项目实战章节付费解锁 */
    private String planetQrcodeUrl;
    private String planetIntroHtml;
    private String planetCtaText;
    /** SEO 主动推送：百度普通收录 token / IndexNow key / 站点 URL / GSC 资源 ID（后台「SEO 提交」页） */
    private String seoBaiduToken;
    private String seoIndexnowKey;
    private String seoSiteUrl;
    private String seoGscResource;
    private OffsetDateTime updatedAt;
}
