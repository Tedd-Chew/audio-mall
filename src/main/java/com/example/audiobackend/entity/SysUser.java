package com.example.audiobackend.entity;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

/**
 * 后台管理员。纯 POJO，不带 ORM 注解。
 * 跟买家账号（User）是两码事，分表存放。
 */
@Data
public class SysUser {

    /** 序列化成字符串：Long 超过 2^53 时前端 JS 会丢精度 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    private String username;

    /** BCrypt 加密后的密码 */
    private String password;

    /** 角色：admin */
    private String role;
}
