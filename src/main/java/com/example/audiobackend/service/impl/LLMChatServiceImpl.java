package com.example.audiobackend.service.impl;

import com.example.audiobackend.service.LLMChatService;
import com.example.audiobackend.util.LLMChatUtil;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class LLMChatServiceImpl implements LLMChatService {

    private static final Logger log = LoggerFactory.getLogger(LLMChatServiceImpl.class);

    private final LLMChatUtil llmChatUtil;
    private final RedisTemplate<String, String> redisTemplate;

    @Override
    @Async("virtualThreadExecutor")//用虚拟线程池，这个注解自动提交到虚拟线程池
    public void chat(String question, String visitorId) {
        try {
            log.info("【LLMChatService】收到请求，visitorId：{}，question：{}", visitorId, question);

            // 1. 直接调用 Python，只传原始问题，其他什么都不管
            String answer = llmChatUtil.sendToLLM(question, visitorId);

            // 2. 把 Python 返回的结果直接存 Redis
            redisTemplate.opsForValue().set(
                    "AI_ANSWER:" + visitorId,
                    answer,
                    5,
                    TimeUnit.MINUTES
            );

            log.info("【LLMChatService】处理完成，visitorId：{}，answer：{}", visitorId, answer);

        } catch (Exception e) {
            log.error("【LLMChatService】处理异常，visitorId：{}，错误：{}", visitorId, e.getMessage(), e);
            redisTemplate.opsForValue().set(
                    "AI_ANSWER:" + visitorId,
                    "系统处理异常：" + e.getMessage(),
                    5,
                    TimeUnit.MINUTES
            );
        }
    }
}
