package com.mashangan.blog.web.admin.dto;

public record MenuItemRequest(
        Long menuId,
        Long parentId,
        String displayName,
        String href,
        String target,
        Integer priority,
        /** category / post / page / custom；为 category/post/page 时 refId 指向资源主键 */
        String refKind,
        Long refId
) {
}
