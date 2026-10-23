package com.example.audiobackend.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 发起支付的响应，前端拿它去"收银台"。
 */
@Data
public class PaymentVO {

    private String payNo;

    private String orderNo;

    private BigDecimal amount;

    /** 模拟收银台地址。真实场景这里是支付平台返回的 payUrl 或调起支付用的参数串 */
    private String payUrl;

    /** 订单的支付截止时间，前端据此倒计时；到点后不应再发起支付 */
    private LocalDateTime expireTime;
}
