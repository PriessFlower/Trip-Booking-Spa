package com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 艺龙 REST 网关签名。
 *
 * <p>公式：{@code signature = md5( timestamp + md5(data + appKey) + secretKey )}，
 * 两层 MD5 结果均为 32 位小写十六进制，UTF-8 取字节。拼接顺序严格：内层 data 在前
 * appKey 在后；外层 timestamp + 内层摘要 + secretKey。
 *
 * <p><b>data 以 JSON 原文参与签名</b>（未 URLEncode）——URL 编码只发生在通道层拼
 * query 时（HttpUtils.buildUrl 对 value 编码一次），两处各司其职，顺序不能颠倒。
 * 依据：cursor 仓 ElongSignUtil 实现 + docs/elong/hotel.data.validate.json 抓包实证。
 */
public final class ElongSignUtil {

    private ElongSignUtil() {
    }

    /**
     * @param timestamp Unix 秒（字符串形态，与 query 参数里的 timestamp 必须同值）
     * @param data      业务参数 JSON 原文（信封 {Version,Local,Request}）
     */
    public static String sign(String timestamp, String data, String appKey, String secret) {
        return md5Hex(timestamp + md5Hex(data + appKey) + secret);
    }

    private static String md5Hex(String input) {
        try {
            byte[] digest = MessageDigest.getInstance("MD5").digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JVM 缺少 MD5", e);
        }
    }
}
