package com.mashangan.blog.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@TableName("series")
public class Series {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String slug;

    private String title;

    private String cover;

    private String description;

    /** updating | complete */
    private String status;

    /** 免费章节数，0 表示全部免费 */
    private Integer freeChapterCount;

    private Integer sortOrder;

    /** 下架标记：true 时前台 /column 卡片列表不展示（详情页与 sitemap 不受影响） */
    private Boolean hidden;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;
}
