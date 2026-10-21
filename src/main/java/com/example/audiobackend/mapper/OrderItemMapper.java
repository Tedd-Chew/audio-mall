package com.example.audiobackend.mapper;

import com.example.audiobackend.entity.OrderItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 订单明细数据访问。
 */
@Mapper
public interface OrderItemMapper {

    int insert(OrderItem item);

    /** 按订单查明细 */
    List<OrderItem> selectByOrderId(@Param("orderId") Long orderId);
}
