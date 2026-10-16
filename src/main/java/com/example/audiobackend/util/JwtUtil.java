//JWT 令牌生成与校验工具类
package com.example.audiobackend.util;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import java.util.Date;
import javax.crypto.SecretKey;
@Component // 把 JwtUtil 注册到 Spring 容器,方便其他类注入使用
public class JwtUtil {
    //自定义密钥：必须足够长，建议 32 个字符，否则加密会报错
    //作用： 签名JWT时使用，只有后端知道，防止Token被篡改，//后期放入配置文件更安全，避免硬编码在代码里被泄露
    // 从 application.yml 读取配置
    @Value("${jwt.secret}")
    private String SECRET_KEY;//为什么去掉 static final：@Value 无法注入静态变量，且 final 要求变量初始化时必须有值，和 Spring 注入时机冲突；
// ③ Token 过期时间：2小时（单位：毫秒），避免 Token 永久有效
    // 从 application.yml 读取配置
    @Value("${jwt.expire-time}")
    private long EXPIRE_TIME;

    /**
     * 生成 Token
     * @return 加密后的 Token 字符串
     */

    public long getExpireTime() {
        return this.EXPIRE_TIME;
    }
    public String generateToken(String username, Long userId, String role) {
        // ④ 生成加密密钥：用 HS256 算法，把字符串密钥转成 SecretKey 对象
        SecretKey key = Keys.hmacShaKeyFor(SECRET_KEY.getBytes());
        
        // ⑤ 构建 Token
        return Jwts.builder()
            .setSubject(username)
            .claim("userId", userId) // 自定义载荷：用户ID
            .claim("role", role)     // 自定义载荷：用户角色（admin/normal）
            .setIssuedAt(new Date())
            .setExpiration(new Date(System.currentTimeMillis() + EXPIRE_TIME))
            .signWith(key)
            .compact();
    }

    /**
     * 校验 Token 是否有效（没过期 + 签名正确）
     * @param token 前端传来的 Token
     */
    // JwtUtil 的 validateToken 方法（改为 void，去掉 catch）
    public void validateToken(String token) {
        SecretKey key = Keys.hmacShaKeyFor(SECRET_KEY.getBytes());
        Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token);
        // 无异常 = 校验通过，有异常直接抛（JJWT 自带的 ExpiredJwtException/SignatureException 等）
    }


    

    /**
     * 从 Token 中解析出用户名（方便后续业务使用）
     */
    //相当于从token中还原出username,比如想限制只有admin才能访问某些接口，就可以先调用这个方法拿到username，再判断是否是admin
    public String getUsernameFromToken(String token) {
        SecretKey key = Keys.hmacShaKeyFor(SECRET_KEY.getBytes());
        // ⑬ 解析 Token 拿到载荷（Claims 就是载荷的封装类）
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
        // ⑭ 从载荷中取出用户名（对应 setSubject 存的内容）
        return claims.getSubject();
    }
    public Long getUserIdFromToken(String token) {
    SecretKey key = Keys.hmacShaKeyFor(SECRET_KEY.getBytes());
    Claims claims = Jwts.parserBuilder()
            .setSigningKey(key)
            .build()
            .parseClaimsJws(token)
            .getBody();
    // 取出存入的userId
    return claims.get("userId", Long.class);
    }

    // 新增：从 Token 解析角色
    public String getRoleFromToken(String token) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(Keys.hmacShaKeyFor(SECRET_KEY.getBytes()))
                .build()
                .parseClaimsJws(token)
                .getBody();
        return claims.get("role", String.class);
    }
}
