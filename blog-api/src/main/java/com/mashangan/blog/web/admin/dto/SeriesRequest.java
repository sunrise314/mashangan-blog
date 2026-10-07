package com.mashangan.blog.web.admin.dto;

/** 项目/系列创建/更新请求 */
public record SeriesRequest(
        String slug,
        String title,
        String cover,
        String description,
        String status,              // updating | complete
        Integer freeChapterCount,   // 免费章节数，0 = 全部免费
        Integer sortOrder,
        Boolean hidden              // 下架标记：true 时前台 /column 卡片不展示
) {
}
