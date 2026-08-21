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

    /**
     * 验价时<b>申报给艺龙</b>的总价（元，字符串），下单侧原样复用。
     *
     * <p>叫「申报价」而不是「总价」：它既不是对客售价、也不一定是结算价，而是我方填进
     * hotel.data.validate / hotel.order.create 的 {@code TotalPrice} 的那个数。
     * <b>结算按它走</b>（对账单实证见 {@code ElongNightlyRate} 类 javadoc），故这个名字
     * 必须让人一眼看出「改它等于改我方应付金额」。
     *
     * <p>常量<b>值</b>保持 {@code "totalPrice"} 不变：它是 Redis 里 {@code offer:*} 句柄的
     * JSON 键，改值会让改动前签发、仍在 TTL 内的句柄在下单侧取不到凭据。
     */
    public static final String DECLARED_TOTAL = "totalPrice";

    /** 验价所用 DayPriceList 的 JSON，下单侧复用同一份每日价 */
    public static final String DAY_PRICE_LIST = "dayPriceList";

    /** 验价住期（yyyy-MM-dd）。下单侧以此为准并校验上游传参一致——住期不同则价必不同 */
    public static final String CHECK_IN = "checkIn";

    public static final String CHECK_OUT = "checkOut";

    /** 验价占用成人数。下单 NumberOfAdults（国际单必填）取此值，与验价口径同源 */
    public static final String ADULT_COUNT = "adultCount";

    private ElongOfferCredentials() {
    }
}
