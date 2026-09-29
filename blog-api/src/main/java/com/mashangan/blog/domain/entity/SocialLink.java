package com.mashangan.blog.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@TableName("social_links")
public class SocialLink {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** github / weibo / twitter / email / rss / custom */
    private String platform;
    private String label;
    private String url;
    private String iconClass;
    private Integer priority;
    private Boolean enabled;
    private OffsetDateTime createdAt;
}
