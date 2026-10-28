package com.example.audiobackend.mapper;

import com.example.audiobackend.entity.SeckillActivity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 秒杀活动数据访问。
 */
@Mapper
public interface SeckillActivityMapper {

    SeckillActivity selectById(@Param("id") Long id);

    /** 预热用：还没结束的活动（status != 2） */
    List<SeckillActivity> selectNotFinished();

    /** 结束扫描用：到点了但状态还没改成已结束的 */
    List<SeckillActivity> selectExpiredRunning(@Param("limit") int limit);

    /**
     * 扣减活动库存。
     *
     * <p>客户端消费 MQ 时调用，是秒杀库存的最终裁决。
     * WHERE 里的 stock >= 1 是"不超卖"的最后一道保证 —— Redis 多放了人进来，
     * 在这里会被挡住，最多是少卖，不会超卖。
     *
     * @return 1 = 扣成功，0 = 库存已经没了
     */
    int decreaseStock(@Param("id") Long id);

    /**
     * 关单回滚：把库存还回活动，<b>但仅当活动还没结束</b>。
     *
     * <p>为什么判断必须写进 WHERE，而不是先在 Java 里查状态再分支：
     * 查状态和改库存是两次数据库交互，中间活动可能被执行结束任务改成 FINISHED。
     * 那一瞬间还在"进行中"分支加的 1 件，会因为活动已结束而永远回不到商品库存。
     *
     * <p>写成条件更新后，这条 UPDATE 和 {@link #finish} 的 UPDATE 会锁同一行、串行执行，
     * 两种先后顺序结果都正确。返回的 affected 就是 CAS 的结果。
     *
     * @return 1 = 已加到活动库存；0 = 活动已结束，调用方应改还 {@code product.stock}
     */
    int increaseStockIfNotFinished(@Param("id") Long id);

    /**
     * 标记活动结束：RUNNING(1) → FINISHED(2)。
     *
     * @return 1 = 本次是第一个改的，0 = 已被处理过（多实例重复扫描时靠它兜）
     */
    int finish(@Param("id") Long id);
}
