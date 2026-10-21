package com.example.audiobackend.entity;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单主表。
 * 商品快照在 order_item 里，这张表只管"订单整体"。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Order {

    private Long id;

    /** 订单号，对外暴露的业务标识 */
    private String orderNo;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    private BigDecimal totalAmount;

    /** 见 SaleType：1 普通 / 2 秒杀 / 3 预售 */
    private Integer saleType;

    /** 见 OrderStatus：0 待支付 / 1 已支付 / 2 已关闭 / 3 待付尾款 */
    private Integer status;

    /** 支付截止时间，超时扫描的判据 */
    private LocalDateTime expireTime;

    private LocalDateTime payTime;

    private LocalDateTime closeTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
