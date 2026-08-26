package com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.shared;

import java.util.List;

/**
 * 飞猪报价句柄的凭据键名——验价（写入方）与下单（读取方）共同引用的唯一出处，
 * 两边各写字面量必然漂移（接入手册 §5 第三步）。
 *
 * <p>飞猪的票据是<b>一组配对</b>而非单值（Offer 凭据设计成 kv Map 的原因之一）：
 * {@code rate_key}（查价签发）+ {@code create_key}（验价签发）双钥缺一不可，
 * {@code request_trace_id} 是定价策略配对键。创单还要回传验价确认的总价与币种
 * （TOP create 必填 total_room_price，防价格漂移）。
 */
public final class FliggyOfferCredentials {

    /** 查价签发的商品票据（易腐，申报见 SupplierIdentityProfile.FLIGGY） */
    public static final String RATE_KEY = "rate_key";

    /** 验价签发的创单钥匙 */
    public static final String CREATE_KEY = "create_key";

    /** 定价策略配对键（availability 响应带出，validate 已回传，创单侧留档备查） */
    public static final String REQUEST_TRACE_ID = "request_trace_id";

    /** 验价确认的总房价（分），创单必填回传 */
    public static final String TOTAL_ROOM_PRICE_CENTS = "total_room_price_cents";

    /** 验价确认的币种（cursor 实证 USD，勿假设 CNY） */
    public static final String CURRENCY_CODE = "currency_code";

    /** 下单前必须齐备的键（缺一即句柄内容不完整，确定性拒单） */
    public static final List<String> REQUIRED_FOR_BOOKING = List.of(
            RATE_KEY, CREATE_KEY, TOTAL_ROOM_PRICE_CENTS, CURRENCY_CODE);

    private FliggyOfferCredentials() {
    }
}
