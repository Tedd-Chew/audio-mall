package com.example.audiobackend.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 秒杀活动信息，给前端展示用。
 *
 * <p>数据来自数据库（账本），不是 Redis —— 用户在详情页看到的"剩余"可能有几十秒延迟，
 * 但真实可抢名额由 Lua 在 Redis 里裁决，展示偏差不影响正确性。
 */
@Data
public class SeckillActivityVO {

    private Long actId;

    private Long productId;

    private BigDecimal seckillPrice;

    /** 剩余活动库存 */
    private Integer stock;

    private Integer limitPerUser;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    /** 0 未开始 / 1 进行中 / 2 已结束 */
    private Integer status;
}
