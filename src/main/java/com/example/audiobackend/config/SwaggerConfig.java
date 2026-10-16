package com.example.audiobackend.config; 

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import springfox.documentation.builders.ApiInfoBuilder;
import springfox.documentation.builders.PathSelectors;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.oas.annotations.EnableOpenApi;
import springfox.documentation.service.*;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spi.service.contexts.SecurityContext;
import springfox.documentation.spring.web.plugins.Docket;

import java.util.Collections;
import java.util.List;

@Configuration 
@EnableOpenApi 
public class SwaggerConfig {

    @Bean
    public Docket createRestApi() {
        return new Docket(DocumentationType.OAS_30) 
                .apiInfo(apiInfo()) 
                // 配置JWT认证规则（让Authorize按钮显示）
                .securitySchemes(Collections.singletonList(apiKey()))
                // 配置安全上下文（替换过时方法）
                .securityContexts(Collections.singletonList(securityContext()))
                
                .select() 
                .apis(RequestHandlerSelectors.basePackage("com.example.audiobackend.controller"))
                .paths(PathSelectors.any()) 
                .build();
    }

    private ApiInfo apiInfo() {
        return new ApiInfoBuilder()
                .title("音频后端系统接口文档") 
                .description("基于 Spring Boot + JWT 的后端接口文档，包含登录、用户管理等接口") 
                .version("1.0.0") 
                .build();
    }

    // 定义JWT的请求头规则
    private ApiKey apiKey() {
        return new ApiKey("JWT Token", "Authorization", "header");
    }

    // 配置安全上下文（核心：替换过时的forPaths方法）
    private SecurityContext securityContext() {
        return SecurityContext.builder()
                // 关联认证规则
                .securityReferences(defaultAuth())
                // ========== 重点：替换过时方法 ==========
                .operationSelector(operationContext -> 
                        // 匹配所有接口（和原来的forPaths(PathSelectors.any())效果一致）
                        PathSelectors.any().test(operationContext.requestMappingPattern())
                )
                .build();
    }

    // 认证范围（全局生效）
    private List<SecurityReference> defaultAuth() {
        AuthorizationScope authorizationScope = new AuthorizationScope("global", "accessEverything");
        AuthorizationScope[] scopes = new AuthorizationScope[1];
        scopes[0] = authorizationScope;
        return Collections.singletonList(new SecurityReference("JWT Token", scopes));
    }
}