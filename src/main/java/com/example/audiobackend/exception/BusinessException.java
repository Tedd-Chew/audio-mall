package com.example.audiobackend.exception;

public class BusinessException extends RuntimeException {
    private int code; // 错误码（默认500）
    private String message;

    // 构造器1：只传msg（匹配你原有Result.error(msg)，默认code=500）
    public BusinessException(String message) {
        super(message);
        this.code = 500; // 和你原有error方法一致
        this.message = message;
    }

    // 构造器2：传code+msg（适配新增的Result.error(code, msg)）
    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
        this.message = message;
    }

    // Getter（全局异常处理器需要）
    public int getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }
}