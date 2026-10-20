package com.example.audiobackend.service;

import com.example.audiobackend.entity.SysUser;

/**
 * 后台管理员服务。
 */
public interface SysUserService {

    /** 按用户名查，登录时用 */
    SysUser getUserByUsername(String username);
}
