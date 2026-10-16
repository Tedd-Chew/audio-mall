package com.example.audiobackend.util;

import com.example.audiobackend.entity.AiQuestionMessage;
import com.example.audiobackend.entity.LLMResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

//这个类只用于给FastAPI发消息，所以会比直接给大模型发简单一点。
@Slf4j
@Component
public class LLMChatUtil {
    private final RestTemplate restTemplate = new RestTemplate();//spring用来发http请求的工具
    @Value("${python.ai.service.url}")
    private String pythonAiServiceUrl;

    public String sendToLLM(String question ,String visitorId){
        try{
            log.info("调用python服务");
            // 1. 构造请求头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            // 2. 构造请求体
            AiQuestionMessage request = new AiQuestionMessage(visitorId, question);
            HttpEntity<AiQuestionMessage> httpEntity = new HttpEntity<>(request, headers);

            // 3. 调用 Python HTTP 接口
            LLMResponse response = restTemplate.postForObject(
                    pythonAiServiceUrl,
                    httpEntity,
                    LLMResponse.class
            );
            String answer = response != null ? response.getAnswer() : "AI返回空";
            log.info("【PythonAI】Python服务返回，visitorId：{}，answer：{}", visitorId, answer);
            return answer;
        } catch (Exception e) {
            log.error("【PythonAI】调用Python服务异常，visitorId：{}，错误：{}", visitorId, e.getMessage(), e);
            return "AI服务异常" + e.getMessage();
        }


    }
}
