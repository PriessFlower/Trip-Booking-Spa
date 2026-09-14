package com.trip.booking.spa.gateway.adapter.outbound.supplier.clwy.shared;

import java.util.List;

/**
 * 差旅无忧报价句柄里存放的凭据键名。验价（写入方）与将来下单（读取方）共同引用，
 * 不许两边各写字面量（architecture.md §5 第三步）。
 *
 * <p><b>下单认的是 {@link #RATE_KEY}</b>——官方 05-product-price：「RateKey 下单必填，
 * 且只有 10 分钟有效期，下单后 RateKey 将直接过期。下单前请务必重新拿取 RateKey」。
 * 故句柄 TTL 上限取 5 分钟（{@code SupplierIdentityProfile.CLWY}，R-2.2 取其一半）。
 *
 * <p>{@link #RATE_PLAN_ID} 是分代轮换的易腐报价码：只进 OfferStore，禁止落库（R-2.1）。
 * 留它是为了排障与下单侧对账，不是为了复用。
 */
public final class ClwyOfferCredentials {

    /** 试单签发的下单校验码，10 分钟有效、下单即失效 */
    public static final String RATE_KEY = "rateKey";

    public static final String HOTEL_ID = "hotelId";

    /** 房型号（稳定），下单不必传但留档供对账——它才是 productKey 的成分 */
    public static final String ROOM_TYPE_ID = "roomTypeId";

    /** 易腐报价码（分代轮换），留存仅供排障与下单侧对账 */
    public static final String RATE_PLAN_ID = "ratePlanId";

    public static final String CHECK_IN = "checkIn";

    public static final String CHECK_OUT = "checkOut";

    /** 试单间数。价格按「每天每间 × 间数」算，下单必须用同一间数回放 */
    public static final String ROOM_NUM = "roomNum";

    /** <b>每间房</b>成人数（官方口径，不是总数） */
    public static final String ADULT_COUNT = "adultCount";

    /** 每间房儿童年龄，逗号分隔；无儿童为空串 */
    public static final String CHILD_AGES = "childAges";

    /** 试单时点确认的总价（元，字符串），全间数口径（B4） */
    public static final String DECLARED_TOTAL = "totalPrice";

    /** 报价币种，与 {@link #DECLARED_TOTAL} 配对——离了币种，金额没有意义 */
    public static final String CURRENCY = "currency";

    /** 试单所报国籍。官方要求报价/验价/下单三处一致 */
    public static final String COUNTRY_CODE = "countryCode";

    /**
     * 特殊渠道标记。官方 05-product-price：「如果验价返回 Tag 有不是 null 和 0，需要传入」——
     * 故它是<b>验价签发、下单回传</b>的配对项，不是我们自己能推出来的值。
     */
    public static final String TAG = "tag";

    /** 下单前必须齐备的键（缺一即句柄内容不完整，确定性拒单）。TAG 与 CHILD_AGES 可为空串，不在内 */
    public static final List<String> REQUIRED_FOR_BOOKING = List.of(
            RATE_KEY, HOTEL_ID, CHECK_IN, CHECK_OUT, ROOM_NUM, ADULT_COUNT, DECLARED_TOTAL, CURRENCY);

    private ClwyOfferCredentials() {
    }
}
