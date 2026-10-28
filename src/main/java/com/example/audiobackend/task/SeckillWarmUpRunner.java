package com.example.audiobackend.task;

import com.example.audiobackend.service.SeckillActivityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 启动后把秒杀活动的库存和时间窗写进 Redis。
 *
 * <p>为什么要在启动时做：开抢那一秒上万个请求同时到，如果第一个请求才去查库建缓存，
 * 那一瞬间所有请求都是 miss，全部打到数据库 —— 缓存等于不存在（缓存击穿）。
 *
 * <p>预热失败不阻断启动：缺了 key，Lua 会返回"库存获取失败"，用户看到活动不可用，
 * 但整个服务照常运行。比起让应用起不来，这是更好的降级。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeckillWarmUpRunner implements ApplicationRunner {

    private final SeckillActivityService seckillActivityService;

    @Override
    public void run(ApplicationArguments args) {
        try {
            seckillActivityService.warmUp();
        } catch (Exception e) {
            log.error("秒杀预热整体失败，服务继续启动（活动将不可抢）", e);
        }
    }
}
