package com.mashangan.blog.web.halo.dto;

import java.util.List;

/** 对齐 Halo menus/- 聚合结构：根级 displayName、status 为 targetRef 解析后的最终值、children 恒为数组 */
public record HaloMenuItem(String displayName, Meta metadata, Spec spec, Status status,
                           List<HaloMenuItem> children) {

    public record Spec(String displayName, String href, String target, Integer priority,
                       String menuName) {
    }

    public record Status(String displayName, String href) {
    }
}
