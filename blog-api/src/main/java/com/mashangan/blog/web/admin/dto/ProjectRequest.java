package com.mashangan.blog.web.admin.dto;

import java.util.List;

/** 项目卡片创建/更新请求（含全量考点，保存时整批替换） */
public record ProjectRequest(
        Long industryId,
        Long seriesId,              // 非空 = 已开更挂系列；null = 占位（筹备中）
        String slug,
        String title,
        String jdFreq,              // 高频 | 中频 | 低频
        String summary,
        Integer sort,
        Boolean visible,
        List<ExamPointRequest> examPoints
) {
}
