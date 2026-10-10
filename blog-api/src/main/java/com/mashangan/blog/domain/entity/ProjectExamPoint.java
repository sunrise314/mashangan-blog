package com.mashangan.blog.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("project_exam_point")
public class ProjectExamPoint {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectId;

    private Integer sort;

    private String point;

    private String detail;
}
