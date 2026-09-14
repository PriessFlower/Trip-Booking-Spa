package com.trip.booking.spa.gateway.adapter.outbound.supplier.meituan.shared;

import java.util.List;

/**
 * 美团报价句柄里存放的凭据键名。验价（写入方）与将来下单（读取方）共同引用，
 * 不许两边各写字面量（architecture.md §5 第三步）。
 *
 * <p><b>本家验价不签发任何票据</b>：order.check 只回最新价与退改，没有 RateKey/ReferenceNo
 * 之类的一次性号，下单靠 {@link #GOODS_ID} 原样再报一次。故句柄里存的是"这一次验价确认过的
 * 那套参数"，下单必须原样回放——尤其是 {@link #ROOM_NUM} 与 {@link #CLIENT_NATIONALITY}：
 * 前者决定罚金口径与库存判定（实测问 2 间会回 code=3 房态不满足），后者决定这条产品对不对
 * 这位客人开放（实测 CN 317 条、US 只剩 37 条）。
 */
public final class MeituanOfferCredentials {

    public static final String HOTEL_ID = "hotelId";

    /** 产品 ID，下单认的就是它。按易腐申报，故只存在句柄里、不落库（R-2.1） */
    public static final String GOODS_ID = "goodsId";

    /** 物理房型号，下单不必传但留档供对账——它才是 productKey 的成分 */
    public static final String REAL_ROOM_ID = "realRoomId";

    public static final String CHECK_IN = "checkIn";

    public static final String CHECK_OUT = "checkOut";

    /** 验价间数。价格按「每间每晚 × 间数」算，罚金也按间数缩放，下单必须用同一间数回放 */
    public static final String ROOM_NUM = "roomNum";

    /** <b>每间房</b>成人数（官方口径，不是总数） */
    public static final String ADULT_COUNT = "adultCount";

    /** 每间房儿童数 */
    public static final String CHILD_COUNT = "childCount";

    /** 每间房儿童年龄，逗号分隔；无儿童为空串 */
    public static final String CHILD_AGES = "childAges";

    /** 验价时点确认的总价（元，字符串），全间数口径（B4） */
    public static final String DECLARED_TOTAL = "totalPrice";

    /** 报价币种，与 {@link #DECLARED_TOTAL} 配对——离了币种，金额没有意义 */
    public static final String CURRENCY = "currency";

    /** 验价所报国籍。它筛可售集合，报价/验价/下单三处必须同值 */
    public static final String CLIENT_NATIONALITY = "clientNationality";

    /** 下单前必须齐备的键（缺一即句柄内容不完整，确定性拒单）。CHILD_AGES 可为空串，不在内 */
    public static final List<String> REQUIRED_FOR_BOOKING = List.of(
            HOTEL_ID, GOODS_ID, CHECK_IN, CHECK_OUT, ROOM_NUM, ADULT_COUNT, DECLARED_TOTAL,
            CURRENCY, CLIENT_NATIONALITY);

    private MeituanOfferCredentials() {
    }
}
