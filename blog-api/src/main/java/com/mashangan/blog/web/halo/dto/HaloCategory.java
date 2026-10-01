package com.mashangan.blog.web.halo.dto;

import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/** 对齐 Halo Category 扩展资源结构 */
public record HaloCategory(Meta metadata, Spec spec, Status status, Integer postCount) {

    public record Spec(String displayName, String slug, String cover, String description,
                       Integer priority, Boolean hideFromList, List<String> children,
                       Boolean preventParentPostCascadeQuery, String template) {
    }

    /** 直接归属文章数为 0 时 Halo 省略计数字段（如纯父分类） */
    public record Status(String permalink, Integer postCount, Integer visiblePostCount) {
    }

    public static Meta meta(String haloName, OffsetDateTime createdAt, String section, String status) {
        Map<String, String> labels = section == null ? null : Map.of("haloweb.section", section);
        // 连载状态注解：blog-web 的 getTutorialStatus() 据此渲染「连载中/已完结」徽章
        Map<String, String> annotations = Map.of(
                "tutorial.halo.run/status",
                status == null || status.isBlank() ? "updating" : status);
        return new Meta(haloName, Time.iso(createdAt), labels, annotations);
    }

    public static String permalink(String slug) {
        return "/categories/" + UriUtils.encodePathSegment(slug, StandardCharsets.UTF_8);
    }
}
