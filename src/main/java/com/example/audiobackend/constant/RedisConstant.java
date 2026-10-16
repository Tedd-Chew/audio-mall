package com.example.audiobackend.constant;

public class RedisConstant {
    public static final String PRODUCT_KEY_PREFIX = "product:";
    public static final String PRODUCT_VIEW_COUNT_PREFIX = "product:viewCount:";
    public static final String PRODUCT_NULL_PREFIX = "product:null:";
    public static final String LOCK_PRODUCT_PREFIX = "lock:product:";
    public static final String TOKEN_PREFIX = "token:";

    private RedisConstant() {
    }
}