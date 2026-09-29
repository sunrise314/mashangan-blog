package com.mashangan.blog.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@TableName("categories")
public class Category {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 迁移自 Halo 的 metadata.name，兼容层用它作为对外标识 */
    private String haloName;

    private String slug;

    private String displayName;

    private String cover;

    private String description;

    private Integer priority;

    private Boolean hideFromList;

    private Long parentId;

    /** 栏目分区标识，如 interview（八股题库），对应 Halo label haloweb.section */
    private String section;

    private String template;

    private Boolean preventParentCascadeQuery;

    private OffsetDateTime createdAt;
}
