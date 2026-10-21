package com.example.audiobackend.mapper;

import com.example.audiobackend.entity.Order;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

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
}
