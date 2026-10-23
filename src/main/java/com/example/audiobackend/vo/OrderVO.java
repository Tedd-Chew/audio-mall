package com.example.audiobackend.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单信息，返回给前端。
 */
@Data
public class OrderVO {

    private String orderNo;

    private BigDecimal totalAmount;

    /** 0 待支付 / 1 已支付 / 2 已关闭 / 3 待付尾款 */
    private Integer status;

    private LocalDateTime createTime;

    /** 支付截止时间 —— 前端要拿它显示倒计时 */
    private LocalDateTime expireTime;

    /** 当前可支付的支付单号。前端拿它调 /api/pay/{payNo} 发起支付 */
    private String payNo;

    /** 对应支付单的状态：0 待支付 / 1 已支付 / 2 失败 / 3 已关闭 */
    private Integer paymentStatus;

    private List<OrderItemVO> items;
}
