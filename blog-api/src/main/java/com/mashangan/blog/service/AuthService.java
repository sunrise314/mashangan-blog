package com.mashangan.blog.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mashangan.blog.domain.entity.User;
import com.mashangan.blog.mapper.UserMapper;
import com.mashangan.blog.security.JwtService;
import com.mashangan.blog.web.admin.dto.LoginResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public LoginResponse login(String username, String password) {
        User user = userMapper.selectOne(new QueryWrapper<User>()
                .eq("username", username)
                .eq("enabled", true));
        if (user == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户名或密码错误");
        }
        String token = jwtService.generate(user.getId(), user.getUsername(), user.getRole());
        return new LoginResponse(token, user.getUsername(), user.getDisplayName(), user.getRole());
    }

    /**
     * 修改当前登录用户密码：校验旧密码后写入新 bcrypt 哈希。
     * 注意：环境变量 ADMIN_PASSWORD 只在首次播种管理员时生效，改密码以本方法为准。
     */
    public void changePassword(Long userId, String oldPassword, String newPassword) {
        User user = userMapper.selectById(userId);
        if (user == null || !Boolean.TRUE.equals(user.getEnabled())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        if (oldPassword == null || !passwordEncoder.matches(oldPassword, user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "旧密码不正确");
        }
        if (newPassword == null || newPassword.length() < 6) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "新密码至少 6 位");
        }
        User update = new User();
        update.setId(user.getId());
        update.setPasswordHash(passwordEncoder.encode(newPassword));
        userMapper.updateById(update);
    }
}
