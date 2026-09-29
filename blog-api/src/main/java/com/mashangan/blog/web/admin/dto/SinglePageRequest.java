package com.mashangan.blog.web.admin.dto;

/** 独立页面创建/更新请求，content 为 Markdown 源文 */
public record SinglePageRequest(
        String title,
        String slug,
        String content,
        Boolean published
) {
}
