package com.mashangan.blog.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

/** 文章修订快照：每次后台更新或回滚前保存旧版本 */
@Data
@TableName("post_revisions")
public class PostRevision {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long postId;
    private Integer revisionNo;
    private String title;
    private String slug;
    private String cover;
    private String excerpt;
    private String contentHtml;
    private String contentRaw;
    private String rawType;
    private Boolean published;
    private Boolean pinned;
    private Integer priority;
    private String visible;
    private Boolean allowComment;
    private OffsetDateTime createdAt;
}
