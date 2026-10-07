package com.mashangan.blog.web.halo.dto;

/** 标签云卡片：TagRef 结构 + 已发布文章数 */
public record TagCard(MetaRef metadata, Spec spec, int postCount) {

    public record MetaRef(String name) {
    }

    public record Spec(String displayName, String slug) {
    }
}
