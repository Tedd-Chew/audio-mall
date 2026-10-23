package com.example.audiobackend.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * 支付回调签名 / 验签。
 *
 * <p>解决的是「这条回调到底是不是支付平台发的」这个问题。回调地址是公开的，
 * 任何人都能 POST 过来，所以不能只校验参数长得对 —— 必须证明请求方持有密钥。
 *
 * <p>三个设计要点，缺一个就有对应的攻击面：
 * <ol>
 *   <li><b>用 HMAC 而不是裸哈希</b>：SHA256(参数) 攻击者自己也能算，等于没签名。
 *       HMAC 需要一个不出现在请求里的密钥，攻击者算不出来。</li>
 *   <li><b>参数按 key 字典序排序后拼 k=v</b>：直接字符串相加会有歧义，
 *       {@code payNo="A" + result="BC"} 和 {@code payNo="AB" + result="C"} 拼出来一模一样。</li>
 *   <li><b>timestamp 参与签名</b>：只把 timestamp 放进参数而不纳入签名，
 *       攻击者改掉它签名仍然有效，重放攻击就拦不住。</li>
 * </ol>
 */
public final class SignatureUtil {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private static final String PARAM_SEPARATOR = "&";//分隔块

    private static final String KV_SEPARATOR = "=";

    private SignatureUtil() {
    }

    /**
     * 计算签名。
     *
     * @param params 参与签名的参数，{@code sign} 字段本身不能传进来
     * @param secret 双方共享的密钥，只存在于服务端配置和支付平台，绝不能出现在请求里
     * @return 小写十六进制的 HMAC-SHA256
     */
    public static String sign(Map<String, String> params, String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("签名密钥不能为空");
        }
        String canonical = canonicalize(params);
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return toHex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            // JDK 一定自带 HmacSHA256，走到这里说明环境异常，属于程序错误不是业务错误
            throw new IllegalStateException("HMAC-SHA256 签名失败", e);
        }
    }

    /**
     * 验签。签名不对返回 false，调用方应当拒绝这条回调并记录告警。
     *
     * <p>用 {@link MessageDigest#isEqual} 而不是 {@code String.equals}：
     * equals 在第一个不同字符处就返回，响应时间会随匹配长度变化，
     * 理论上可以被用来逐字节猜签名（时序侧信道）。isEqual 是常量时间比较。
     */
    public static boolean verify(Map<String, String> params, String sign, String secret) {
        if (sign == null || sign.isBlank()) {
            return false;
        }
        String expected = sign(params, secret);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                sign.trim().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 判断时间戳是否在容忍窗口内 —— 防重放。
     *
     * <p>单独一个方法而不是塞进 {@link #verify}：验签和时效是两个独立判断，
     * 混在一起容易在改代码时丢掉其中一个。调用方必须两个都调。
     *
     * @param timestamp 支付平台发来的秒级时间戳
     * @param toleranceSeconds 允许的时间偏差（秒）
     */
    public static boolean isTimestampFresh(Long timestamp, long toleranceSeconds) {
        if (timestamp == null || timestamp <= 0) {
            return false;
        }
        long nowSeconds = System.currentTimeMillis() / 1000;
        return Math.abs(nowSeconds - timestamp) <= toleranceSeconds;
    }

    /**
     * 把参数拼成待签名的规范串：按 key 字典序排序，跳过空值，格式 {@code k1=v1&k2=v2}。
     *
     * <p>跳过空值是为了跟主流支付网关的规则对齐（空参数不参与签名），
     * 同时避免 {@code k=} 和 {@code k=null} 两种写法产生不同签名。
     */
    private static String canonicalize(Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            throw new IllegalArgumentException("待签名的参数不能为空");
        }
        // TreeMap 天然按 key 升序，不用自己排序
        TreeMap<String, String> sorted = new TreeMap<>(params);
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : sorted.entrySet()) {
            String value = entry.getValue();
            if (value == null || value.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(PARAM_SEPARATOR);
            }
            sb.append(entry.getKey()).append(KV_SEPARATOR).append(value);
        }
        return sb.toString();
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}
