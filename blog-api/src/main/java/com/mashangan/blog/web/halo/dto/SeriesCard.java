package com.mashangan.blog.web.halo.dto;

/** /column 卡片列表用的精简系列信息 */
public record SeriesCard(
        String slug,
        String title,
        String cover,
        String description,
        String status,        // updating | complete
        int chapterCount
) {
}
