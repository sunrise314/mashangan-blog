package com.mashangan.blog.web.admin.dto;

public record SocialLinkRequest(
        String platform,
        String label,
        String url,
        String iconClass,
        Integer priority,
        Boolean enabled
) {
}
