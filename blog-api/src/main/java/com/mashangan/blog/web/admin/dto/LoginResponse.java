package com.mashangan.blog.web.admin.dto;

public record LoginResponse(String token, String username, String displayName, String role) {
}
