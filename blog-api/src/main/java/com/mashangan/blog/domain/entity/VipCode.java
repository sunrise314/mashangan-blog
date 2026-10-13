package com.mashangan.blog.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@TableName("vip_codes")
public class VipCode {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String code;

    /** 激活名额上限 */
    private Integer maxUses;

    private Integer usedCount;

    private String note;

    private Boolean enabled;

    private OffsetDateTime createdAt;
}
