package com.mashangan.blog.web.admin.dto;

import java.time.OffsetDateTime;

/** 行业后台响应：实体字段 + 卡片数 */
public record IndustryAdminResponse(
        Long id,
        String slug,
        String name,
        String intro,
        Integer sort,
        Boolean visible,
        OffsetDateTime createdAt,
        long projectCount
) {
}
