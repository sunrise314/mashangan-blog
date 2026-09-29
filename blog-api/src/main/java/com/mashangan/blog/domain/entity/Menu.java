package com.mashangan.blog.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@TableName("menus")
public class Menu {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String haloName;

    private String displayName;

    private Boolean isPrimary;

    private OffsetDateTime createdAt;
}
