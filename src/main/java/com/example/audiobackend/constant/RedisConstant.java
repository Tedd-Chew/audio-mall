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

    /** 秒杀库存：audio:seckill:stock:{actId} —— String，预热时从 DB 写入，Lua 扣减 */
    public static final String SECKILL_STOCK = PREFIX + "seckill:stock:";

    /**
     * 秒杀已购记录：audio:seckill:bought:{actId} —— Hash，field = userId，value = 已买件数。
     * 用 Hash 而不是 Set，是因为要支持"每人限购 N 件"：Set 只能回答"抢没抢过"，回答不了"抢了几件"。
     * **必须设 TTL**，否则活动越开越多，这个 Hash 永远不释放。
     */
    public static final String SECKILL_BOUGHT = PREFIX + "seckill:bought:";

    /** 秒杀时间窗：audio:seckill:meta:{actId} —— 供 Lua 判开始/结束，避免每个请求查一次库 */
    public static final String SECKILL_META = PREFIX + "seckill:meta:";

    /** 秒杀限流令牌桶：audio:seckill:rate:{actId} —— Hash，存 tokens 和上次补充时间 */
    public static final String SECKILL_RATE = PREFIX + "seckill:rate:";

    /**
     * 秒杀幂等门：audio:seckill:idem:{actId}:{userId}:{requestId} —— String，值 = orderNo。
     *
     * <p>在扣库存**之前**用 SET NX 占位。为什么值要存 orderNo：重复请求能直接把上次的单号还回去，
     * 用户看到的还是同一张订单，而不是一句"重复提交"。
     *
     * <p>TTL 600 秒：覆盖"网络超时重试 + 刷新重试"的窗口，超过 10 分钟就当新的意图。
     */
    public static final String SECKILL_IDEM = PREFIX + "seckill:idem:";

    private RedisConstant() {
    }
}
