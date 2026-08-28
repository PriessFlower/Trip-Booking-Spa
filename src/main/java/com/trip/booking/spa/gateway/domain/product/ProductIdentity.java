package com.trip.booking.spa.gateway.domain.product;

/**
 * 产品身份及其**全部成分**（R-1.1 / R-2.7）。
 *
 * <p><b>为什么要把成分带出来</b>：{@link ProductKeyFactory#derive} 产出的是 sha256，
 * <b>单向不可逆</b>。派生时成分是完整的（{@link MealSignature} 的早/午/晚三位、
 * {@link CancelClass} 的三态、占用串），但如果只把 hash 返回给调用方，成分就地作废——
 * 下游想入库就只能拿原始响应<b>重新判一遍</b>，而重新判必然降维（2026-08-20 复盘：
 * 建档把 {@code B1L1D1} 与 {@code B1L0D0} 一起压成 {@code breakfast=1}，占用干脆没落）。
 *
 * <p>更坏的是重判会与派生器<b>分叉</b>：改造前建档侧 {@code hasFreeCancelWindow} 只看
 * {@code cancelType==1}，而派生器 {@code classifyCancel} 还要求
 * {@link RefundType#NO_DEDUCTION}——两者结论一致纯属侥幸。现在 UNKNOWN 判决随成分
 * 带出（{@code mealSignature}/{@code cancelClass} 的 UNKNOWN 取值），建档读成分即可
 * 挡掉不进目录者（R-5.4），无须重判。
 *
 * <p>故纪律为 R-2.8：<b>成分只算一次，下游照抄，不得自行判定</b>。
 *
 * @param productKey      sha256 hex，64 字符
 * @param supplierCode    成分 s
 * @param account         成分 a：账号/渠道 profile
 * @param supplierHotelId 成分 h
 * @param supplierRoomId  成分 r
 * @param mealSignature   成分 m：{@link MealSignature#canonical()}，如 {@code B1L0D0}
 * @param cancelClass     成分 c：{@link CancelClass} 名，如 {@code FREE_CANCELLABLE}
 * @param occupancy       成分 o：占用规范串，如 {@code 2} 或 {@code 2-9,4}
 */
public record ProductIdentity(
        String productKey,
        int supplierCode,
        String account,
        String supplierHotelId,
        String supplierRoomId,
        String mealSignature,
        String cancelClass,
        String occupancy) {

    /**
     * 由成分派生身份。<b>唯一的构造入口</b>——不允许调用方自己 new，
     * 否则 productKey 与成分可能对不上，R-2.7 的「列能重算出 key」就失效了。
     */
    public static ProductIdentity of(int supplierCode, String account, String supplierHotelId,
                                     String supplierRoomId, MealSignature meal, CancelClass cancel,
                                     String occupancy) {
        String key = ProductKeyFactory.derive(supplierCode, account, supplierHotelId,
                supplierRoomId, meal, cancel, occupancy);
        return new ProductIdentity(key, supplierCode, account, supplierHotelId, supplierRoomId,
                meal.canonical(), cancel.name(), occupancy);
    }
}
