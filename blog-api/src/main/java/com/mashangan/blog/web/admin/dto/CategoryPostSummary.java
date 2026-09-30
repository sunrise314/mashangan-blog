package com.mashangan.blog.web.admin.dto;

import java.time.OffsetDateTime;

/** 分类管理下拉列表用的文章轻量摘要（不含正文） */
public record CategoryPostSummary(
        Long id,
        String title,
        String slug,
        Boolean published,
        Boolean pinned,
        OffsetDateTime updatedAt
) {
}
