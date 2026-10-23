package com.example.audiobackend.task;

import com.example.audiobackend.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 超时未支付订单的关单兜底。
 *
 * <p>只用定时扫描，没有上延迟消息。理由：延迟消息在这个数据量下买不到什么
 * （日订单 5 万、未支付按 30% 算是 1.5 万行，走 idx_status_expire 是索引范围扫描），
 * 而扫描本身是真正的需求 —— 不做的话订单永远停在待支付，库存永远锁死。
 * 等 Sprint 5 秒杀引入 RabbitMQ 之后，再补延迟消息并拿真实数据对比。
 *
 * <p>逐条独立事务：一条脏数据不该拖累整批，否则它会每分钟把整批拖垮一次。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderTimeoutTask {

    /** 单批扫描上限 */
    private static final int BATCH_SIZE = 100;

    /** 每轮最多扫几批，防止积压时一轮跑太久 */
    private static final int MAX_BATCHES = 10;

    private final OrderService orderService;

    /** 启动 30 秒后开始，避开启动时的资源竞争；之后每 60 秒一轮 */
    @Scheduled(initialDelay = 30_000, fixedDelay = 60_000)
    public void closeExpiredOrders() {
        int closedCount = 0;

        for (int batch = 0; batch < MAX_BATCHES; batch++) {
            List<Long> ids = orderService.findExpiredOrderIds(BATCH_SIZE);
            if (ids.isEmpty()) {
                break;
            }

            for (Long orderId : ids) {
                try {
                    if (orderService.closeIfUnpaid(orderId)) {
                        closedCount++;
                    }
                } catch (Exception e) {
                    // 单条失败不影响其余；下一轮还会扫到它，但不会卡住别人
                    log.error("关单失败，orderId={}", orderId, e);
                }
            }

            // 没扫满说明已经没有更多超时单了
            if (ids.size() < BATCH_SIZE) {
                break;
            }
        }

        if (closedCount > 0) {
            log.info("本轮超时关单完成，共关闭 {} 单", closedCount);
        }
    }
}
