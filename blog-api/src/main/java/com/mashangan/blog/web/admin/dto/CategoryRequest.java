package com.mashangan.blog.web.admin.dto;

/** 分类创建/更新请求 */
public record CategoryRequest(
        String displayName,
        String slug,
        String cover,
        String description,
        Integer priority,
        Boolean hideFromList,
        String parentHaloName,
        String section,
        String template,
        Boolean preventParentCascadeQuery
) {
}
