package com.example.audiobackend.mapper;

import com.example.audiobackend.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 后台管理员数据访问。
 */
@Mapper
public interface SysUserMapper {

    SysUser selectByUsername(@Param("username") String username);
}
