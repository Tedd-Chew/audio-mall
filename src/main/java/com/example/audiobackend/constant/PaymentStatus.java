package com.example.audiobackend.constant;

/**
 * 支付单状态。
 */
public final class PaymentStatus {

    public static final int UNPAID = 0;

    public static final int PAID = 1;

    public static final int FAILED = 2;

    public static final int CLOSED = 3;

    private PaymentStatus() {
    }
}
