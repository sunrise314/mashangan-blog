package com.mashangan.blog.web.admin.dto;

import java.time.OffsetDateTime;
import java.util.List;

/** 后台文章列表/详情响应（含分类、标签名，便于编辑回显）。 */
public record PostResponse(
        Long id,
        String haloName,
        String title,
        String slug,
        String cover,
        String excerpt,
        String content,        // Markdown 源文
        String contentHtml,
        String rawType,
        Boolean published,
        Boolean pinned,
        Integer priority,
        String visible,
        Boolean allowComment,
        List<String> categories,
        List<String> tags,
        Long seriesId,
        Integer freeOverride,
        OffsetDateTime publishTime,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
