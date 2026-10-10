package com.mashangan.blog.web.halo.dto;

import java.util.List;

/**
 * 筹备中项目落地页（考点清单页）：项目元信息 + 面试考点清单。
 * open=true 时也返回 seriesSlug，前端可跳转真实系列页。
 */
public record ProjectDetail(
        String slug,
        String title,
        String jdFreq,
        String summary,
        boolean open,
        String seriesSlug,
        String seriesTitle,
        String industrySlug,
        String industryName,
        List<ExamPoint> examPoints
) {

    public record ExamPoint(int sort, String point, String detail) {
    }
}
