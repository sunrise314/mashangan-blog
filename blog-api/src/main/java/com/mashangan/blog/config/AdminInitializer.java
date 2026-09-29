package com.mashangan.blog.config;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mashangan.blog.domain.entity.User;
import com.mashangan.blog.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

/** 首次启动时若无任何用户，则创建默认管理员（凭据由环境变量覆盖）。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminInitializer implements ApplicationRunner {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.username:admin}")
    private String adminUsername;

    @Value("${app.admin.password:Admin@2026}")
    private String adminPassword;

    @Override
    public void run(ApplicationArguments args) {
        Long count = userMapper.selectCount(new QueryWrapper<>());
        if (count != null && count > 0) {
            return;
        }
        User admin = new User();
        admin.setUsername(adminUsername);
        admin.setPasswordHash(passwordEncoder.encode(adminPassword));
        admin.setDisplayName("管理员");
        admin.setRole("ADMIN");
        admin.setEnabled(true);
        admin.setCreatedAt(OffsetDateTime.now());
        userMapper.insert(admin);
        log.info("已创建初始管理员账号：{}（请尽快通过 ADMIN_USERNAME/ADMIN_PASSWORD 环境变量修改默认凭据）", adminUsername);
    }
}
