package com.mashangan.blog.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 后台 SPA 侧边栏导航项。数据库可配置化，对应 admin_nav 表。
 * path 以 /admin/ 开头视为 SPA 内部路由，否则按外部链接处理。
 */
@Data
@TableName("admin_nav")
public class AdminNav {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long parentId;

    private String menuName;

    private String path;

    private String icon;

    private Integer sortOrder;

    private Boolean visible;

    private OffsetDateTime createdAt;
}
