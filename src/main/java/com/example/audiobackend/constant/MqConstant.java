package com.example.audiobackend.constant;

/**
 * 消息队列的交换机 / 队列 / 路由键。
 *
 * <p>命名带 {@code audio.} 前缀，和 Redis key 的 {@code audio:} 前缀同一个目的：
 * 多个项目共用一个 RabbitMQ 时不打架。
 */
public interface MqConstant {

    /** 秒杀交换机，Direct 类型 —— 一个路由键对一个队列，不需要 fanout/topic 的复杂度 */
    String SECKILL_EXCHANGE = "audio.seckill.exchange";

    /** 秒杀下单队列，durable —— 队列定义活过 RabbitMQ 重启 */
    String SECKILL_ORDER_QUEUE = "audio.seckill.order.queue";

    String SECKILL_ORDER_ROUTING_KEY = "audio.seckill.order";
}
