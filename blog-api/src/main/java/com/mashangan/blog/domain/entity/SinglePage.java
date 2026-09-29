package com.mashangan.blog.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@TableName("single_pages")
public class SinglePage {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String haloName;

    private String title;

    private String slug;

    private String contentHtml;

    private String contentRaw;

    private String rawType;

    private Boolean published;

    private Boolean deleted;

    private OffsetDateTime publishTime;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;
}
