package com.example.audiobackend.vo;

import lombok.Data;

/**
 * 登录 / 注册成功后的返回。
 * 不含 password，任何情况下都不该把密码字段传到前端。
 */
@Data
public class LoginVO {

    private String token;

    private Long userId;

    private String username;

    private String nickname;

    /** BUYER / ADMIN */
    private String role;
}
