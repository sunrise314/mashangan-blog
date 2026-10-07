package com.mashangan.blog.web.halo.dto;

/** /column 卡片列表用的精简系列信息 */
public record SeriesCard(
        String slug,
        String title,
        String cover,
        String description,
        String status,        // updating | complete
        int chapterCount,
        boolean hidden        // 下架标记：true 时前台 /column 卡片列表过滤掉（sitemap/rss 仍包含）
) {
}
