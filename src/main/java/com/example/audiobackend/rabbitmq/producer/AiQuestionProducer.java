package com.example.audiobackend.rabbitmq.producer;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import com.example.audiobackend.constant.MqConstant;
import com.example.audiobackend.entity.AiQuestionMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@Component
public class AiQuestionProducer {
    private static final Logger log = LoggerFactory.getLogger(AiQuestionProducer.class);

    final private RabbitTemplate rabbitTemplate;
    public AiQuestionProducer(RabbitTemplate rabbitTemplate)
    {
        this.rabbitTemplate = rabbitTemplate;
    }

    // 发送消息到队列
    public void sendQuestion(AiQuestionMessage message) {
        rabbitTemplate.convertAndSend(MqConstant.AI_QUESTION_QUEUE, message);
        log.info("发送成功：{}", message);
    }
    public void sendTestQuestion(AiQuestionMessage message) {
        rabbitTemplate.convertAndSend(MqConstant.LLM_TEST_QUEUE, message);
        log.info("发送成功：{}", message);
    }
}

