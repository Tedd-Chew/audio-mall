package com.example.audiobackend.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 订单里的一件商品（下单时的快照）。
 */
@Data
public class OrderItemVO {

    private Long productId;

    private String productName;

    private String productImg;

    private BigDecimal unitPrice;

    private Integer quantity;
}
