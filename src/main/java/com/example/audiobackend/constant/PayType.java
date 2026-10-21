package com.example.audiobackend.constant;

/**
 * 支付类型。一张支付单只对应一次支付行为。
 */
public final class PayType {

    /** 预售定金 */
    public static final int DEPOSIT = 1;

    /** 预售尾款 */
    public static final int FINAL = 2;

    /** 普通 / 秒杀：一次性付全款 */
    public static final int FULL = 3;

    private PayType() {
    }
}
