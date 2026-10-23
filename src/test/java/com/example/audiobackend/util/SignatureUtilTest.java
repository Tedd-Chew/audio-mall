package com.example.audiobackend.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验签的三道防线，一条测试对应一种攻击。
 *
 * <p>这些测试不是为了覆盖率，是为了把「不这么做会被怎么打」变成可以反复运行的证据。
 * 哪天有人把 {@code canonicalize} 改成直接字符串相加，{@code 拼接歧义} 那条会立刻红。
 */
class SignatureUtilTest {

    /** 支付平台和我方共享的密钥，攻击者拿不到 */
    private static final String SECRET = "audio-mall-test-secret";

    /** 攻击者自己猜的密钥 */
    private static final String WRONG_SECRET = "guess-guess-guess";

    private static final long TOLERANCE_SECONDS = 300L;

    private static Map<String, String> callbackParams(String payNo, String result, long timestamp) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("payNo", payNo);
        params.put("result", result);
        params.put("timestamp", String.valueOf(timestamp));
        return params;
    }

    @Test
    @DisplayName("参数顺序不影响签名 —— 证明排序真的生效了")
    void parameterOrderDoesNotChangeSignature() {
        Map<String, String> ascending = new LinkedHashMap<>();
        ascending.put("payNo", "PAY001");
        ascending.put("result", "SUCCESS");
        ascending.put("timestamp", "1700000000");

        // 故意反着放，模拟不同语言 / 不同框架序列化顺序不一致
        Map<String, String> descending = new LinkedHashMap<>();
        descending.put("timestamp", "1700000000");
        descending.put("result", "SUCCESS");
        descending.put("payNo", "PAY001");

        assertEquals(SignatureUtil.sign(ascending, SECRET), SignatureUtil.sign(descending, SECRET),
                "如果这里不相等，说明拼接前没有排序，支付平台和我方会算出不同签名");
    }

    @Test
    @DisplayName("攻击 A 伪造：没有密钥就算不出合法签名")
    void forgedCallbackIsRejected() {
        long now = System.currentTimeMillis() / 1000;
        Map<String, String> params = callbackParams("PAY001", "SUCCESS", now);

        // 攻击者按文档自己造了一条回调，用猜的密钥签名
        String forgedSign = SignatureUtil.sign(params, WRONG_SECRET);

        assertFalse(SignatureUtil.verify(params, forgedSign, SECRET),
                "用错误密钥签出来的东西必须验不过");
    }

    @Test
    @DisplayName("攻击 C 篡改：改任何一个字段签名都对不上")
    void tamperedCallbackIsRejected() {
        long now = System.currentTimeMillis() / 1000;
        Map<String, String> original = callbackParams("PAY001", "SUCCESS", now);
        String sign = SignatureUtil.sign(original, SECRET);

        // 攻击者把支付单号改成别人的订单
        Map<String, String> tampered = callbackParams("PAY999", "SUCCESS", now);

        assertFalse(SignatureUtil.verify(tampered, sign, SECRET),
                "改了 payNo 却还是原签名，必须被拦住");
    }

    @Test
    @DisplayName("拼接歧义：payNo=A/result=BC 与 payNo=AB/result=C 签名必须不同")
    void concatenationAmbiguityIsEliminated() {
        Map<String, String> first = callbackParams("A", "BC", 1700000000L);
        Map<String, String> second = callbackParams("AB", "C", 1700000000L);

        // 如果拼接方式是 payNo + result 直接相加，这两个会得到同样的串 "ABC1700000000"
        assertNotEquals(SignatureUtil.sign(first, SECRET), SignatureUtil.sign(second, SECRET),
                "签名相同意味着攻击者可以在这两组参数之间自由改写而不被察觉");
    }

    @Test
    @DisplayName("攻击 B 重放：签名有效但时间戳太旧，判定为过期")
    void replayedCallbackIsRejectedByTimestamp() {
        long tenMinutesAgo = System.currentTimeMillis() / 1000 - 600;
        Map<String, String> replayed = callbackParams("PAY001", "SUCCESS", tenMinutesAgo);
        String sign = SignatureUtil.sign(replayed, SECRET);

        // 签名本身是对的 —— 因为它就是当初那条真回调
        assertTrue(SignatureUtil.verify(replayed, sign, SECRET),
                "重放攻击的特点是签名一定验得过，所以光验签拦不住它");
        // 拦住它的是时效检查
        assertFalse(SignatureUtil.isTimestampFresh(tenMinutesAgo, TOLERANCE_SECONDS),
                "超过 5 分钟的回调必须判为重放");
    }

    @Test
    @DisplayName("正常回调：验签通过且时间戳新鲜")
    void normalCallbackPasses() {
        long now = System.currentTimeMillis() / 1000;
        Map<String, String> params = callbackParams("PAY001", "SUCCESS", now);
        String sign = SignatureUtil.sign(params, SECRET);

        assertTrue(SignatureUtil.verify(params, sign, SECRET));
        assertTrue(SignatureUtil.isTimestampFresh(now, TOLERANCE_SECONDS));
    }

    @Test
    @DisplayName("空签名、null 签名直接拒绝，不能抛异常")
    void blankSignatureIsRejected() {
        Map<String, String> params = callbackParams("PAY001", "SUCCESS", 1700000000L);
        assertFalse(SignatureUtil.verify(params, null, SECRET));
        assertFalse(SignatureUtil.verify(params, "", SECRET));
        assertFalse(SignatureUtil.verify(params, "   ", SECRET));
    }
}
