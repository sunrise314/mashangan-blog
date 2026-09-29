package com.mashangan.blog.web.halo.dto;

import java.time.OffsetDateTime;

/** 统一 ISO 时间输出，空值输出 null（由全局 NON_NULL 决定是否省略字段） */
public final class Time {

    private Time() {
    }

    public static String iso(OffsetDateTime time) {
        return time == null ? null : time.toInstant().toString();
    }
}
