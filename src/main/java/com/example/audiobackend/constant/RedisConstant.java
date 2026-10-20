package com.example.audiobackend.constant;

/**
 * Redis key 常量。
 * 统一格式：项目:模块:标识 —— 多个项目共用一个 Redis 时不会互相覆盖。
 */
public final class RedisConstant {

    /** 项目前缀，所有 key 都带上 */
    private static final String PREFIX = "audio:";

    /** 商品详情缓存：audio:product:info:{id} */
    public static final String PRODUCT_INFO = PREFIX + "product:info:";

    /** 商品空值缓存：audio:product:null:{id} —— 防止同一个不存在的 id 反复打数据库 */
    public static final String PRODUCT_NULL = PREFIX + "product:null:";

    /** 商品浏览量：audio:product:view:{id} —— Redis 累加，定时回写数据库 */
    public static final String PRODUCT_VIEW = PREFIX + "product:view:";

    /** 商品缓存重建锁：audio:product:lock:{id} —— 目前没用上，Sprint 8 做多实例时启用 */
    public static final String PRODUCT_LOCK = PREFIX + "product:lock:";

    /** 登录 token：audio:user:token:{userId} */
    public static final String USER_TOKEN = PREFIX + "user:token:";

    private RedisConstant() {
    }
}
