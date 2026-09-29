package com.mashangan.blog.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@TableName("menu_items")
public class MenuItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String haloName;

    private Long menuId;

    private Long parentId;

    private String displayName;

    private String href;

    /** _blank / _self 等 */
    private String target;

    private Integer priority;

    /** category / post / page / custom */
    private String refKind;

    private Long refId;

    private OffsetDateTime createdAt;
}
