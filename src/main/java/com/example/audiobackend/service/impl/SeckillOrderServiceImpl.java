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
import org.springframework.util.StringUtils;

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
    private static final String CODE_DUPLICATE = "7";

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
     * <p>限流、时间窗、库存、限购、幂等、扣减全部在 {@code seckill.lua} 里一次执行完，
     * 整个方法只和 Redis 交互一次。
     *
     * <p>{@code requestId} 可以不传（方便测试）：不传就不启用幂等门。
     * 传了的话，同一个 (用户, requestId) 重复提交会拿到第一次那个订单号，不会多扣库存。
     */
    @Override
    public SeckillResultVO seckill(Long actId, Long userId, String requestId) {
        boolean idemEnabled = StringUtils.hasText(requestId);
        // 没传 requestId 时用一个固定占位：这个 key 永远不会被写（ARGV[7]='0'），
        // 但比把 "null" 拼进 key 名里干净
        String idemSuffix = idemEnabled ? requestId : "disabled";
        String idemKey = RedisConstant.SECKILL_IDEM + actId + ":" + userId + ":" + idemSuffix;

        // 单号必须在调脚本之前生成：幂等门里存的就是它，重复请求要把原单号还回去
        String orderNo = BizNoGenerator.orderNo();

        List<String> keys = List.of(
                RedisConstant.SECKILL_RATE + actId,
                RedisConstant.SECKILL_STOCK + actId,
                RedisConstant.SECKILL_BOUGHT + actId,
                RedisConstant.SECKILL_META + actId,
                idemKey);

        List<?> result = stringRedisTemplate.execute(
                seckillScript,
                keys,
                String.valueOf(userId),
                String.valueOf(Instant.now().getEpochSecond()),
                String.valueOf(rateCapacity),
                String.valueOf(ratePermitsPerSecond),
                String.valueOf(System.currentTimeMillis()),
                orderNo,
                idemEnabled ? "1" : "0");

        String code = (result == null || result.isEmpty())
                ? CODE_NOT_READY
                : String.valueOf(result.get(0));

        return switch (code) {
            case CODE_OK -> enqueue(actId, userId, idemKey, idemEnabled ? requestId : null, orderNo,
                    String.valueOf(result.get(1)), String.valueOf(result.get(2)));
            case CODE_DUPLICATE -> build(String.valueOf(result.get(1)),
                    "重复提交，这是你上次抢到的订单，单号不变");
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
    private SeckillResultVO enqueue(Long actId, Long userId, String idemKey, String requestId,
                                    String orderNo, String productId, String seckillPrice) {
        SeckillOrderMessage message = new SeckillOrderMessage();
        message.setOrderNo(orderNo);
        message.setActId(actId);
        message.setUserId(userId);
        message.setProductId(Long.valueOf(productId));
        message.setSeckillPrice(new BigDecimal(seckillPrice));
        // 带下去写进 orders.request_id：Redis 那道门是主判据，
        // DB 唯一索引是兜底（万一 Redis 整库丢了）
        message.setRequestId(requestId);

        try {
            rabbitTemplate.convertAndSend(
                    MqConstant.SECKILL_EXCHANGE, MqConstant.SECKILL_ORDER_ROUTING_KEY, message);
        } catch (Exception e) {
            // 消息没发出去，用户还没收到"抢到"的承诺，可以反悔 —— 把名额和幂等门一起还回去
            log.error("秒杀消息投递失败，回滚 Redis，actId={}，userId={}", actId, userId, e);
            compensate(actId, userId, idemKey, StringUtils.hasText(requestId), orderNo);
            return build(null, "排队失败，请重试");
        }

        log.info("秒杀抢到名额并入队，actId={}，userId={}，orderNo={}", actId, userId, orderNo);
        return build(orderNo, "排队中，请稍候");
    }

    /**
     * 撤销这条消息对应的那次扣减。消费者撞上 uk_user_request 时调用。
     *
     * <p>幂等门里存的单号必须是<b>这一条消息的单号</b>，脚本才动手 ——
     * 这是「这次扣减还没被撤销」的证据，也是重复调用的守卫。
     */
    @Override
    public boolean compensateRejected(SeckillOrderMessage message) {
        boolean idemEnabled = StringUtils.hasText(message.getRequestId());
        String idemSuffix = idemEnabled ? message.getRequestId() : "disabled";
        String idemKey = RedisConstant.SECKILL_IDEM + message.getActId() + ":" + message.getUserId()
                + ":" + idemSuffix;
        return compensate(message.getActId(), message.getUserId(), idemKey,
                idemEnabled, message.getOrderNo());
    }

    /**
     * 回滚刚从 Redis 占掉的名额，同时拆掉幂等门。
     *
     * <p>幂等门必须一起删：不删的话库存虽然还回去了，但这个 requestId 仍然被占着，
     * 用户重试会被判重复、拿到一个不存在的订单号。
     *
     * <p><b>这个方法是幂等的</b>，可以重复调用：守卫在 lua 里，靠「门里存的单号是不是
     * 本次这一条」来判断。撤销成功会删门，第二次调用就什么都做不了。
     * 这一点很重要 —— 消费者侧的重试会让它被调用多次。
     *
     * <p>失败只记日志、不抛：调用方是消费失败路径，抛出去只会让消息再次重投。
     * 宁可少卖一件（名额没还回去），不可超卖（名额还两次）。
     *
     * @return true 表示这次真的撤销了
     */
    private boolean compensate(Long actId, Long userId, String idemKey,
                               boolean idemEnabled, String orderNo) {
        try {
            Long result = stringRedisTemplate.execute(
                    seckillCompensateScript,
                    List.of(RedisConstant.SECKILL_STOCK + actId,
                            RedisConstant.SECKILL_BOUGHT + actId,
                            idemKey),
                    String.valueOf(userId),
                    idemEnabled ? "1" : "0",
                    orderNo == null ? "" : orderNo);
            return result != null && result == 1L;
        } catch (Exception e) {
            log.error("撤销 Redis 名额失败，本单少卖一件，actId={}，userId={}，orderNo={}",
                    actId, userId, orderNo, e);
            return false;
        }
    }

    private SeckillResultVO build(String orderNo, String message) {
        SeckillResultVO vo = new SeckillResultVO();
        vo.setOrderNo(orderNo);
        vo.setMessage(message);
        return vo;
    }
}
