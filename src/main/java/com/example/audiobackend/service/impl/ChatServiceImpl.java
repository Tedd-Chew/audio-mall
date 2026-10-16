package com.example.audiobackend.service.impl;

import com.example.audiobackend.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import com.example.audiobackend.util.AiChatUtil;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.data.redis.core.StringRedisTemplate;
import java.util.concurrent.TimeUnit;



//@Service  //暂停使用，现在用LLMChatService
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatServiceImpl.class);


    private final AiChatUtil aiChatUtil;
    private final JdbcTemplate jdbcTemplate;
    private final ThreadPoolExecutor executor;
    // 注入Redis存结果
    private final StringRedisTemplate redisTemplate;

    // ================== 你的原有线程池方法，完全不动 ==================
    @Override
    public void chat(String visitorId, String userMessage) {
        executor.submit(() -> {
            try {
                log.info("【线程池】开始处理AI任务，visitorId：{}，消息：{}", visitorId, userMessage);
                String prompt = buildPrompt(userMessage);
                String aiReply = aiChatUtil.sendToAi(prompt).trim();
                log.info("【线程池】AI初步回复：{}", aiReply);

                if (aiReply.startsWith("NEED_SQL:")) {
                    String sql = aiReply.replace("NEED_SQL:", "").trim();
                    if (!sql.toLowerCase().startsWith("select")) {
                        log.warn("【线程池】拦截非法SQL：{}", sql);
                        redisTemplate.opsForValue().set("AI_ANSWER:"+visitorId, "拦截非法SQL",5,TimeUnit.MINUTES);
                        return;
                    }
                    log.info("【线程池】执行SQL：{}", sql);
                    List<Map<String, Object>> result = jdbcTemplate.queryForList(sql);
                    String finalAnswer = aiChatUtil.sendToAi(buildFinalPrompt(userMessage, result));
                    log.info("【线程池】最终回复：{}", finalAnswer);
                    redisTemplate.opsForValue().set("AI_ANSWER:"+visitorId, finalAnswer,5,TimeUnit.MINUTES);
                } else {
                    redisTemplate.opsForValue().set("AI_ANSWER:"+visitorId, aiReply,5,TimeUnit.MINUTES);
                }
            } catch (Exception e) {
                log.error("【线程池】AI处理异常，visitorId：{}，错误：{}", visitorId, e.getMessage(), e);
                redisTemplate.opsForValue().set("AI_ANSWER:"+visitorId, "AI处理失败",5,TimeUnit.MINUTES);
            }
        });

    }

    // ================== 下面是抽取的提示语方法 ==================
    private String buildPrompt(String userMessage) {
        return """
            你是智能助手。
            如果需要查询数据库，仅输出 NEED_SQL:xxx，只用 SELECT * FROM product 或 SELECT * FROM sys_user，不要指定列名。
            不需要就直接回答。
            用户问题：%s
            """.formatted(userMessage);
    }

    private String buildFinalPrompt(String userMessage, List<Map<String, Object>> result) {
        return """
            用户问题：%s
            数据：%s
            规则：只根据数据回答，自然中文，简洁专业
            """.formatted(userMessage, result);
    }
}


