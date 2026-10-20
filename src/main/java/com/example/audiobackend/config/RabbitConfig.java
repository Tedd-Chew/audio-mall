package com.example.audiobackend.config;

import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 配置。
 * 具体的队列和交换机等到业务需要时再建（订单超时关闭、秒杀异步下单）。
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
}
