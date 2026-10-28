package com.example.audiobackend.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 秒杀下单消息，从抢购接口投递到 MQ，由消费者据此创建订单。
 *
 * <p>为什么把 {@code productId} 和 {@code seckillPrice} 放进消息而不是让消费者查库：
 * <ol>
 *   <li>消费者少一次查询</li>
 *   <li>价格是<b>快照语义</b> —— 以"抢到那一刻"的价格为准。活动后来改价，已排队的人不受影响</li>
 * </ol>
 *
 * <p>前提：生产者必须从<b>可信来源</b>取价格（Redis meta，由活动表预热而来），
 * 绝不能从请求参数取，否则用户能自己定价。
 */
@Data
public class SeckillOrderMessage {

    /** 生产者生成，消费者靠它做幂等（靠 orders.order_no 唯一索引） */
    private String orderNo;

    private Long actId;

    private Long userId;

    private Long productId;

    /** 抢到那一刻的秒杀价 */
    private BigDecimal seckillPrice;
}
