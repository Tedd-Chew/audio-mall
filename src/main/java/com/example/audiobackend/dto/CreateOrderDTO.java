package com.example.audiobackend.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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

    /**
     * 客户端幂等键：同一次提交的重试必须复用同一个值。
     *
     * <p>暂时不是必填（方便测试和脚本调用）。不传就等于没有幂等保护 ——
     * 重复请求会真的下两张单。前端的正确做法是在"点击下单"那一刻生成一个 UUID，
     * 重试/刷新复用，下单成功后丢弃。
     */
    @Size(max = 64, message = "requestId 最长 64 个字符")
    private String requestId;
}
