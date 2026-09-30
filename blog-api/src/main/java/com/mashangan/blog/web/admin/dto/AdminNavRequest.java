package com.mashangan.blog.web.admin.dto;

/**
 * 后台导航项创建/更新请求。
 *
 * @param parentId   父导航 id（用于树形结构，目前扁平使用传 null）
 * @param menuName   显示名称
 * @param path       路由路径（/admin/xxx 视为 SPA 内部路由，其他视为外部链接）
 * @param icon       emoji 或图标名
 * @param sortOrder  升序排列
 * @param visible    是否在侧边栏显示
 */
public record AdminNavRequest(
        Long parentId,
        String menuName,
        String path,
        String icon,
        Integer sortOrder,
        Boolean visible
) {
}
