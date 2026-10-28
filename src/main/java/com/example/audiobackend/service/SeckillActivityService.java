package com.example.audiobackend.service;

import com.example.audiobackend.vo.SeckillActivityVO;

import java.util.List;

/**
 * 秒杀活动的查询、预热、库存回滚与结束收尾。
 */
public interface SeckillActivityService {

    /** 活动详情，给前端展示 */
    SeckillActivityVO getById(Long actId);

    /**
     * 把所有未结束活动的库存和时间窗写入 Redis。
     *
     * @return 新写入的库存 key 数量（已存在的不会覆盖，所以可能小于活动总数）
     */
    int warmUp();

    /**
     * 订单关闭时，把占掉的名额还回去。
     *
     * <p>还到哪里取决于活动是否已结束：
     * 活动进行中 → 还 {@code seckill_activity.stock} + Redis 名额；
     * 活动已结束 → 剩余库存早已还回商品，这 1 件也直接还给 {@code product.stock}。
     *
     * @param actId  活动 id
     * @param userId 抢到名额的用户，用于把已购件数减回去
     */
    void releaseSeckillStock(Long actId, Long userId);

    /** 扫出到点但还没改状态的进行中活动 id */
    List<Long> findExpiredRunningIds(int limit);

    /**
     * 结束单个活动，独立事务：改状态 → 剩余库存还回商品 → 清理 Redis。
     *
     * @return true 表示本次真的结束了；false 表示已被别的实例处理过
     */
    boolean finishOneActivity(Long actId);
}
