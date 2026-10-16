package com.example.audiobackend.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.example.audiobackend.exception.BusinessException;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
// 1. 替换废弃的 SignatureException → 新增以下2个新异常类
import io.jsonwebtoken.security.SignatureException;
import io.jsonwebtoken.UnsupportedJwtException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
/**
 * 全局异常处理器
 * 作用：捕获应用中抛出的异常，统一处理并返回友好的错误信息给前端
 * 优点：避免每个接口都写 try-catch，减少重复代码，提升维护性
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // Token过期（不变）
    @ExceptionHandler(ExpiredJwtException.class)
    public Result<Void> handleExpiredJwtException(ExpiredJwtException e) {
        return Result.error(401, "Token已过期，请重新登录");
    }

    // 2. 替换：用 security.SignatureException 处理签名错误
    @ExceptionHandler(SignatureException.class)
    public Result<Void> handleSignatureException(SignatureException e) {
        return Result.error(401, "Token无效（签名错误）");
    }

    // Token格式错误（不变）
    @ExceptionHandler(MalformedJwtException.class)
    public Result<Void> handleMalformedJwtException(MalformedJwtException e) {
        return Result.error(401, "Token格式错误");
    }

    // 可选：不支持的Token类型
    @ExceptionHandler(UnsupportedJwtException.class)
    public Result<Void> handleUnsupportedJwtException(UnsupportedJwtException e) {
        return Result.error(401, "不支持的Token类型");
    }

    // 自定义业务异常（不变）
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e) {
        return Result.error(e.getCode(), e.getMessage());
    }

    // 兜底异常（不变）
    @ExceptionHandler(Exception.class)
    public Result<Void> handle(Exception e) {
        // ① 打印详细异常栈到日志文件（开发/生产排查问题用）
        log.error("系统异常：", e);
        // ② 返回友好提示给前端（不暴露具体异常信息）
        return Result.error("系统繁忙，请稍后重试！");
    }
}