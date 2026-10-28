package com.example.audiobackend.constant;

/**
 * 秒杀活动在 Redis meta Hash 里的字段名。
 *
 * <p>这些名字是「写入方」（预热）和「读取方」（Lua 脚本）之间的契约，
 * 改任何一个都要两边同时改，否则 Lua 读不到值会走兜底逻辑。
 */
public final class SeckillMetaField {

    public static final String START_TIME = "startTime";

    public static final String END_TIME = "endTime";

    /** 每人限购件数。Lua 查不了库，只能靠预热写进来 */
    public static final String LIMIT_PER_USER = "limitPerUser";

    public static final String PRODUCT_ID = "productId";

    /** 秒杀价快照：以"抢到那一刻"的价格为准，活动后来改价不影响已排队的人 */
    public static final String SECKILL_PRICE = "seckillPrice";

    private SeckillMetaField() {
    }
}
