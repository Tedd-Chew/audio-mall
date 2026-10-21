package com.example.audiobackend.constant;

/**
 * 售卖类型。三种入口共用同一条「下单 → 支付」主链路。
 */
public final class SaleType {

    public static final int NORMAL = 1;

    public static final int SECKILL = 2;

    public static final int PRESALE = 3;

    private SaleType() {
    }
}
