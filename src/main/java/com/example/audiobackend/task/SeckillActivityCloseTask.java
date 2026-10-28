package com.example.audiobackend.task;

import com.example.audiobackend.service.SeckillActivityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 秒杀活动到点收尾：改状态、把没卖完的库存还回商品、清理 Redis。
 *
 * <p>不做这个的后果：活动结束后 {@code seckill_activity.stock} 里剩下的库存
 * 永远回不到 {@code product.stock} —— 商品库存被永久扣掉一部分。
 *
 * <p>逐条独立事务：一条脏数据不该拖累整批。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeckillActivityCloseTask {

    private static final int BATCH_SIZE = 100;

    private final SeckillActivityService seckillActivityService;

    /** 启动 1 分钟后开始，之后每 60 秒一轮 —— 活动结束的延迟最多 1 分钟，够用 */
    @Scheduled(initialDelay = 60_000, fixedDelay = 60_000)
    public void finishExpiredActivities() {
        List<Long> actIds = seckillActivityService.findExpiredRunningIds(BATCH_SIZE);
        if (actIds.isEmpty()) {
            return;
        }

        int finished = 0;
        for (Long actId : actIds) {
            try {
                if (seckillActivityService.finishOneActivity(actId)) {
                    finished++;
                }
            } catch (Exception e) {
                log.error("结束秒杀活动失败，actId={}", actId, e);
            }
        }
        if (finished > 0) {
            log.info("本轮结束秒杀活动 {} 个", finished);
        }
    }
}
