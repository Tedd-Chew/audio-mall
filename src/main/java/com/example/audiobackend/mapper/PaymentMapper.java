package com.example.audiobackend.mapper;

import com.example.audiobackend.entity.Payment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
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

    /**
     * 支付单置为已支付：UNPAID(0) → PAID(1)。
     *
     * <p>回调幂等的第一道闸门。返回 0 = 这个 payNo 已经处理过，属于重复回调。
     * 平台保证 out_trade_no 唯一（同一单号不能支付两次），所以重复只会来自同一个 payNo。
     */
    int markPaid(@Param("payNo") String payNo, @Param("payTime") LocalDateTime payTime);
}
