package com.mashangan.blog.web.halo.dto;

import java.util.List;

/** 对齐 POST /indices/-/search 的请求与响应，标题高亮使用 <B> 包裹（与 Halo 一致） */
public class SearchDtos {

    public record Request(String keyword, int limit) {
    }

    public record Hit(String metadataName, String title, String description, String permalink,
                      List<String> categories, List<String> tags, boolean published,
                      String creationTimestamp, String updateTimestamp) {
    }

    public record Response(List<Hit> hits, long total) {
    }
}
