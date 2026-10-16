package com.example.audiobackend.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * BCrypt密码加密工具类
 * 核心：加密 + 密码校验
 */
@Component // 交给Spring管理，方便注入
public class PasswordUtil {

    // 创建BCrypt加密器（固定写法）
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    /**
     * 加密明文密码
     * @param rawPassword 明文密码（比如123456）
     * @return 密文密码（比如$2a$10$xxx）
     */
    public String encrypt(String rawPassword) {
        return encoder.encode(rawPassword);
    }

    /**
     * 校验密码（明文 vs 密文）
     * @param rawPassword 前端传的明文密码
     * @param encodedPassword 数据库存的密文密码
     * @return true=匹配，false=不匹配
     */
    public boolean match(String rawPassword, String encodedPassword) {
        return encoder.matches(rawPassword, encodedPassword);
    }

    public static void main(String[] args) {
    PasswordUtil util = new PasswordUtil();
    System.out.println(util.encrypt("123456"));
}
}
