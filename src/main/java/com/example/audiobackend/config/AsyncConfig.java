package com.example.audiobackend.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

@Configuration
@EnableAsync
public class AsyncConfig {

    private static final Logger log = LoggerFactory.getLogger(AsyncConfig.class);

    @Bean("virtualThreadExecutor")
    public Executor virtualThreadExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // 【核心1】自定义虚拟线程工厂：加命名、加异常处理、加日志
        ThreadFactory customVirtualThreadFactory = new ThreadFactory() {
            private final AtomicInteger threadIndex = new AtomicInteger(1);

            @Override
            public Thread newThread(Runnable r) {
                // 1. 用官方 API 创建虚拟线程
                Thread virtualThread = Thread.ofVirtual()
                        .name("virtual-ai-", threadIndex.getAndIncrement()) // 2. 自定义线程名前缀+序号
                        .uncaughtExceptionHandler((t, e) -> { // 3. 统一异常处理+日志
                            log.error("【虚拟线程异常】线程 {} 抛出未捕获异常：", t.getName(), e);
                        })
                        .unstarted(r); // 4. 创建未启动的虚拟线程

                log.info("【虚拟线程工厂】创建新虚拟线程：{}", virtualThread.getName());
                return virtualThread;
            }
        };

        // 【核心2】绑定我们自定义的虚拟线程工厂
        executor.setThreadFactory(customVirtualThreadFactory);

        // 虚拟线程最佳实践配置（保持不变）
        executor.setCorePoolSize(0);
        executor.setMaxPoolSize(Integer.MAX_VALUE);
        executor.setQueueCapacity(0);
        executor.setWaitForTasksToCompleteOnShutdown(true); // 【可选】优雅停机：等待任务完成再关闭
        executor.setAwaitTerminationSeconds(60); // 【可选】优雅停机：最多等待60秒
        executor.initialize();

        log.info("【虚拟线程池】初始化完成");
        return executor;
    }
}
