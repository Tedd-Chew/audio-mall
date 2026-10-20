package com.example.audiobackend.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 商品分类。纯 POJO，不带 ORM 注解。
 * 注意：数据库表名是 category，不是 product_category。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductCategory {

    private Long id;

    private String name;

    /** 排序，小的在前 */
    private Integer sort;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
