package com.mashangan.blog.web.admin.dto;

/** 修改密码请求体 */
public record ChangePasswordRequest(String oldPassword, String newPassword) {
}
