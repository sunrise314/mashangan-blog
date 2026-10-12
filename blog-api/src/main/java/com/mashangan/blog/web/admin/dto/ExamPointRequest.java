package com.mashangan.blog.web.admin.dto;

/** 项目卡片面试考点条目 */
public record ExamPointRequest(
        Integer sort,
        String point,
        String detail
) {
}
