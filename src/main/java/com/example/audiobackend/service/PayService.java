package com.example.audiobackend.service;

import com.example.audiobackend.dto.PayCallbackDTO;
import com.example.audiobackend.vo.PaymentVO;

/**
 * 支付。发起支付、处理平台回调、查支付状态。
 */
public interface PayService {

    /**
     * 发起支付：校验支付单归属、状态、订单是否过期，返回收银台参数。
     * 不写数据库 —— "用户打开了收银台"不是业务事实。
     */
    PaymentVO createPay(String payNo, Long userId);

    /**
     * 处理支付平台回调。
     *
     * @return 直接回给支付平台的纯文本：success 表示收到且不再重试，fail 表示让平台重试
     */
    String handleCallback(PayCallbackDTO dto);

    /**
     * 查支付单状态，前端轮询用。
     */
    Integer queryStatus(String payNo, Long userId);
}
