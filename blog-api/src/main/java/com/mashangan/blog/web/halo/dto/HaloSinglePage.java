package com.mashangan.blog.web.halo.dto;

import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;

/** 对齐 Halo SinglePage 扩展资源结构 */
public record HaloSinglePage(Meta metadata, Spec spec, Status status, Content content) {

    public record Spec(String title, String slug, String publishTime) {
    }

    public record Status(String permalink, String lastModifyTime,
                         String phase) {
    }

    public record Content(String content, String raw) {
    }

    public static String permalink(String slug) {
        return "/" + UriUtils.encodePathSegment(slug, StandardCharsets.UTF_8);
    }
}
