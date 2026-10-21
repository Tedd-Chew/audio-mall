package com.example.audiobackend.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付单，一次支付行为一行。
 * 普通 / 秒杀订单是一张（全额），预售订单是两张（定金 + 尾款）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Payment {

    private Long id;

    /** 支付单号 —— 支付回调幂等的依据，不能挂在订单号上 */
    private String payNo;

    private Long orderId;

    private Long userId;

    /** 见 PayType：1 定金 / 2 尾款 / 3 全额 */
    private Integer payType;

    /** 本次支付金额 */
    private BigDecimal amount;

    /** 见 PaymentStatus：0 待支付 / 1 已支付 / 2 失败 / 3 已关闭 */
    private Integer status;

    private LocalDateTime payTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
