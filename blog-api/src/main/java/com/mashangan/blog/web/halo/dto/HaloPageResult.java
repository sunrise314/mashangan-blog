package com.mashangan.blog.web.halo.dto;

import java.util.List;

/** 对齐 Halo 分页信封 ListResult（1-based） */
public record HaloPageResult<T>(int page, int size, long total, List<T> items,
                                boolean first, boolean last, boolean hasNext,
                                boolean hasPrevious, int totalPages) {

    public static <T> HaloPageResult<T> of(long current, long size, long total, List<T> items) {
        int totalPages = size <= 0 ? 0 : (int) ((total + size - 1) / size);
        int page = (int) current;
        return new HaloPageResult<>(page, (int) size, total, items,
                page <= 1, page >= totalPages || totalPages == 0,
                page < totalPages, page > 1, totalPages);
    }
}
