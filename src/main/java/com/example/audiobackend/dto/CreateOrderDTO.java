package com.example.audiobackend.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 下单入参。
 * 目前只支持一次买一件商品 —— 没有购物车，也就没有多商品订单。
 */
@Data
public class CreateOrderDTO {

    @NotNull(message = "商品不能为空")
    private Long productId;

    @NotNull(message = "数量不能为空")
    @Min(value = 1, message = "数量至少 1 件")
    @Max(value = 10, message = "单次最多买 10 件")
    private Integer quantity;
}
