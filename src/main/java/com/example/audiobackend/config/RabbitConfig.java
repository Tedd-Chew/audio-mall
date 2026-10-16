package com.example.audiobackend.config;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import static com.example.audiobackend.constant.MqConstant.AI_QUESTION_QUEUE;

@Configuration
public class RabbitConfig {
    @Bean
    public Queue aiQuestionQueue() {
        return new Queue(AI_QUESTION_QUEUE, true);
    }

    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }//spring启动时，会把messageConverter这个方法返回的对象，注入到rabbitTemplate中，这样我们在发送消息时，就可以直接传对象，底层会自动转换成JSON字符串发送到MQ队列中；消费者接收消息时，也会自动把JSON字符串转换成对象，简化了开发。
}
