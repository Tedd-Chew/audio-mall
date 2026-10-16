package com.example.audiobackend.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.example.audiobackend.rabbitmq.producer.AiQuestionProducer;
import com.example.audiobackend.entity.AiQuestionMessage;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Map;

@RestController
public class AiController {

    // @Autowired
    // private ChatService chatService;弃用，逻辑不直接给service，而是发给rabbitmq等待消费者处理
    private final AiQuestionProducer aiQuestionProducer;

    private final StringRedisTemplate redisTemplate;

    public AiController(AiQuestionProducer aiQuestionProducer, StringRedisTemplate redisTemplate)
    {
        this.aiQuestionProducer = aiQuestionProducer;
        this.redisTemplate = redisTemplate;
    }

    @PostMapping("/api/ai/test")
    public Map<String,String> test(@RequestBody Map<String,String> request){
        String userMessage = request.get("message");
        String visitorId = request.get("visitorId");
        AiQuestionMessage message = new AiQuestionMessage(visitorId,userMessage);
        aiQuestionProducer.sendTestQuestion(message);
        return Map.of("reply", "AI 正在思考中，请稍等...");
    }

    @PostMapping("/api/ai/chat")
    public Map<String, String> chat(@RequestBody Map<String, String> request) {
        String userMessage = request.get("message");
        String visitorId = request.get("visitorId");//由前端第一次访问页面时自动生成，后续每次请求都携带
        AiQuestionMessage message = new AiQuestionMessage(visitorId, userMessage);
        // 将问题发送到RabbitMQ队列
        aiQuestionProducer.sendQuestion(message);
        return Map.of("reply", "AI 正在思考中，请稍等...");//这里先返回HTTP响应，真正的AI回答会在消费者处理完后再返回前端，前端再更新界面显示AI回答
    }
    // 2. 【新增接口】前端轮询查询 
    @GetMapping("/api/ai/result")
    public Map<String, String> getAiResult(@RequestParam String visitorId) {
        // 从 Redis 取出你消费者存的答案
        String answer = redisTemplate.opsForValue().get("AI_ANSWER:" + visitorId);

        // 没有答案 = 还在处理中
        if (answer == null) {
            return Map.of("status", "processing", "reply", "AI 还在思考中...");
        }

        // 有答案 = 返回最终结果
        return Map.of("status", "success", "reply", answer);
    }
}//controller层只负责接受请求，获取用户输入的消息，调用service层的方法处理消息，返回处理结果给前端。真正的业务逻辑（调用AI接口、处理SQL查询等）都在service层实现，controller层保持简洁。
