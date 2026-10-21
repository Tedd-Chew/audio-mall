package com.example.audiobackend.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单明细，存的是下单那一刻的商品快照。
 * 商品以后改名、调价，历史订单要显示当时的信息。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderItem {

    private Long id;

    private Long orderId;

    private Long productId;

    /** 快照：下单时的商品名 */
    private String productName;

    /** 快照：下单时的封面图 */
    private String productImg;

    /** 快照：下单时的单价 */
    private BigDecimal unitPrice;

    private Integer quantity;

    private LocalDateTime createTime;
}
