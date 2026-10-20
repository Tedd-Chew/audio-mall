package com.example.audiobackend.service.impl;

import com.example.audiobackend.entity.SysUser;
import com.example.audiobackend.mapper.SysUserMapper;
import com.example.audiobackend.service.SysUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 后台管理员服务。
 * 不继承任何框架基类 —— 换持久层框架时这个类最多改一下 Mapper 调用。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysUserServiceImpl implements SysUserService {

    private final SysUserMapper sysUserMapper;

    @Override
    public SysUser getUserByUsername(String username) {
        return sysUserMapper.selectByUsername(username);
    }
}
