package com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.shared;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

/**
 * 淘宝 TOP MD5 签名：{@code md5(secret + k1v1k2v2... + secret)} 大写十六进制，
 * 键按字典序排、空值跳过。
 *
 * <p>算法与 cursor 生产实现（{@code FilggySignUtil.signTopRequest}，跑了数月）逐步同构，
 * 保证两边对同一参数集算出同一签名——TOP 平台侧只回「签名错误」不回差异细节，
 * 算法漂移只能靠对拍发现。只实现 md5：TOP 支持 hmac 等，但生产只用 md5，
 * 不给用不到的分支留活口。
 */
public final class FliggySignUtil {

    private FliggySignUtil() {
    }

    public static String sign(Map<String, String> params, String secret) {
        String[] keys = params.keySet().toArray(new String[0]);
        Arrays.sort(keys);
        StringBuilder query = new StringBuilder(secret);
        for (String key : keys) {
            String value = params.get(key);
            if (StringUtils.isNotEmpty(key) && StringUtils.isNotEmpty(value)) {
                query.append(key).append(value);
            }
        }
        query.append(secret);
        return byte2hex(md5(query.toString()));
    }

    private static byte[] md5(String data) {
        try {
            return MessageDigest.getInstance("MD5").digest(data.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JVM 无 MD5 实现", e);
        }
    }

    private static String byte2hex(byte[] bytes) {
        StringBuilder hex = new StringBuilder();
        for (byte b : bytes) {
            String h = Integer.toHexString(0xFF & b);
            if (h.length() == 1) {
                hex.append('0');
            }
            hex.append(h);
        }
        return hex.toString().toUpperCase(java.util.Locale.ROOT);
    }
}
