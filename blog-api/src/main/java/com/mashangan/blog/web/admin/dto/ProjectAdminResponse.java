package com.mashangan.blog.web.admin.dto;

import java.time.OffsetDateTime;
import java.util.List;

/** 项目卡片后台列表/详情响应：实体字段 + 行业名/系列名/状态 */
public record ProjectAdminResponse(
        Long id,
        Long industryId,
        String industryName,
        Long seriesId,
        String seriesTitle,
        String slug,
        String title,
        String jdFreq,
        String summary,
        Integer sort,
        Boolean visible,
        OffsetDateTime createdAt,
        List<ExamPointRequest> examPoints
) {
}
