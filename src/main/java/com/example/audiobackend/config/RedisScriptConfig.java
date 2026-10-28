package com.example.audiobackend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.List;

/**
 * Redis Lua 脚本注册成 Bean，只加载一次。
 *
 * <p>不要每次调用都 new DefaultRedisScript —— 那样每次都要读文件、重新编译脚本，
 * 在高并发下是纯浪费。注册成单例 Bean，Spring 会复用同一个 SHA，走 EVALSHA。
 */
@Configuration
public class RedisScriptConfig {

    /**
     * 秒杀核心脚本：限流 + 判断 + 扣减，一次往返完成。
     *
     * <p>返回的是数组 {@code {'code', productId, seckillPrice}}，不是单个数字，
     * 所以结果类型是 {@code List} 而不是 {@code Long}。
     */
    @Bean
    public DefaultRedisScript<List> seckillScript() {
        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("lua/seckill.lua"));
        script.setResultType(List.class);
        return script;
    }

    /** 发送 MQ 失败时的补偿脚本：把占掉的名额还回去 */
    @Bean
    public DefaultRedisScript<Long> seckillCompensateScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("lua/seckill-compensate.lua"));
        script.setResultType(Long.class);
        return script;
    }

    /** 关单回滚脚本：把超时未付订单占掉的名额还回去 */
    @Bean
    public DefaultRedisScript<Long> seckillRollbackScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("lua/seckill-rollback.lua"));
        script.setResultType(Long.class);
        return script;
    }
}
