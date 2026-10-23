package com.example.audiobackend.controller;

import com.example.audiobackend.constant.PayResult;
import com.example.audiobackend.util.SignatureUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.HashMap;
import java.util.Map;

/**
 * 模拟支付平台。仅用于开发和演示，生产必须删掉。
 *
 * <p>为什么要有它：验签和幂等的漏洞只在"回调从外部涌进来"时才会暴露。
 * 如果后端直接调用自己的 Service 冒充回调，就绕过了 HTTP、绕过了签名，
 * 也就测不出任何安全相关的问题。这里用 RestClient 真的发一次 HTTP 请求，
 * 才能让 {@code /api/pay/callback} 走完整的真实路径。
 *
 * <p>它拿着和 PayServiceImpl 相同的密钥 —— 这就是"支付平台和我方共享密钥"的样子。
 */
@RestController
@RequestMapping("/api/mock-pay")
public class MockPayPlatformController {

    private static final String SIGN_KEY_PAY_NO = "payNo";
    private static final String SIGN_KEY_TRADE_NO = "tradeNo";
    private static final String SIGN_KEY_RESULT = "result";
    private static final String SIGN_KEY_TIMESTAMP = "timestamp";

    @Value("${payment.callback-secret}")
    private String callbackSecret;

    private final RestClient restClient = RestClient.create();

    /**
     * 模拟收银台页面。真实场景这里是支付平台的付款页。
     */
    @GetMapping(value = "/{payNo}", produces = MediaType.TEXT_HTML_VALUE)
    public String cashier(@PathVariable String payNo) {
        return """
                <!doctype html>
                <html lang="zh-CN">
                <head><meta charset="utf-8"><title>模拟收银台</title></head>
                <body style="font-family:system-ui;max-width:420px;margin:80px auto;text-align:center">
                  <h2>模拟支付平台</h2>
                  <p>支付单号：<code>%s</code></p>
                  <form method="post" action="/api/mock-pay/%s/confirm">
                    <button type="submit"
                            style="padding:12px 32px;font-size:16px;cursor:pointer">
                      确认支付
                    </button>
                  </form>
                  <p style="color:#888;font-size:13px;margin-top:32px">
                    点击后会由"支付平台"带着签名回调 /api/pay/callback
                  </p>
                </body>
                </html>
                """.formatted(payNo, payNo);
    }

    /**
     * 用户点了"确认支付"。
     *
     * <p>这里做的事情就是真实支付平台的服务器在做的：算签名，然后主动 POST 到商户的回调地址。
     *
     * @param times 连续发几次回调，用来验证幂等。默认 1；填 3 就是"平台重试 3 次"
     */
    @PostMapping(value = "/{payNo}/confirm", produces = MediaType.TEXT_PLAIN_VALUE)
    public String confirm(@PathVariable String payNo,
                          @RequestParam(defaultValue = "1") int times) {
        StringBuilder result = new StringBuilder();
        for (int i = 1; i <= Math.max(times, 1); i++) {
            String ack = sendCallback(payNo);
            result.append("第 ").append(i).append(" 次回调，服务端应答：").append(ack).append('\n');
        }
        return result.toString();
    }

    /**
     * 组参数 → 签名 → 真的 POST 到 /api/pay/callback。
     */
    private String sendCallback(String payNo) {
        long timestamp = System.currentTimeMillis() / 1000;
        String tradeNo = "MOCKTRADE" + timestamp;

        // 参与签名的字段必须和 PayServiceImpl.toSignParams 完全一致
        Map<String, String> signParams = new HashMap<>(8);
        signParams.put(SIGN_KEY_PAY_NO, payNo);
        signParams.put(SIGN_KEY_TRADE_NO, tradeNo);
        signParams.put(SIGN_KEY_RESULT, PayResult.SUCCESS);
        signParams.put(SIGN_KEY_TIMESTAMP, String.valueOf(timestamp));
        String sign = SignatureUtil.sign(signParams, callbackSecret);

        // 请求体的字段名要和 PayCallbackDTO 的属性名一致
        Map<String, Object> payload = new HashMap<>(8);
        payload.put(SIGN_KEY_PAY_NO, payNo);
        payload.put(SIGN_KEY_TRADE_NO, tradeNo);
        payload.put(SIGN_KEY_RESULT, PayResult.SUCCESS);
        payload.put(SIGN_KEY_TIMESTAMP, timestamp);
        payload.put("sign", sign);

        String callbackUrl = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/pay/callback")
                .toUriString();

        return restClient.post()
                .uri(callbackUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .body(String.class);
    }
}
