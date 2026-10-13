package com.mashangan.blog.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@TableName("vip_members")
public class VipMember {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long codeId;

    private String nickname;

    /** false = 冻结（复用名额不返还） */
    private Boolean enabled;

    private OffsetDateTime createdAt;
}
