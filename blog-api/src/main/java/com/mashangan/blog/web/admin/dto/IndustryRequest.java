package com.mashangan.blog.web.admin.dto;

/** 行业创建/更新请求 */
public record IndustryRequest(
        String slug,
        String name,
        String intro,
        Integer sort,
        Boolean visible
) {
}
