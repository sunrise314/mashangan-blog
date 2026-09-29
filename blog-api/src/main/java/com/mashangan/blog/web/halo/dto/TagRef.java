package com.mashangan.blog.web.halo.dto;

/** 文章列表中内嵌的标签引用，仅暴露前端需要的 name/slug/displayName */
public record TagRef(MetaRef metadata, Spec spec) {

    public record MetaRef(String name) {
    }

    public record Spec(String displayName, String slug) {
    }
}
