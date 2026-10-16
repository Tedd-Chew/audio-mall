package com.example.audiobackend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

//这是平台线程池，适合进行cpu密集型业务
@Configuration
public class PlatformThreadPoolConfig {

    @Bean("platformThreadPoolExecutor")
    public ThreadPoolExecutor threadPoolExecutor() {
        // 1. 自定义线程工厂：给线程起个专业的名字
        ThreadFactory threadFactory = new ThreadFactory() {//线程工厂用来创建线程，如果不自定义，就会用守护线程
        private final AtomicInteger threadIndex = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r);
            thread.setName("ai-chat-thread-" + threadIndex.getAndIncrement());
            
            // 关键：统一处理线程未捕获的异常
            thread.setUncaughtExceptionHandler((t, e) -> {
                System.err.println("【线程异常】线程" + t.getName() + "抛出未捕获异常：");
                e.printStackTrace();
            });
            
            return thread;
        }
    };//这里写了一个匿名内部类，重写了newThread方法，给线程起了个名字，并且设置了未捕获异常处理器，这样线程池里所有线程都能自动处理异常，不会因为某个线程抛出异常而导致整个线程池崩溃。

        // 2. 创建并返回优化后的线程池
        return new ThreadPoolExecutor(
            5,                              // 核心线程：5个（调大一点，同时处理更多AI任务）
            10,                             // 最大线程：10个（高峰期临时扩容）
            60,                             // 空闲线程存活时间：60秒
            TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(200),  // 队列容量：200（调大一点，缓冲更多任务）
            threadFactory,                   // 绑定自定义线程工厂
            // 关键：设置友好的拒绝策略，线程池满了不抛异常、不丢消息
            new ThreadPoolExecutor.CallerRunsPolicy()//任务队列满了之后就会用消费者线程处理任务，就暂时不会接收新的任务
        );
    }
}
