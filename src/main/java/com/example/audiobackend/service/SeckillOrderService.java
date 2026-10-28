package com.example.audiobackend.service;

import com.example.audiobackend.vo.SeckillResultVO;

/**
 * 秒杀下单。
 */
public interface SeckillOrderService {

    /**
     * 抢购。
     *
     * <p>当前是批次 2a：只做 Redis 扣减，不写数据库。
     * 返回的 orderNo 还没有对应的订单行，批次 2b 才落库。
     *
     * @return 抢到则带 orderNo；没抢到 orderNo 为 null，message 说明原因
     */
    SeckillResultVO seckill(Long actId, Long userId);
}
