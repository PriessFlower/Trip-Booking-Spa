package com.trip.booking.spa.platform.util;

import com.google.common.base.Joiner;

/**
 * Redis 键的拼法。
 *
 * <p><b>价格键必须带占用</b>（2026-08-20）：productKey 的成分里有占用（R-1.1 的 {@code o:}），
 * 所以 1 人的卖法与 2 人的卖法本就是两个不同的 field，在同一个 Hash 里互不覆盖——结构上不冲突。
 * 问题出在<b>读的人分不出来</b>：出价是把整个 Hash 端出来、里面有什么报什么，没有任何一处按占用过滤。
 * 于是刷价按 1 人刷、客人 2 人来查，1 人的价被原样报了出去（实测同店同日 1 人 335.46 / 2 人 293.17，
 * 差 42 元）。把占用放进键之后，2 人查询取到的是空片，如实回报无货——宁可少卖，不可卖错（R-1.6）。
 *
 * <p><b>票据键叫 quote 不叫 product</b>：瘦身后它只承载易腐的东西（供应商报价码等），
 * 稳定属性归 MySQL 档案表（R-2.6）。叫 product 会让人以为那里有产品的完整信息。
 */
public class RedisKeyUtils {

    public static final Joiner JOINER = Joiner.on(":").skipNulls();


    public static final String PRICE = "price";

    /** 易腐票据键前缀。旧前缀 {@code product} 已弃用，见类注释 */
    public static final String QUOTE = "quote";



    /**
     * 当日价格 Hash：{@code price:{hotelId}:{occupancy}:{date}}，field = productKey。
     *
     * @param occupancy {@link com.trip.booking.spa.gateway.domain.product.Occupancy#canonical} 的产物
     */
    public static String buildPriceKey(String hotelId, String occupancy, String checkIn) {
        return JOINER.join(PRICE, hotelId, occupancy, checkIn);
    }

    /** 易腐票据：{@code quote:{hotelId}:{productKey}} */
    public static String buildQuoteKey(String hotelId, String productKey) {
        return JOINER.join(QUOTE, hotelId, productKey);
    }


}
