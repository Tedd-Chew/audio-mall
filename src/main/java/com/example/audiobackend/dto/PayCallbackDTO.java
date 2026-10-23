package com.example.audiobackend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 支付平台回调的入参。
 *
 * <p>这个接口不走 JWT —— 支付平台不是我们的用户，没有 token。
 * 身份靠 {@code sign} 证明，所以 sign、timestamp 必须参与验签。
 */
@Data
public class PayCallbackDTO {

    /** 我方支付单号（对应平台的 out_trade_no） */
    @NotBlank(message = "payNo 不能为空")
    private String payNo;

    /** 支付平台的交易号（对应平台的 transaction_id），仅记录用 */
    private String tradeNo;

    /** 支付结果，取值见 PayResult */
    @NotBlank(message = "result 不能为空")
    private String result;

    /** 秒级时间戳。参与签名，用来判断这条回调是不是刚发的 */
    @NotNull(message = "timestamp 不能为空")
    private Long timestamp;

    /** 签名，见 SignatureUtil */
    @NotBlank(message = "sign 不能为空")
    private String sign;
}
