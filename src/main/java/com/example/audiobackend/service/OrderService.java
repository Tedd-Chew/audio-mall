package com.example.audiobackend.service;

import com.example.audiobackend.dto.CreateOrderDTO;
import com.example.audiobackend.vo.OrderVO;

import java.util.List;

/**
 * 订单服务。
 */
public interface OrderService {

    /**
     * 普通下单。返回订单号。
     * 失败抛 BusinessException（库存不足、商品不存在、已下架）。
     */
    String createNormalOrder(Long userId, CreateOrderDTO dto);

    /** 按订单号查详情，前端轮询用 */
    OrderVO getByOrderNo(String orderNo);

    /**
     * 扫描已超时且未支付的订单 id，供关单任务分批处理。
     * 只查 id 不查整行 —— 关单时反正要重新判断状态，多查的字段没用。
     */
    List<Long> findExpiredOrderIds(int limit);

    /**
     * 关闭单个订单，独立事务。
     *
     * <p>独立事务是为了让一条脏数据不拖累整批：某单关不掉只回滚它自己。
     * 先改状态拿到结果再回滚库存 —— 顺序反了会在崩溃时把库存加两次。
     *
     * @return true 表示本次真的关掉了；false 表示已被支付或已被关闭，什么都没做
     */
    boolean closeIfUnpaid(Long orderId);
}
