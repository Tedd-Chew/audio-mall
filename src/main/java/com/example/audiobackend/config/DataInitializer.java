package com.example.audiobackend.config;

import com.example.audiobackend.constant.UserRole;
import com.example.audiobackend.entity.User;
import com.example.audiobackend.mapper.UserMapper;
import com.example.audiobackend.util.PasswordUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * 启动时的数据初始化。
 * 管理员账号不走注册接口（注册只能是买家），所以第一次启动在这里建一个。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private static final String DEFAULT_ADMIN_USERNAME = "admin";
    private static final String DEFAULT_ADMIN_PASSWORD = "admin123";

    private final UserMapper userMapper;
    private final PasswordUtil passwordUtil;

    @Override
    public void run(String... args) {
        if (userMapper.selectByUsername(DEFAULT_ADMIN_USERNAME) != null) {
            return;
        }

        User admin = new User();
        admin.setUsername(DEFAULT_ADMIN_USERNAME);
        admin.setPassword(passwordUtil.encrypt(DEFAULT_ADMIN_PASSWORD));
        admin.setNickname("管理员");
        admin.setRole(UserRole.ADMIN);
        admin.setStatus(1);
        userMapper.insert(admin);

        log.info("已创建默认管理员：{} / {}（生产环境请立刻改密码）", DEFAULT_ADMIN_USERNAME, DEFAULT_ADMIN_PASSWORD);
    }
}
