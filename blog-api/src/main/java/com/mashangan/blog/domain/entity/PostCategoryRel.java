package com.mashangan.blog.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("post_categories")
public class PostCategoryRel {

    @TableId(type = IdType.INPUT)
    private Long postId;

    private Long categoryId;
}
