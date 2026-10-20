package com.example.audiobackend.service.impl;

import com.example.audiobackend.constant.RedisConstant;
import com.example.audiobackend.constant.UserRole;
import com.example.audiobackend.dto.LoginDTO;
import com.example.audiobackend.dto.RegisterDTO;
import com.example.audiobackend.entity.User;
import com.example.audiobackend.exception.BusinessException;
import com.example.audiobackend.mapper.UserMapper;
import com.example.audiobackend.service.UserService;
import com.example.audiobackend.util.JwtUtil;
import com.example.audiobackend.util.PasswordUtil;
import com.example.audiobackend.vo.LoginVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final PasswordUtil passwordUtil;
    private final JwtUtil jwtUtil;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginVO register(RegisterDTO dto) {
        // username 有唯一索引，这里先查一次是为了给出可读的提示，
        // 真正的防重靠数据库约束（并发下两个请求同时通过这里，只有一条能插入成功）
        if (userMapper.selectByUsername(dto.getUsername()) != null) {
            throw new BusinessException("用户名已存在");
        }

        User user = new User();
        user.setUsername(dto.getUsername());
        user.setPassword(passwordUtil.encrypt(dto.getPassword()));
        user.setNickname(dto.getNickname() == null || dto.getNickname().isBlank()
                ? dto.getUsername() : dto.getNickname());
        user.setPhone(dto.getPhone());
        // 写死买家角色：注册接口永远不产生管理员
        user.setRole(UserRole.BUYER);
        user.setStatus(1);

        userMapper.insert(user);
        log.info("用户注册成功，id={}，username={}", user.getId(), user.getUsername());
        return issueToken(user);
    }

    @Override
    public LoginVO login(LoginDTO dto) {
        User user = userMapper.selectByUsername(dto.getUsername());

        // 用户不存在和密码错误返回同一句话，避免被拿来枚举用户名
        if (user == null || !passwordUtil.match(dto.getPassword(), user.getPassword())) {
            throw new BusinessException("用户名或密码错误");
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BusinessException("账号已被禁用");
        }

        log.info("用户登录成功，id={}，username={}", user.getId(), user.getUsername());
        return issueToken(user);
    }

    @Override
    public void logout(Long userId) {
        stringRedisTemplate.delete(RedisConstant.USER_TOKEN + userId);
        log.info("用户退出登录，id={}", userId);
    }

    @Override
    public User getById(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        return user;
    }

    /**
     * 签发 token，并往 Redis 存一份 —— 存了才能支持强制下线。
     * 多实例部署时，两个实例共用 Redis 里的这份，token 才是一致的。
     */
    private LoginVO issueToken(User user) {
        String token = jwtUtil.generateToken(user.getUsername(), user.getId(), user.getRole());
        long expireSeconds = jwtUtil.getExpireTime() / 1000;
        stringRedisTemplate.opsForValue()
                .set(RedisConstant.USER_TOKEN + user.getId(), token, expireSeconds, TimeUnit.SECONDS);

        LoginVO vo = new LoginVO();
        vo.setToken(token);
        vo.setUserId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setRole(user.getRole());
        return vo;
    }
}
