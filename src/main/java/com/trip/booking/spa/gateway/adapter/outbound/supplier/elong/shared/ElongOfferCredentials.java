package com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared;

/**
 * 艺龙报价句柄里存放的凭据键名。验价（写入方）与将来下单（读取方）共同引用，
 * 不许两边各写字面量（architecture.md §5 第三步）。
 *
 * <p>艺龙下单要凭据<b>七项全齐</b>（littleMajiaId/goodsUniqId/hotelCode/supplierId/
 * subSupplierId/shopperProductId/roomTypeId，另加 ratePlanId 与 hotelId）——cursor
 * 侧任一缺失即 SUPPLIER_EXT_MISSING。其中马甲与报价码是会话级易腐凭证：只进
 * OfferStore（TTL 上限见 SupplierIdentityProfile.ELONG），禁止落库（R-2.1）。
 */
public final class ElongOfferCredentials {

    public static final String HOTEL_ID = "hotelId";

    public static final String HOTEL_CODE = "hotelCode";

    public static final String ROOM_TYPE_ID = "roomTypeId";

    public static final String RATE_PLAN_ID = "ratePlanId";

    public static final String GOODS_UNIQ_ID = "goodsUniqId";

    public static final String LITTLE_MAJIA_ID = "littleMajiaId";

    public static final String SUPPLIER_ID = "supplierId";

    public static final String SUB_SUPPLIER_ID = "subSupplierId";

    public static final String SHOPPER_PRODUCT_ID = "shopperProductId";

    /** 验价通过时的总价（元，字符串），下单侧核对口径 */
    public static final String TOTAL_PRICE = "totalPrice";

    /** 验价所用 DayPriceList 的 JSON，下单侧复用同一份每日价 */
    public static final String DAY_PRICE_LIST = "dayPriceList";

    private ElongOfferCredentials() {
    }
}
