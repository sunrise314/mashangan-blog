package com.mashangan.blog.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@TableName("industry")
public class Industry {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String slug;

    private String name;

    private String intro;

    private Integer sort;

    private OffsetDateTime createdAt;
}
