package com.mashangan.blog.web.halo.dto;

import java.util.List;

/** 对齐 Halo Menu / MenuItem 结构，menuItems 为按 priority 排好序的树 */
public record HaloMenu(Meta metadata, Spec spec, List<HaloMenuItem> menuItems) {

    public record Spec(String displayName, List<String> menuItems) {
    }
}
