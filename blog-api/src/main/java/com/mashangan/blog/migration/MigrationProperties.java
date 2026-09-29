package com.mashangan.blog.migration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 迁移器配置（仅 migrate-halo profile 使用）。
 * haloBaseUrl: Halo 源站 API 根地址，如 http://49.235.136.65:8090（公开只读接口即可，无需 PAT）
 * attachmentDir: Halo 附件根目录（含 upload/ 子目录）的本机路径，配置后扫描落库；不配置则跳过
 */
@ConfigurationProperties(prefix = "app.migration")
public class MigrationProperties {

    private String haloBaseUrl;

    private int pageSize = 100;

    private String attachmentDir;

    public String getHaloBaseUrl() {
        return haloBaseUrl;
    }

    public void setHaloBaseUrl(String haloBaseUrl) {
        this.haloBaseUrl = haloBaseUrl;
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }

    public String getAttachmentDir() {
        return attachmentDir;
    }

    public void setAttachmentDir(String attachmentDir) {
        this.attachmentDir = attachmentDir;
    }
}
