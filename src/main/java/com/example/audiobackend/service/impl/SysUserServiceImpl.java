package com.example.audiobackend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.audiobackend.entity.SysUser;
import com.example.audiobackend.mapper.SysUserMapper;
import com.example.audiobackend.service.SysUserService;
import org.springframework.stereotype.Service;

// 关键：@Service 注解让Spring管理
@Service
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements SysUserService {

    // 根据用户名查询用户（封装MyBatis-Plus查询逻辑）
    @Override
    public SysUser getUserByUsername(String username) {
        // 构造查询条件：用户名等于传入值
        LambdaQueryWrapper<SysUser> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SysUser::getUsername, username);
        // 执行查询（BaseMapper的selectOne方法）
        return this.baseMapper.selectOne(queryWrapper);
    }
}

