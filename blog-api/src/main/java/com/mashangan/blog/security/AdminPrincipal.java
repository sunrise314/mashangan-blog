package com.mashangan.blog.security;

/** 当前登录管理员的轻量主体 */
public record AdminPrincipal(Long id, String username, String role) {
}
