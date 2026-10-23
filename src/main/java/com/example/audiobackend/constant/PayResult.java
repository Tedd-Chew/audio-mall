package com.example.audiobackend.constant;

/**
 * 支付回调协议中的固定取值。
 * 这些是跟"支付平台"约定的字符串，改动等于改协议，必须两边同步。
 */
public final class PayResult {

    /** 支付成功 */
    public static final String SUCCESS = "SUCCESS";

    /** 支付失败 */
    public static final String FAIL = "FAIL";

    /**
     * 回调处理完成后返回给支付平台的响应体。
     *
     * <p>支付平台只认这个纯文本。返回 JSON 包装、返回 200 带别的 body，
     * 都会被认为"没收到"，从而触发重试（微信支付最多重试 15 次）。
     */
    public static final String CALLBACK_ACK = "success";

    /** 处理失败时应答，平台会重试 */
    public static final String CALLBACK_NACK = "fail";

    private PayResult() {
    }
}
