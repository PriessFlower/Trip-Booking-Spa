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
 * <p><b>价格键必须带供应商</b>（2026-08-26，同一条推理的第二次应用）：{@code hotelId} 是
 * <b>供应商侧</b>酒店 ID，Expedia 的 property_id 与艺龙的 hotelCode 都是数字串，撞上同一个值时
 * 两家的 field 会进同一个 Hash——productKey 含供应商成分所以 field 不覆盖，但<b>读的人分不出来</b>：
 * 出价把整个 Hash 端出来，还把外家的产品打上本次查询方的 supplierId 报出去。两家在产时靠
 * ID 空间不重叠侥幸无事，供应商扩到多家是必然串货。供应商成分取 code（与档案表
 * {@code supplier_id INT} 同一词汇；名字会漂移，编码持久）。换键即缓存冷启动，
 * 借 2026-08-26 无报价窗口一次到位，无双写过渡。
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
     * 当日价格 Hash：{@code price:{supplierCode}:{hotelId}:{occupancy}:{date}}，field = productKey。
     *
     * @param supplierCode {@link com.trip.booking.spa.gateway.domain.supplier.SupplierSourceEnum} 的 code
     * @param occupancy    {@link com.trip.booking.spa.gateway.domain.product.Occupancy#canonical} 的产物
     */
    public static String buildPriceKey(int supplierCode, String hotelId, String occupancy, String checkIn) {
        return JOINER.join(PRICE, supplierCode, hotelId, occupancy, checkIn);
    }

    /** 易腐票据：{@code quote:{supplierCode}:{hotelId}:{productKey}} */
    public static String buildQuoteKey(int supplierCode, String hotelId, String productKey) {
        return JOINER.join(QUOTE, supplierCode, hotelId, productKey);
    }


}
