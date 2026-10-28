package com.example.audiobackend.listener;

import com.example.audiobackend.constant.MqConstant;
import com.example.audiobackend.dto.SeckillOrderMessage;
import com.example.audiobackend.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

/**
 * 秒杀下单消费者。
 *
 * <p>职责只有两件：收消息、把异常兜住。业务和事务都在 {@link OrderService#createSeckillOrder} 里，
 * 这样 {@code @Transactional} 才能正常生效（同类内部调用会绕过代理）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeckillOrderListener {

    private final OrderService orderService;

    @RabbitListener(queues = MqConstant.SECKILL_ORDER_QUEUE)
    public void onMessage(SeckillOrderMessage message) {
        try {
            orderService.createSeckillOrder(message);
        } catch (DuplicateKeyException e) {
            // order_no 唯一索引拦住了重复消息，事务已回滚，属于正常情况
            log.info("重复的秒杀消息，订单已存在，忽略 orderNo={}", message.getOrderNo());
        } catch (Exception e) {
            // 其他异常：当前不做重试，直接吞掉让消息被 ack —— 后果是少卖一件。
            // 为什么不用 requeue：重投会让一条脏消息无限循环占住消费者。
            // 真正的重试 + 死信队列留到部署阶段。
            log.error("秒杀订单创建失败，该消息已被丢弃 orderNo={}，actId={}，userId={}",
                    message.getOrderNo(), message.getActId(), message.getUserId(), e);
        }
    }
}
