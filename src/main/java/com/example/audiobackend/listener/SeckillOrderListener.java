package com.example.audiobackend.listener;

import com.example.audiobackend.constant.MqConstant;
import com.example.audiobackend.dto.SeckillOrderMessage;
import com.example.audiobackend.service.OrderService;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 秒杀下单消费者（手动 ack）。
 *
 * <p><b>为什么不用 auto</b>：auto 模式下「方法正常返回」就等于签收。以前这里用
 * {@code catch (Exception)} 把所有异常吞掉，结果是一条消息执行失败也照样被 ack，
 * Redis 里扣掉的库存永远回不来，用户的重试还会被幂等门挡住，全程只有一行日志。
 *
 * <p><b>为什么重试能随便重试</b>：{@code order_no} 有唯一索引。同一条消息被处理两次时，
 * 第二次的 insert 会撞索引抛 {@link DuplicateKeyException}，说明「上一次其实成功了」。
 * 所以重试本身就是「上次到底提交没有」的判定器，不需要额外去 MySQL 查一次。
 *
 * <p><b>但撞唯一索引有两种，处理完全相反</b>：{@code uk_order_no} 是重复投递，ack 即可；
 * {@code uk_user_request} 是「这个 requestId 已经有别的单了，这次扣减永远建不出来」，
 * 必须把名额还回去，否则永久少卖一件。区分逻辑在
 * {@link OrderService#resolveSeckillDuplicate}，这里不重复判断。
 *
 * <p><b>为什么是线程内重试，不是 nack 重投</b>：requeue 是立刻的，失败原因（MySQL 挂了）
 * 一秒内不会自己好，消息会原地疯狂重投，把消费者线程和 CPU 全占住。这里改成线程内等待，
 * 消息一直挂着未 ack，进程真挂了它也会被重新投递，可靠性不损失。
 *
 * <p><b>为什么重试完是进死信队列，不是补偿还库存</b>：走到上限说明「不知道发生了什么」，
 * 这时猜错方向的代价不对称 —— 多还一件 = 超卖，少还一件 = 少卖。秒杀卖的是「库存数字可信」，
 * 所以宁可把它留在死信队列里等人看。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeckillOrderListener {

    /**
     * 第 n 次失败后等多久再试。走完这四次还不行就进死信队列。
     *
     * <p>总共 1+2+4+8 = 15 秒，够扛住一次 MySQL 抖动或主从切换，
     * 又不足以让一条真正的脏消息长期占住消费者。
     */
    private static final long[] RETRY_DELAYS_MS = {1_000, 2_000, 4_000, 8_000};

    private final OrderService orderService;

    @RabbitListener(queues = MqConstant.SECKILL_ORDER_QUEUE)
    public void onMessage(SeckillOrderMessage message, Channel channel,
                          @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws IOException {
        for (int attempt = 0; ; attempt++) {
            Exception failure = processOnce(message);
            if (failure == null) {
                // 事务已经提交（或已确认无需建单），签收是安全的。
                // 顺序不能反：先 ack 再提交，进程崩在中间就是「消息没了、订单也没了」。
                channel.basicAck(tag, false);
                return;
            }

            if (attempt >= RETRY_DELAYS_MS.length) {
                log.error("秒杀订单处理失败且已重试 {} 次，转入死信队列。orderNo={}，actId={}，userId={}",
                        RETRY_DELAYS_MS.length, message.getOrderNo(),
                        message.getActId(), message.getUserId(), failure);
                // requeue=false → 走队列上配的死信交换机，消息进 audio.seckill.order.dlq
                channel.basicNack(tag, false, false);
                return;
            }

            long delay = RETRY_DELAYS_MS[attempt];
            log.warn("秒杀订单处理失败，{} ms 后重试（第 {} 次）。orderNo={}，原因={}",
                    delay, attempt + 1, message.getOrderNo(), failure.toString());
            if (!sleep(delay)) {
                // 等待被中断（通常是应用关闭）：不 ack 也不 nack，
                // 连接断开后消息会自动回到队列，交给下一次投递。
                log.warn("重试等待被中断，消息保持未确认。orderNo={}", message.getOrderNo());
                return;
            }
        }
    }

    /**
     * 处理一次。
     *
     * @return null 表示这条消息已经处理完（订单建成 / 确认重复投递 / 已撤销掉注定失败的扣减）；
     *         非 null 表示还没处理完，值就是失败原因，调用方会重试
     */
    private Exception processOnce(SeckillOrderMessage message) {
        try {
            orderService.createSeckillOrder(message);
            return null;
        } catch (DuplicateKeyException e) {
            // 撞唯一索引不等于「处理完了」，要看撞的是哪一个 —— 交给 service 判断
            try {
                orderService.resolveSeckillDuplicate(message);
                return null;
            } catch (Exception resolveError) {
                // 撤销失败（Redis 不可用 / 幂等门已过期）→ 当作普通失败，走重试
                return resolveError;
            }
        } catch (Exception e) {
            return e;
        }
    }

    /** @return false 表示等待被中断 */
    private static boolean sleep(long ms) {
        try {
            Thread.sleep(ms);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
