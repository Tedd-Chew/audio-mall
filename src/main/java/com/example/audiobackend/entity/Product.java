package com.example.audiobackend.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品。
 * 扁平模型：每个配色 / 版本独立一行，不做 SPU/SKU 两层。
 * 纯 POJO，不带任何 ORM 注解 —— 换持久层框架时这个类不用动。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Product {

    private Long id;

    /** 商品名，如 "JBL Charge 5 黑色" */
    private String name;

    private Long categoryId;

    /** 售价 */
    private BigDecimal price;

    /** 库存，秒杀扣的就是它 */
    private Integer stock;

    /** 销量，支付成功时累加 */
    private Integer sales;

    /** 浏览量，Redis 累加后定时回写 */
    private Integer viewCount;

    /** 封面图地址 */
    private String coverImg;

    private String description;

    /** 1 上架 / 0 下架 */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
