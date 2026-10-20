package com.example.audiobackend.mapper;

import com.example.audiobackend.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 用户数据访问。买家和管理员共用这张表。
 */
@Mapper
public interface UserMapper {

    /** 按用户名查，登录用。走 uk_username 唯一索引 */
    User selectByUsername(@Param("username") String username);

    /** 按 id 查 */
    User selectById(@Param("id") Long id);

    /** 新增，主键回填到 user.id */
    int insert(User user);
}
