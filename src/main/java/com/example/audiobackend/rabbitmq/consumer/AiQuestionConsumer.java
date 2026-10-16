package com.example.audiobackend.rabbitmq.consumer;

import com.example.audiobackend.constant.MqConstant;
import com.example.audiobackend.entity.AiQuestionMessage;
import com.example.audiobackend.service.LLMChatService;

import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;


@Component
public class AiQuestionConsumer {

    // 1. 注入AI业务服务（纯逻辑，和MQ解耦）
    private final LLMChatService llmChatService;

    public AiQuestionConsumer(LLMChatService llmChatService)
    {
        this.llmChatService = llmChatService;
    }

    /**
     * 核心：监听MQ队列，自动接收消息
     */
    @RabbitListener(queues = MqConstant.AI_QUESTION_QUEUE)//执行到这里领走消息
    @RabbitHandler
    public void receive(AiQuestionMessage message) {
        // 从消息对象中获取参数,这里底层自动帮我们把json转化为了对象，才能被收到。
        String visitorId = message.getVisitorId();
        String question = message.getQuestion();
        try {
        llmChatService.chat(question, visitorId);
        } catch (Exception e) {
            // 异常处理：失败，告诉前端
            System.err.println("【消费者】AI处理异常：" + e.getMessage());
        }
    }
}

