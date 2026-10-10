package com.mashangan.blog.web.halo.dto;

import java.util.List;

/**
 * 行业项目地图：一个行业 + 其下全部项目。
 * /column 总览页（全量）与 /column/industry/{slug} 行业页（单个）共用。
 */
public record IndustryDetail(
        String slug,
        String name,
        String intro,
        List<ProjectCard> projects
) {

    /** open=true 表示已开更（挂真实系列），seriesSlug/seriesTitle 非空；open=false 为筹备中 */
    public record ProjectCard(
            String slug,
            String title,
            String jdFreq,
            String summary,
            boolean open,
            String seriesSlug,
            String seriesTitle,
            int examPointCount
    ) {
    }
}
