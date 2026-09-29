package com.mashangan.blog.web.admin.dto;

import java.util.List;

/** 后台文章创建/更新请求。content 为 Markdown 源文，服务端渲染为 HTML。 */
public record PostRequest(
        String title,
        String slug,
        String cover,
        String content,
        String excerpt,
        List<String> categories,  // 分类 halo_name 列表
        List<String> tags,        // 标签 halo_name 列表
        Boolean published,
        Boolean pinned,
        Integer priority,
        String visible,
        Boolean allowComment,
        Long seriesId             // 所属项目/系列 ID，可空
) {
}
