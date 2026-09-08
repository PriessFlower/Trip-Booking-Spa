package com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared;

/**
 * 道旅报价句柄里存放的凭据键名。验价（写入方）与将来下单（读取方）共同引用，
 * 不许两边各写字面量（architecture.md §5 第三步）。
 *
 * <p>道旅下单只认一样东西：{@link #REFERENCE_NO}——官方 booking-api/booking-confirm
 * （2026-09-08 查阅）的 {@code ReferenceNo} 字段说明「要填写 Price Confirm 的 Response 里的
 * ReferenceNo」。其余键是下单侧的自校验材料（住期、间数、占用、申报总价必须与验价那次一致，
 * 否则报 3001/3005/3008）。
 *
 * <p><b>关于"有效期 2 小时"</b>：这个数只出现在错误码表 3006 的文案里（「订单参考号过期了。
 * 订单参考号有效时长为2小时」），price-confirm 与 booking-confirm 两页都<b>没写</b>有效期。
 * 且它说的是<b>这个号最长能活多久</b>，不是"这 2 小时内房和价被锁住"——道旅自己的 3015
 * 「无房或变价」正是拿着有效号去下单仍然失败的那一种。故本仓不依赖它：句柄 TTL 上限
 * 30 分钟（{@code SupplierIdentityProfile.DIDA}），实际存活以 OfferStore 的 TTL 为准。
 *
 * <p>{@link #RATE_PLAN_ID} 是会话级易腐报价码：只进 OfferStore，禁止落库（R-2.1）。
 */
public final class DidaOfferCredentials {

    /** PriceConfirm(PreBook=true) 回传的下单参考号，有效期 2 小时 */
    public static final String REFERENCE_NO = "referenceNo";

    public static final String HOTEL_ID = "hotelId";

    /** 易腐报价码，实测 3 秒即换代；留存仅供排障与下单侧对账 */
    public static final String RATE_PLAN_ID = "ratePlanId";

    /** 验价住期（yyyy-MM-dd）。下单侧以此为准并校验上游传参一致——住期不同则价必不同 */
    public static final String CHECK_IN = "checkIn";

    public static final String CHECK_OUT = "checkOut";

    /** 验价间数。道旅 PriceConfirm 的 TotalPrice 是全部间数的总价，下单必须用同一间数回放 */
    public static final String ROOM_NUM = "roomNum";

    public static final String ADULT_COUNT = "adultCount";

    /** 儿童年龄，逗号分隔；无儿童时为空串 */
    public static final String CHILD_AGES = "childAges";

    /** 验价时点由道旅确认的总价（元，字符串），下单侧原样复用 */
    public static final String DECLARED_TOTAL = "totalPrice";

    /** 报价币种，与 {@link #DECLARED_TOTAL} 配对——离了币种，金额没有意义 */
    public static final String CURRENCY = "currency";

    /** 验价所报国籍。下单必须一致，否则可能拿到另一套价（官方 pricesearch 注 14） */
    public static final String NATIONALITY = "nationality";

    private DidaOfferCredentials() {
    }
}
