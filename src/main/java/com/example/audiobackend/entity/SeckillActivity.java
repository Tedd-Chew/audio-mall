package com.example.audiobackend.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 秒杀活动。
 *
 * <p>库存有两份，角色不同：这张表的 {@code stock} 是真实账本，随每笔成交递减；
 * Redis 里那份只是入口的快速过滤器。活动结束时这张表剩下的就是没卖掉的，还回商品库存。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SeckillActivity {

    private Long id;

    private Long productId;

    /** 秒杀价，不是商品原价 */
    private BigDecimal seckillPrice;

    /** 剩余活动库存。创建活动时从 product.stock 扣出来，每成交一笔减 1 */
    private Integer stock;

    /** 每人限购件数，当前固定 1 */
    private Integer limitPerUser;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    /** 见 SeckillStatus：0 未开始 / 1 进行中 / 2 已结束 */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
