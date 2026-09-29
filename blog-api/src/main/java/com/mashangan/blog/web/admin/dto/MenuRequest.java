package com.mashangan.blog.web.admin.dto;

public record MenuRequest(
        String displayName,
        Boolean isPrimary
) {
}
