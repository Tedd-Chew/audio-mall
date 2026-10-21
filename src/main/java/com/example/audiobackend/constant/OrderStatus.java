package com.example.audiobackend.constant;

/**
 * 订单状态。
 * 普通 / 秒杀：0 待支付 → 1 已支付，或者 0 → 2 已关闭
 * 预售：0 待付定金 → 3 待付尾款 → 1 已支付
 */
public final class OrderStatus {

    public static final int UNPAID = 0;

    public static final int PAID = 1;

    /** 已关闭：超时未付、主动取消 */
    public static final int CLOSED = 2;

    /** 预售专用：定金已付，等尾款 */
    public static final int WAIT_FINAL = 3;

    private OrderStatus() {
    }
}
