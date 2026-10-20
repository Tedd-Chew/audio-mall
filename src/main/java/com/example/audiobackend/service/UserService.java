package com.example.audiobackend.service;

import com.example.audiobackend.dto.LoginDTO;
import com.example.audiobackend.dto.RegisterDTO;
import com.example.audiobackend.entity.User;
import com.example.audiobackend.vo.LoginVO;

/**
 * 用户服务。买家和管理员共用一套登录。
 */
public interface UserService {

    /** 注册，只能注册成买家 */
    LoginVO register(RegisterDTO dto);

    /** 登录，成功返回 token */
    LoginVO login(LoginDTO dto);

    /** 退出登录，删掉 Redis 里的 token */
    void logout(Long userId);

    User getById(Long userId);
}
