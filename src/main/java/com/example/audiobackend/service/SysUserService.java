package com.example.audiobackend.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.audiobackend.entity.SysUser;

public interface SysUserService extends IService<SysUser> {
    // 自定义方法：根据用户名查询用户
    SysUser getUserByUsername(String username);
}
