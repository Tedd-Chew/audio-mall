package com.example.audiobackend.mapper;

import com.example.audiobackend.entity.Payment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 支付单数据访问。
 */
@Mapper
public interface PaymentMapper {

    int insert(Payment payment);

    /** 支付回调按支付单号查 —— 幂等的依据 */
    Payment selectByPayNo(@Param("payNo") String payNo);

    List<Payment> selectByOrderId(@Param("orderId") Long orderId);
}
