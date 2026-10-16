package com.example.audiobackend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

@Data
@TableName("sys_user") // 对应数据库表名
public class SysUser {
    
    @TableId(type = IdType.AUTO)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id; // 用户ID，主键，自增
    
    private String username; // 用户名
    
    private String password; // 密码（实际开发中应加密存储）
    
    private String role; // 角色（如 admin/user）
}