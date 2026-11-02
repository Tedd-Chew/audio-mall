package com.example.audiobackend.service;

import com.example.audiobackend.dto.SeckillOrderMessage;
import com.example.audiobackend.vo.SeckillResultVO;

/**
 * 秒杀下单。
 */
public interface SeckillOrderService {

    /**
     * 抢购。
     *
     * <p>返回的 orderNo 不是"订单已创建"，而是"抢到名额，订单生成中"。
     * 调用方要拿它去查 {@code GET /api/order/{orderNo}}，查到订单才算真的成功。
     *
     * @param requestId 客户端幂等键。可以不传（不启用幂等）；传了的话，
     *                  同一个 (用户, requestId) 重复提交会拿到第一次那个订单号
     * @return 抢到则带 orderNo；没抢到 orderNo 为 null，message 说明原因
     */
    SeckillResultVO seckill(Long actId, Long userId, String requestId);

    /**
     * 撤销这条消息对应的那次扣减：还库存、bought -1、删幂等门。
     *
     * <p>消费者撞上 {@code uk_user_request} 时调用 —— 说明这次扣减注定建不出订单，
     * 不撤销就是永久少卖一件。
     *
     * <p><b>可以重复调用</b>：幂等门里存的单号就是"这次扣减还没被撤销"的证据，
     * 撤完门就删掉了，第二次调用什么都不做。
     *
     * @return true 表示本次真的撤销了；false 表示没撤销（已经撤过 / 门已过期 / 没买过）
     */
    boolean compensateRejected(SeckillOrderMessage message);
}
