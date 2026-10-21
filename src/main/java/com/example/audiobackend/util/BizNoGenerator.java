package com.example.audiobackend.util;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 业务单号生成：订单号、支付单号。
 *
 * 格式：前缀 + 毫秒时间戳 + 3 位序列。
 * 单实例下不会重复；多实例时两个进程的序列各自从 0 开始，同一毫秒可能撞号，
 * 靠数据库的唯一索引兜底（撞了插入失败）。真要多实例部署，得换成分库分表用的雪花算法。
 */
public final class BizNoGenerator {

    private static final AtomicLong ORDER_SEQ = new AtomicLong();
    private static final AtomicLong PAY_SEQ = new AtomicLong();

    public static String orderNo() {
        return "ORD" + System.currentTimeMillis() + sequence(ORDER_SEQ);
    }

    public static String payNo() {
        return "PAY" + System.currentTimeMillis() + sequence(PAY_SEQ);
    }

    private static String sequence(AtomicLong seq) {
        long n = seq.incrementAndGet() % 1000;
        // 手动补零，比 String.format 快
        if (n < 10) {
            return "00" + n;
        }
        if (n < 100) {
            return "0" + n;
        }
        return String.valueOf(n);
    }

    private BizNoGenerator() {
    }
}
