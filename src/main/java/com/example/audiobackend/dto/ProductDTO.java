package com.example.audiobackend.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 新增 / 修改商品的入参。
 * 只放允许前端传的字段 —— id、sales、createTime 这些由服务端决定，
 * 让前端直接传实体的话，它能改销量、改创建时间。
 */
@Data
public class ProductDTO {

    @NotBlank(message = "商品名不能为空")
    @Size(max = 100, message = "商品名不能超过 100 个字")
    private String name;

    @NotNull(message = "分类不能为空")
    private Long categoryId;

    @NotNull(message = "价格不能为空")
    @DecimalMin(value = "0.01", message = "价格必须大于 0")
    private BigDecimal price;

    @NotNull(message = "库存不能为空")
    @Min(value = 0, message = "库存不能为负")
    private Integer stock;

    @Size(max = 255, message = "图片地址过长")
    private String coverImg;

    private String description;

    /** 1 上架 / 0 下架，不传由 Service 兜底成上架 */
    private Integer status;
}
