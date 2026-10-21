package com.example.audiobackend.service;

import com.example.audiobackend.dto.CreateOrderDTO;
import com.example.audiobackend.vo.OrderVO;

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
}
