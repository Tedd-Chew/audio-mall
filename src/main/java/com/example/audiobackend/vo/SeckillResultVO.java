package com.example.audiobackend.vo;

import lombok.Data;

/**
 * 秒杀下单的返回。
 *
 * <p>注意 {@code orderNo} 不是"订单已创建"的承诺 —— 它是"你抢到了名额，订单正在生成"。
 * 前端要拿它去轮询订单详情，查到订单才算真的成功。
 */
@Data
public class SeckillResultVO {

    /** 抢到了才返回；没抢到为 null */
    private String orderNo;

    /** 给用户看的提示 */
    private String message;
}
