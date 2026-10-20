package com.example.audiobackend.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 商品信息，返回给前端。
 * 跟实体分开：实体对应数据库，VO 对应接口返回，两者字段不一定一致
 * （比如 categoryName 数据库里没有，是联表查出来拼上的）。
 */
@Data
public class ProductVO {

    private Long id;

    private String name;

    private Long categoryId;

    /** 分类名。实体里没有这个字段，从 category 表查出来填上 */
    private String categoryName;

    private BigDecimal price;

    private Integer stock;

    private Integer sales;

    private String coverImg;

    private String description;

    /** 1 上架 / 0 下架 */
    private Integer status;
}
