package com.example.audiobackend.service.impl;

import com.example.audiobackend.constant.OrderStatus;
import com.example.audiobackend.constant.PayResult;
import com.example.audiobackend.constant.PaymentStatus;
import com.example.audiobackend.dto.PayCallbackDTO;
import com.example.audiobackend.entity.Order;
import com.example.audiobackend.entity.Payment;
import com.example.audiobackend.exception.BusinessException;
import com.example.audiobackend.mapper.OrderMapper;
import com.example.audiobackend.mapper.PaymentMapper;
import com.example.audiobackend.service.PayService;
import com.example.audiobackend.util.SignatureUtil;
import com.example.audiobackend.vo.PaymentVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PayServiceImpl implements PayService {

    private final PaymentMapper paymentMapper;
    private final OrderMapper orderMapper;

    @Value("${payment.callback-secret}")
    private String callbackSecret;

    @Value("${payment.callback-tolerance-seconds:300}")
    private long callbackToleranceSeconds;

    @Override
    public PaymentVO createPay(String payNo, Long userId) {
        Payment payment = paymentMapper.selectByPayNo(payNo);
        if (payment == null) {
            throw new BusinessException("支付单不存在");
        }
        if (!payment.getUserId().equals(userId)) {
            throw new BusinessException(403, "无权操作该支付单");
        }
        if (payment.getStatus() == null || payment.getStatus() != PaymentStatus.UNPAID) {
            throw new BusinessException("该支付单已处理，请勿重复支付");
        }

        Order order = orderMapper.selectById(payment.getOrderId());
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        if (order.getStatus() != null && order.getStatus() == OrderStatus.CLOSED) {
            throw new BusinessException("订单已关闭");
        }
        // 用户可能卡在最后一秒才点支付，此时订单已被关单任务关掉，
        // 不拦的话会出现"钱收了、单没了"
        if (order.getExpireTime() != null && LocalDateTime.now().isAfter(order.getExpireTime())) {
            throw new BusinessException("订单已超时，无法支付");
        }

        PaymentVO vo = new PaymentVO();
        vo.setPayNo(payment.getPayNo());
        vo.setOrderNo(order.getOrderNo());
        vo.setAmount(payment.getAmount());
        vo.setPayUrl("/api/mock-pay/" + payment.getPayNo());
        vo.setExpireTime(order.getExpireTime());
        return vo;
    }

    /**
     * 处理回调。整个方法在一个事务里 —— 支付单和订单的状态变更必须同生共死。
     *
     * <p>如果只改了支付单就崩了，订单会永远停在待支付，而后续回调都会被
     * 第一道闸门当成"重复"吞掉，再也修不回来。事务回滚 + 平台重试才能自愈。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String handleCallback(PayCallbackDTO dto) {
        // ① 验签：证明这条回调确实来自支付平台，且内容没被改
        if (!SignatureUtil.verify(toSignParams(dto), dto.getSign(), callbackSecret)) {
            log.warn("支付回调验签失败，payNo={}", dto.getPayNo());
            return PayResult.CALLBACK_NACK;
        }

        // ② 时效：签名对了但可能是很久以前那条被重放
        if (!SignatureUtil.isTimestampFresh(dto.getTimestamp(), callbackToleranceSeconds)) {
            log.warn("支付回调时间戳超出容忍窗口，payNo={}，timestamp={}",
                    dto.getPayNo(), dto.getTimestamp());
            return PayResult.CALLBACK_NACK;
        }

        // 支付失败也是一种合法回调，钱没到，什么都不用改
        if (!PayResult.SUCCESS.equals(dto.getResult())) {
            log.info("收到支付失败回调，payNo={}，result={}", dto.getPayNo(), dto.getResult());
            return PayResult.CALLBACK_ACK;
        }

        Payment payment = paymentMapper.selectByPayNo(dto.getPayNo());
        if (payment == null) {
            log.warn("回调的支付单不存在，payNo={}", dto.getPayNo());
            return PayResult.CALLBACK_NACK;
        }

        LocalDateTime now = LocalDateTime.now();

        // ③ 闸门一：支付单。挡住"同一个 payNo 来了多次"（平台重试）
        if (paymentMapper.markPaid(dto.getPayNo(), now) == 0) {
            log.info("重复回调，已忽略，payNo={}", dto.getPayNo());
            return PayResult.CALLBACK_ACK;
        }

        // ④ 闸门二：订单。挡住"关单和支付撞车"
        if (orderMapper.markPaid(payment.getOrderId(), now) == 0) {
            Order order = orderMapper.selectById(payment.getOrderId());
            if (order != null && order.getStatus() != null && order.getStatus() == OrderStatus.CLOSED) {
                // 钱到了、单关了。支付单已如实记为已支付，这个矛盾本身就是要人工介入的信号
                log.error("支付成功但订单已关闭，需退款，payNo={}，orderNo={}，amount={}",
                        payment.getPayNo(), order.getOrderNo(), payment.getAmount());
            }
            // 返回 success：订单已经是终态，让平台重试一万次也不会变，只会刷爆日志
            return PayResult.CALLBACK_ACK;
        }

        // ⑤ 走到这里说明本次是第一个处理它的，可以执行副作用
        //    累加销量等 Sprint 7 排行榜再补
        log.info("支付成功，payNo={}，orderId={}，amount={}",
                payment.getPayNo(), payment.getOrderId(), payment.getAmount());
        return PayResult.CALLBACK_ACK;
    }

    @Override
    public Integer queryStatus(String payNo, Long userId) {
        Payment payment = paymentMapper.selectByPayNo(payNo);
        if (payment == null) {
            throw new BusinessException("支付单不存在");
        }
        if (!payment.getUserId().equals(userId)) {
            throw new BusinessException(403, "无权查看该支付单");
        }
        return payment.getStatus();
    }

    /**
     * 组装参与签名的参数。
     *
     * <p>字段清单必须和"支付平台"那边完全一致 —— 少一个多一个都会导致
     * 两边的规范串不同，签名永远对不上。
     */
    private Map<String, String> toSignParams(PayCallbackDTO dto) {
        Map<String, String> params = new HashMap<>(8);
        params.put("payNo", dto.getPayNo());
        params.put("tradeNo", dto.getTradeNo());
        params.put("result", dto.getResult());
        params.put("timestamp", String.valueOf(dto.getTimestamp()));
        return params;
    }
}
