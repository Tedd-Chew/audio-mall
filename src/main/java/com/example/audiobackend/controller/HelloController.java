package com.example.audiobackend.controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

// Spring Boot核心注解：标记这是接口控制器
@RestController
public class HelloController {
    // 定义GET请求接口，路径：http://localhost:8080/hello
    @GetMapping("/hello")
    public String sayHello() {
        return "音响网站后端启动成功！Maven配置没问题～";
    }
}