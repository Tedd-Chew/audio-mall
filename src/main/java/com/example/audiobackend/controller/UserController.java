package com.example.audiobackend.controller;

import com.example.audiobackend.common.Result;
import com.example.audiobackend.constant.RedisConstant;
import com.example.audiobackend.entity.SysUser;
import com.example.audiobackend.service.SysUserService;
import com.example.audiobackend.util.JwtUtil;
import com.example.audiobackend.util.PasswordUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/user")
@CrossOrigin(originPatterns = "*")
public class UserController {

    private static final Logger log = LoggerFactory.getLogger(UserController.class);

    private final JwtUtil jwtUtil;
    private final SysUserService sysUserService;
    private final PasswordUtil passwordUtil;
    private final StringRedisTemplate stringRedisTemplate;

    public UserController(
            JwtUtil jwtUtil,
            SysUserService sysUserService,
            PasswordUtil passwordUtil,
            StringRedisTemplate stringRedisTemplate
    ) {
        this.jwtUtil = jwtUtil;
        this.sysUserService = sysUserService;
        this.passwordUtil = passwordUtil;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @PostMapping("/login")
    public Result<String> login(@RequestBody SysUser sysUser) {//收到时Spring自动帮我转成了SysUser对象
        if (sysUser == null || sysUser.getUsername() == null || sysUser.getUsername().trim().isEmpty()) {
            log.warn("登录失败：用户名为空");
            return Result.error("用户名不能为空");
        }
        if (sysUser.getPassword() == null || sysUser.getPassword().trim().isEmpty()) {
            log.warn("登录失败：密码为空");
            return Result.error("密码不能为空");
        }

        try {
            log.info("用户登录请求：{}", sysUser.getUsername());

            SysUser dbUser = sysUserService.getUserByUsername(sysUser.getUsername());
            if (dbUser == null) {
                log.warn("登录失败：用户不存在，用户名：{}", sysUser.getUsername());
                return Result.error("用户名或密码错误");
            }

            if (!passwordUtil.match(sysUser.getPassword(), dbUser.getPassword())) {
                log.warn("登录失败：密码错误，用户名：{}", sysUser.getUsername());
                return Result.error("用户名或密码错误");
            }

            String token = jwtUtil.generateToken(dbUser.getUsername(), dbUser.getId(), dbUser.getRole());

            String redisKey = RedisConstant.TOKEN_PREFIX + dbUser.getId();
            long expireSeconds = jwtUtil.getExpireTime() / 1000;
            stringRedisTemplate.opsForValue().set(redisKey, token, expireSeconds, TimeUnit.SECONDS);

            log.info("用户登录成功：{}，用户ID：{}", dbUser.getUsername(), dbUser.getId());
            return Result.success(token);
        } catch (Exception e) {
            log.error("用户登录异常：{}，错误：{}", sysUser.getUsername(), e.getMessage(), e);
            return Result.error("登录失败：" + e.getMessage());
        }
    }

    @PostMapping("/logout")
    public Result<String> logout(@RequestHeader("Authorization") String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("退出登录失败：Token格式错误");
            return Result.error("Token格式错误");
        }

        try {
            String token = authHeader.replace("Bearer ", "").trim();

            Long userId = jwtUtil.getUserIdFromToken(token);

            if (userId == null) {
                log.warn("退出登录失败：无法从Token中解析用户ID");
                return Result.error("无效的Token");
            }

            String redisKey = RedisConstant.TOKEN_PREFIX + userId;
            stringRedisTemplate.delete(redisKey);

            log.info("用户退出登录成功：用户ID {}", userId);
            return Result.success("退出登录成功");
        } catch (Exception e) {
            log.error("退出登录失败：{}", e.getMessage(), e);
            return Result.error("退出登录失败：" + e.getMessage());
        }
    }
}