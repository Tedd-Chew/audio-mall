package com.example.audiobackend.mapper;

import com.example.audiobackend.entity.Order;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单数据访问。
 */
@Mapper
public interface OrderMapper {

    /** 新增订单，主键回填到 order.id */
    int insert(Order order);

    /** 按订单号查，前端轮询和订单详情用 */
    Order selectByOrderNo(@Param("orderNo") String orderNo);

    Order selectById(@Param("id") Long id);

    /**
     * 支付成功：UNPAID(0) → PAID(1)。
     *
     * <p>WHERE 带 status = 0，返回影响行数：
     * 1 = 本次是第一个改的，0 = 已被别人改过（重复回调，或订单已关闭）。调用方必须区分这两种 0。
     */
    int markPaid(@Param("id") Long id, @Param("payTime") LocalDateTime payTime);

    /**
     * 关单：UNPAID(0) → CLOSED(2)。
     *
     * <p>返回 1 才允许回滚库存；返回 0 说明已被支付或已被关闭，此时回滚库存会让库存凭空多出来。
     */
    int close(@Param("id") Long id, @Param("closeTime") LocalDateTime closeTime);

    /**
     * 扫描超时未支付订单，走 idx_status_expire(status, expire_time)，按 expire_time 排序避免 filesort。
     *
     * @param limit 单批上限，避免一次拉太多
     */
    List<Order> selectExpiredUnpaid(@Param("now") LocalDateTime now, @Param("limit") int limit);
}
