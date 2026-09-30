package com.mashangan.blog.web.admin.dto;

import java.util.List;

/** 分类拖拽排序：ids 为调整后的完整展示顺序（第 1 位最靠前） */
public record ReorderRequest(List<Long> ids) {
}
