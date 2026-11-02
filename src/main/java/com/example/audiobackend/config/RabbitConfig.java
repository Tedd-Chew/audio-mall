package com.example.audiobackend.config;

import com.example.audiobackend.constant.MqConstant;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 配置：消息转换器 + 秒杀下单的交换机/队列/绑定。
 */
@Configuration
public class RabbitConfig {

    /**
     * 让 RabbitTemplate 发送对象时自动转成 JSON，消费时自动转回对象。
     * 不配的话默认走 JDK 序列化：要求对象实现 Serializable，且消息体不可读。
     */
    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /** Direct 交换机：路由键完全匹配才投递，秒杀场景够用 */
    @Bean
    public DirectExchange seckillExchange() {
        // durable = true：交换机定义活过 RabbitMQ 重启
        return new DirectExchange(MqConstant.SECKILL_EXCHANGE, true, false);
    }

    /**
     * 秒杀下单队列。
     *
     * <p>durable = true 保证队列定义活过重启。消息本身默认是持久化的
     * （Spring 的 RabbitTemplate 默认 deliveryMode = PERSISTENT），不用额外配。
     *
     * <p>挂了死信交换机：消费端调用 basicNack(requeue=false) 时，消息不是消失，
     * 而是被转投到死信队列。没有这一段，「重试到上限」等于「静默丢弃」。
     */
    @Bean
    public Queue seckillOrderQueue() {
        return QueueBuilder.durable(MqConstant.SECKILL_ORDER_QUEUE)
                .deadLetterExchange(MqConstant.SECKILL_ORDER_DLX)
                .deadLetterRoutingKey(MqConstant.SECKILL_ORDER_DLQ_ROUTING_KEY)
                .build();
    }

    @Bean
    public Binding seckillOrderBinding(Queue seckillOrderQueue, DirectExchange seckillExchange) {
        return BindingBuilder.bind(seckillOrderQueue)
                .to(seckillExchange)
                .with(MqConstant.SECKILL_ORDER_ROUTING_KEY);
    }

    /** 死信交换机：只服务秒杀下单的失败消息 */
    @Bean
    public DirectExchange seckillOrderDlx() {
        return new DirectExchange(MqConstant.SECKILL_ORDER_DLX, true, false);
    }

    /** 死信队列：消息留在这里不删，可以事后查、可以重放回主交换机 */
    @Bean
    public Queue seckillOrderDlq() {
        return QueueBuilder.durable(MqConstant.SECKILL_ORDER_DLQ).build();
    }

    @Bean
    public Binding seckillOrderDlqBinding(Queue seckillOrderDlq, DirectExchange seckillOrderDlx) {
        return BindingBuilder.bind(seckillOrderDlq)
                .to(seckillOrderDlx)
                .with(MqConstant.SECKILL_ORDER_DLQ_ROUTING_KEY);
    }
}
