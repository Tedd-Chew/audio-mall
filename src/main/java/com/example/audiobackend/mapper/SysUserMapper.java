package com.example.audiobackend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.audiobackend.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;

// 关键：@Mapper 注解让MyBatis-Plus扫描到
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {
    // 继承BaseMapper后，自动拥有CRUD方法，无需手写SQL
}