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

    private List<OrderItemVO> items;
}
