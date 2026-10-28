package com.example.audiobackend.service.impl;

import com.example.audiobackend.constant.MqConstant;
import com.example.audiobackend.constant.RedisConstant;
import com.example.audiobackend.dto.SeckillOrderMessage;
import com.example.audiobackend.service.SeckillOrderService;
import com.example.audiobackend.util.BizNoGenerator;
import com.example.audiobackend.vo.SeckillResultVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SeckillOrderServiceImpl implements SeckillOrderService {

    /** 与 seckill.lua 的返回码一一对应 */
    private static final String CODE_OK = "0";
    private static final String CODE_SOLD_OUT = "1";
    private static final String CODE_LIMIT_EXCEEDED = "2";
    private static final String CODE_NOT_READY = "3";
    private static final String CODE_NOT_STARTED = "4";
    private static final String CODE_FINISHED = "5";
    private static final String CODE_RATE_LIMITED = "6";

    private final StringRedisTemplate stringRedisTemplate;
    private final RabbitTemplate rabbitTemplate;
    private final DefaultRedisScript<List> seckillScript;
    private final DefaultRedisScript<Long> seckillCompensateScript;

    /** 桶容量 = 允许的瞬时突发。参考 Tomcat 线程数（默认 200），先给 500 */
    @Value("${seckill.rate.capacity:500}")
    private int rateCapacity;

    /** 每秒补充的令牌数 = 长期速率。Sprint 3 压测单机 300 QPS，先给 200 */
    @Value("${seckill.rate.permits-per-second:200}")
    private int ratePermitsPerSecond;

    /**
     * 秒杀下单。
     *
     * <p>限流、时间窗、库存、限购、扣减全部在 {@code seckill.lua} 里一次执行完，
     * 整个方法只和 Redis 交互一次。省掉的两次往返（原来限流一次、读活动信息一次）
     * 在压测里各值约 2.9ms，见 docs/压测记录.md。
     */
    @Override
    public SeckillResultVO seckill(Long actId, Long userId) {
        List<String> keys = List.of(
                RedisConstant.SECKILL_RATE + actId,
                RedisConstant.SECKILL_STOCK + actId,
                RedisConstant.SECKILL_BOUGHT + actId,
                RedisConstant.SECKILL_META + actId);

        List<?> result = stringRedisTemplate.execute(
                seckillScript,
                keys,
                String.valueOf(userId),
                String.valueOf(Instant.now().getEpochSecond()),
                String.valueOf(rateCapacity),
                String.valueOf(ratePermitsPerSecond),
                String.valueOf(System.currentTimeMillis()));

        String code = (result == null || result.isEmpty())
                ? CODE_NOT_READY
                : String.valueOf(result.get(0));

        return switch (code) {
            case CODE_OK -> enqueue(actId, userId,
                    String.valueOf(result.get(1)), String.valueOf(result.get(2)));
            case CODE_SOLD_OUT -> build(null, "已售罄");
            case CODE_LIMIT_EXCEEDED -> build(null, "已达到限购数量");
            case CODE_NOT_STARTED -> build(null, "活动尚未开始");
            case CODE_FINISHED -> build(null, "活动已结束");
            case CODE_RATE_LIMITED -> build(null, "当前抢购人数过多，请稍后再试");
            default -> build(null, "活动不存在或暂不可用");
        };
    }

    /**
     * 抢到名额后：组装消息 → 投递 MQ。投递失败要回滚 Redis。
     *
     * <p>商品 id 和秒杀价由脚本一起返回，这里不再查 Redis。
     */
    private SeckillResultVO enqueue(Long actId, Long userId, String productId, String seckillPrice) {
        String orderNo = BizNoGenerator.orderNo();

        SeckillOrderMessage message = new SeckillOrderMessage();
        message.setOrderNo(orderNo);
        message.setActId(actId);
        message.setUserId(userId);
        message.setProductId(Long.valueOf(productId));
        message.setSeckillPrice(new BigDecimal(seckillPrice));

        try {
            rabbitTemplate.convertAndSend(
                    MqConstant.SECKILL_EXCHANGE, MqConstant.SECKILL_ORDER_ROUTING_KEY, message);
        } catch (Exception e) {
            // 消息没发出去，用户还没收到"抢到"的承诺，可以反悔 —— 把名额还回去
            log.error("秒杀消息投递失败，回滚 Redis，actId={}，userId={}", actId, userId, e);
            compensate(actId, userId);
            return build(null, "排队失败，请重试");
        }

        log.info("秒杀抢到名额并入队，actId={}，userId={}，orderNo={}", actId, userId, orderNo);
        return build(orderNo, "排队中，请稍候");
    }

    /**
     * 回滚刚从 Redis 占掉的名额。
     *
     * <p>失败只记日志，不重试：重试可能补两次导致库存变多（超卖）。
     * 宁可少卖一件，不可超卖。
     */
    private void compensate(Long actId, Long userId) {
        try {
            stringRedisTemplate.execute(
                    seckillCompensateScript,
                    List.of(RedisConstant.SECKILL_STOCK + actId, RedisConstant.SECKILL_BOUGHT + actId),
                    String.valueOf(userId));
        } catch (Exception e) {
            log.error("回滚 Redis 名额失败，本单少卖一件，actId={}，userId={}", actId, userId, e);
        }
    }

    private SeckillResultVO build(String orderNo, String message) {
        SeckillResultVO vo = new SeckillResultVO();
        vo.setOrderNo(orderNo);
        vo.setMessage(message);
        return vo;
    }
}
