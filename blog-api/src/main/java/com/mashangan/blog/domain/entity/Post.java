package com.mashangan.blog.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@TableName("posts")
public class Post {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String haloName;

    private String title;

    private String slug;

    /** Halo 快照无 cover 键时为 NULL（省略），空串为 ""，迁移 upsert 需写入 NULL */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String cover;

    private String excerpt;

    /** spec.excerpt.raw：键存在即存值（可能 ""），无键为 NULL；迁移 upsert 需写入 NULL */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String specExcerpt;

    private Boolean autoExcerpt;

    private String contentHtml;

    private String contentRaw;

    /** HTML 或 MARKDOWN */
    private String rawType;

    private Boolean published;

    private Boolean pinned;

    private Integer priority;

    /** PUBLIC / PRIVATE / INTERNAL */
    private String visible;

    private Boolean allowComment;

    private Boolean deleted;

    private OffsetDateTime publishTime;

    /** 所属系列/项目 ID，可空 */
    private Long seriesId;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;
}
