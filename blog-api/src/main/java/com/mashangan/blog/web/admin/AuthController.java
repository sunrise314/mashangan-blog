package com.mashangan.blog.web.admin;

import com.mashangan.blog.security.AdminPrincipal;
import com.mashangan.blog.service.AuthService;
import com.mashangan.blog.web.admin.dto.ChangePasswordRequest;
import com.mashangan.blog.web.admin.dto.LoginRequest;
import com.mashangan.blog.web.admin.dto.LoginResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public LoginResponse login(@RequestBody LoginRequest req) {
        return authService.login(req.username(), req.password());
    }

    /** 修改当前登录用户密码（须携带 Bearer Token） */
    @PostMapping("/password")
    public Map<String, String> changePassword(@AuthenticationPrincipal AdminPrincipal principal,
                                              @RequestBody ChangePasswordRequest req) {
        authService.changePassword(principal.id(), req.oldPassword(), req.newPassword());
        return Map.of("message", "密码已修改，请使用新密码重新登录");
    }
}
