package com.mashangan.blog.web.halo.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;

/** 对齐 Halo Post 扩展资源结构；HaloPostDetail 额外携带 content 段 */
public record HaloPost(Meta metadata, Spec spec, Status status,
                       List<HaloCategory> categories, List<TagRef> tags) {

    public record Excerpt(String raw, Boolean autoGenerate) {
    }

    public record Spec(String title, String slug, String cover, Excerpt excerpt,
                       String publishTime, List<String> categories, List<String> tags,
                       Integer priority, Boolean pinned, Boolean deleted, String visible,
                       Boolean allowComment) {
    }

    public record Status(String permalink, String excerpt,
                         String lastModifyTime, String phase) {
    }

    public record Content(String content, String raw) {
    }

    /** 文章详情：列表结构 + content；access 仅系列文章携带（付费墙标记） */
    public record Detail(Meta metadata, Spec spec, Status status,
                         List<HaloCategory> categories, List<TagRef> tags, Content content,
                         @JsonInclude(JsonInclude.Include.NON_NULL) Access access) {

        public HaloPost toListed() {
            return new HaloPost(metadata, spec, status, categories, tags);
        }
    }

    /** 系列章节访问控制信息：locked=true 时 content 已被服务端剥离 */
    public record Access(boolean locked, String seriesSlug, String seriesTitle,
                         int freeChapterCount, int chapterOrder, int totalChapters) {
    }

    public static String permalink(String slug) {
        return "/archives/" + UriUtils.encodePathSegment(slug, StandardCharsets.UTF_8);
    }

    public static String publishTime(OffsetDateTime time) {
        return Time.iso(time);
    }
}
