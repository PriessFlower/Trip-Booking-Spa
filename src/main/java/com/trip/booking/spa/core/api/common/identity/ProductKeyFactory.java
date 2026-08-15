package com.trip.booking.spa.core.api.common.identity;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 派生稳定产品身份 productKey（docs/product-identity.md R-1.1）。
 *
 * <p>productKey 标识的是"卖法"（等价类），不是某条报价：一个 key 对应 0..N 条
 * 供应商在售报价都是正常态。键只由长期不变的事实拼成——供应商报价码、价格、床型、
 * 自由文本一律不进键（R-1.2）；身份只许派生、不许发号（R-1.5，cursor 嵌合体教训：
 * 自增序列回拨 → 13,090 家酒店身份错乱）。
 *
 * <p>产物为 sha256 hex（64 字符），恰好适配目录表现有 {@code product_id VARCHAR(64)}。
 * {@code pk1} 版本前缀参与哈希：若未来键成分变更，bump 版本即整体换代，不会新旧混淆。
 */
public final class ProductKeyFactory {

    private static final String VERSION = "pk1";

    private ProductKeyFactory() {
    }

    /**
     * @param supplierCode 供应商编码（SupplierSourceEnum.getCode()）
     * @param account      账号/渠道 profile 标识。必须进键：cursor 汇智双账号共享产品 ID
     *                     空间、69% 价格不同的教训（R-1.3）
     * @param supplierHotelId 供应商酒店 id
     * @param supplierRoomId  供应商房型 id；无房型 ID 的供应商（如 ratehawk）传结构化
     *                        属性拼接的替身（R-4.3），由各家适配层负责
     * @param meal        餐食规范形；未知传 {@link MealSignature#unknown()}
     * @param cancel      退改粗分类；未知传 {@link CancelClass#UNKNOWN}
     * @param occupancy   占用规范串（如 {@code 2} 或 {@code 2-9,4}）
     */
    public static String derive(int supplierCode, String account, String supplierHotelId,
                                String supplierRoomId, MealSignature meal, CancelClass cancel,
                                String occupancy) {
        String canonical = VERSION
                + "|s:" + supplierCode
                + "|a:" + component(account, "account")
                + "|h:" + component(supplierHotelId, "supplierHotelId")
                + "|r:" + component(supplierRoomId, "supplierRoomId")
                + "|m:" + require(meal, "meal").canonical()
                + "|c:" + require(cancel, "cancel")
                + "|o:" + component(occupancy, "occupancy");
        return sha256Hex(canonical);
    }

    /** 身份成分不许缺席，也不许含分隔符——缺了就是上游代码的 bug，宁可炸在派生处 */
    private static String component(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("productKey 成分缺失: " + name);
        }
        if (value.indexOf('|') >= 0) {
            throw new IllegalArgumentException("productKey 成分含分隔符 '|': " + name + "=" + value);
        }
        return value.trim();
    }

    private static <T> T require(T value, String name) {
        if (value == null) {
            throw new IllegalArgumentException("productKey 成分缺失: " + name);
        }
        return value;
    }

    private static String sha256Hex(String canonical) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JVM 缺少 SHA-256", e);
        }
    }
}
