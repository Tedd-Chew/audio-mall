package com.example.audiobackend;


import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.scheduling.annotation.EnableScheduling;


@MapperScan("com.example.audiobackend.mapper")
@SpringBootApplication
@EnableScheduling//告诉spring我要使用定时任务。
public class AudiobackendApplication {
	public static void main(String[] args) {
		SpringApplication.run(AudiobackendApplication.class, args);
	}
}
