package com.mashangan.blog.web.halo.dto;

import java.util.List;

/** 项目/系列详情：卡片元信息 + 章节列表（含免费/付费标记） */
public record SeriesDetail(
        String slug,
        String title,
        String cover,
        String description,
        String status,        // updating | complete
        int freeChapterCount,
        int totalChapters,
        List<Chapter> chapters
) {
    public record Chapter(
            String name,       // halo_name（文章唯一标识）
            String title,
            String slug,
            String cover,
            String excerpt,
            boolean free,
            int order
    ) {
    }
}
