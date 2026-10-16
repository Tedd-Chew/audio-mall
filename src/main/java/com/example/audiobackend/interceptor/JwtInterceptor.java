package com.example.audiobackend.interceptor;

import com.example.audiobackend.constant.RedisConstant;
import com.example.audiobackend.exception.BusinessException;
import com.example.audiobackend.util.JwtUtil;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Component
public class JwtInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(JwtInterceptor.class);


    final private JwtUtil jwtUtil;
    final private StringRedisTemplate stringRedisTemplate;

    public JwtInterceptor(JwtUtil jwtUtil,StringRedisTemplate stringRedisTemplate)
    {
        this.jwtUtil = jwtUtil;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override//这里spring MVC会自动传参。
    public boolean preHandle(HttpServletRequest request, @NotNull HttpServletResponse response, @NotNull Object handler) throws Exception {
        String token = request.getHeader("Authorization");
        String requestUri = request.getRequestURI();

        if (token == null || token.isEmpty()) {
            log.warn("请求未携带Token，URI：{}", requestUri);
            throw new BusinessException(401, "未登录，请先登录！");
        }

        if (token.startsWith("Bearer ")) {
            token = token.replace("Bearer ", "").trim();
        }
        
        try {
            jwtUtil.validateToken(token);
        } catch (Exception e) {
            log.warn("Token校验失败，URI：{}，错误：{}", requestUri, e.getMessage());
            throw new BusinessException(401, "Token无效或已过期");
        }

        try {
            Long userId = jwtUtil.getUserIdFromToken(token);
            if (userId == null) {
                log.warn("无法从Token解析用户ID，URI：{}", requestUri);
                throw new BusinessException(401, "Token解析失败");
            }

            String redisKey = RedisConstant.TOKEN_PREFIX + userId;

            if (!stringRedisTemplate.hasKey(redisKey)) {
                log.warn("Token已失效（Redis中不存在），用户ID：{}，URI：{}", userId, requestUri);
                throw new BusinessException(401, "Token已失效（已退出登录）！");
            }

            String redisToken = stringRedisTemplate.opsForValue().get(redisKey);
            if (!token.equals(redisToken)) {
                log.warn("Token不匹配（账号在其他设备登录），用户ID：{}，URI：{}", userId, requestUri);
                throw new BusinessException(401, "Token已失效（账号在其他设备登录）！");
            }

            String role = jwtUtil.getRoleFromToken(token);
            if (requestUri.startsWith("/api/admin/") && !"admin".equals(role)) {
                log.warn("无管理员权限，用户ID：{}，角色：{}，URI：{}", userId, role, requestUri);
                throw new BusinessException(403, "无管理员权限，禁止访问！");
            }

            log.debug("Token校验通过，用户ID：{}，URI：{}", userId, requestUri);
            return true;

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Token解析异常，URI：{}，错误：{}", requestUri, e.getMessage(), e);
            throw new BusinessException(401, "Token解析失败：" + e.getMessage());
        }
    }
}