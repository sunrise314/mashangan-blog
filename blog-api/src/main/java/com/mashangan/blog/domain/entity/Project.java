package com.mashangan.blog.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@TableName("project")
public class Project {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long industryId;

    /** 非空 = 已开更（挂真实系列）；NULL = 筹备中 */
    private Long seriesId;

    private String slug;

    private String title;

    private String jdFreq;

    private String summary;

    private Integer sort;

    /** 是否在前台展示；false 时不出现在行业详情页与项目落地页 */
    private Boolean visible;

    private OffsetDateTime createdAt;
}
