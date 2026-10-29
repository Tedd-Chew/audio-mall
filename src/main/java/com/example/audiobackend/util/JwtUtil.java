//JWT 令牌生成与校验工具类
package com.example.audiobackend.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 的签发与解析。
 *
 * <p><b>密钥和解析器都在启动时建一次，之后复用。</b>
 * {@code Jwts.parserBuilder().build()} 不只是 new 几个对象 —— jjwt 内部会用
 * {@code ServiceLoader} 扫 classpath、打开 jar 读 {@code META-INF/services} 声明，
 * Windows 上还会触发 {@code GetFileAttributesEx} 系统调用。
 *
 * <p>原来每个请求要建 4 次解析器（拦截器里 validate / getUserId / getRole 各一次，
 * 控制器里 currentUserId 又一次），200 并发下请求线程全部堵在 jar 的全局缓存上，
 * 吞吐从 15,545（不碰 JWT 的空接口）掉到 757。线程栈证据见 docs/压测记录.md。
 *
 * <p>为什么密钥可以共用一个：验签不需要知道"这是谁"，只需要知道"用哪把密钥"。
 * 想单独作废某个用户的 token，靠的是 Redis 里的 {@code user:token:{userId}}，不是密钥。
 */
@Component // 把 JwtUtil 注册到 Spring 容器,方便其他类注入使用
public class JwtUtil {

    // 自定义密钥：必须足够长（HS256 至少 32 字节），否则 Keys.hmacShaKeyFor 会抛 WeakKeyException
    // 作用：签名 JWT，只有后端知道，防止 Token 被篡改
    @Value("${jwt.secret}")
    private String secret;

    // Token 有效期（毫秒），同时决定 Redis 里 user:token:{userId} 的 TTL
    @Value("${jwt.expire-time}")
    private long expireTime;

    /** 签名与验签共用的密钥，启动时推导一次 */
    private SecretKey key;

    /**
     * 解析器，启动时构建一次。
     *
     * <p>jjwt 0.11 里 JwtParser 建好之后是不可变的，里面的解析器、算法、密钥都只读，
     * 所以可以安全地被所有请求线程共用。
     */
    private JwtParser parser;

    @PostConstruct
    void init() {
        // 显式指定 UTF-8：不写字符集就用 JVM 默认字符集，换个默认字符集的机器会算出不同的字节，
        // 签出来的 token 到另一台机器上就验不过
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.parser = Jwts.parserBuilder().setSigningKey(key).build();
    }

    public long getExpireTime() {
        return expireTime;
    }

    /**
     * 生成 Token
     * @return 加密后的 Token 字符串
     */
    public String generateToken(String username, Long userId, String role) {
        return Jwts.builder()
            .setSubject(username)
            .claim("userId", userId) // 自定义载荷：用户ID
            .claim("role", role)     // 自定义载荷：用户角色（admin/normal）
            .setIssuedAt(new Date())
            .setExpiration(new Date(System.currentTimeMillis() + expireTime))
            .signWith(key)
            .compact();
    }

    /**
     * 解析并验签，一次拿到全部 claims。
     *
     * <p>签名错误、已过期、格式非法都会抛异常（SignatureException / ExpiredJwtException 等），
     * 由调用方决定怎么处理。
     *
     * <p><b>需要 userId 和 role 的地方不要各解析一遍</b> —— 把这份 claims 传下去就行。
     */
    public Claims parse(String token) {
        return parser.parseClaimsJws(token).getBody();
    }

    /** 兼容旧调用点：内部复用缓存的解析器，不再有额外开销 */
    public Long getUserIdFromToken(String token) {
        return parse(token).get("userId", Long.class);
    }

    /** 兼容旧调用点：内部复用缓存的解析器 */
    public String getRoleFromToken(String token) {
        return parse(token).get("role", String.class);
    }
}
