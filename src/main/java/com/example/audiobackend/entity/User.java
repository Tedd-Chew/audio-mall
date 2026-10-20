package com.example.audiobackend.entity;

import com.example.audiobackend.constant.UserRole;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 用户。买家和后台管理员共用一张表，靠 role 区分。
 * 纯 POJO，不带 ORM 注解。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class User {

    /** 序列化成字符串：Long 超过 2^53 时前端 JS 会丢精度 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    private String username;

    /** BCrypt 加密后的密码，永远不返回给前端 */
    private String password;

    private String nickname;

    private String phone;

    /** 见 {@link UserRole}：BUYER / ADMIN */
    private String role;

    /** 1 正常 / 0 禁用 */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
