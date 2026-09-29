package com.mashangan.blog.web.halo.dto;

import java.util.Map;

/** Halo 扩展资源的 metadata 段 */
public record Meta(String name, String creationTimestamp, Map<String, String> labels,
                   Map<String, String> annotations) {
}
