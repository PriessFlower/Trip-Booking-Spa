package com.trip.booking.spa.gateway.adapter.outbound.supplier.meituan.shared;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;

/**
 * 美团请求体的构造与签名：HMAC-SHA1 + Base64（官方"公共请求参数"，2026-09-14 查阅）。
 *
 * <p>本家没有会话也没有令牌，<b>每个请求现签一次</b>（腐性申报 {@code CredentialRenewal.STATELESS}）。
 * 签名口径：把除 {@code signature} 外的全部参数按<b>键字典序</b>拼成 {@code k=v&k=v…}，
 * 用 secretKey 做 HMAC-SHA1，再 Base64。
 *
 * <p><b>业务参数是被当成字符串塞进 {@code data} 的</b>，不是嵌套对象；因此参与签名的那份
 * JSON 必须与真正发出去的那份<b>逐字节相同</b>——{@link #build} 只序列化一次再两处复用，
 * 就是为了这个。分两次序列化在字段顺序或转义上稍有出入即签名不符，而服务端只回一句
 * 「签名错误」，从报文上看不出差在哪。
 */
public final class MeituanSigner {

    private static final String HMAC_SHA1 = "HmacSHA1";

    private static final SecureRandom RANDOM = new SecureRandom();

    private MeituanSigner() {
    }

    /**
     * 组装带签名的完整请求参数。
     *
     * @param method   接口名，如 {@code hotel.oversea.batch.goods.rp}
     * @param dataJson 业务参数的 JSON 串；无业务参数传 null（官方要求此时不参与签名）
     */
    public static Map<String, Object> build(String method, String dataJson, MeituanProperties properties) {
        // TreeMap 即字典序，签名与发送共用同一份，避免两处排序不一致
        Map<String, Object> params = new TreeMap<>();
        params.put("method", method);
        params.put("version", "2.0");
        params.put("timestamp", System.currentTimeMillis() / 1000);
        params.put("nonce", RANDOM.nextInt(10000));
        // partnerId 按官方参数表是整数：配置里存字符串，发送前转回数值，
        // 否则签名串里会多出引号而与服务端算的不一致
        params.put("partnerId", Integer.parseInt(properties.getPartnerId()));
        params.put("accesskey", properties.getAccessKey());
        if (dataJson != null) {
            params.put("data", dataJson);
        }
        params.put("signature", sign(params, properties.getSecretKey()));
        return params;
    }

    /** 按字典序拼 {@code k=v&k=v…} 后 HMAC-SHA1 再 Base64。入参不得含 signature */
    static String sign(Map<String, Object> params, String secretKey) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            if (sb.length() > 0) {
                sb.append('&');
            }
            sb.append(entry.getKey()).append('=').append(entry.getValue());
        }
        try {
            Mac mac = Mac.getInstance(HMAC_SHA1);
            mac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), HMAC_SHA1));
            return Base64.getEncoder().encodeToString(mac.doFinal(sb.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            // 签不出来就是配置坏了（密钥为空/算法缺失），不该被当成一次调用失败吞掉
            throw new IllegalStateException("美团签名计算失败", e);
        }
    }
}
