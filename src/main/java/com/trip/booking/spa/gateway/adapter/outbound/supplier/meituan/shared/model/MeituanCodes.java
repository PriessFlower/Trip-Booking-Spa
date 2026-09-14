package com.trip.booking.spa.gateway.adapter.outbound.supplier.meituan.shared.model;

/**
 * 美团业务响应码（官方各接口的"业务错误码"表，2026-09-14 查阅）。
 *
 * <p><b>码按接口各自成义</b>：同一个数字在不同接口上意思不同（下单前校验的 2 是"酒店被拉黑"，
 * 预约下单的 2 是"价格校验失败"）。故本类按接口分段命名，禁止跨接口复用常量。
 */
public final class MeituanCodes {

    private MeituanCodes() {
    }

    /** 通用成功码 */
    public static final int SUCCESS = 0;

    /** 通用系统错误：参数缺失、内部异常等都落这个码，属"没问出结果"而非"没货" */
    public static final int SYSTEM_ERROR = 2000;

    /**
     * 频控：{@code 超过配额:境外酒店请求接口被限流}。官方文档没写这个码，是 2026-09-14 开闸
     * 首轮在生产撞出来的——按 2 QPS 刷价，277 次里 2 次。属"没问出结果"，绝不当没货去清缓存。
     */
    public static final int THROTTLED = 1200;

    // ── 下单前校验 hotel.oversea.order.check ──

    /** 校验失败 */
    public static final int CHECK_VALIDATION_FAILED = 1;
    /** 酒店被拉黑 */
    public static final int CHECK_HOTEL_BLACKLISTED = 2;
    /** 房态不满足预订。2026-09-14 实测：同一产品问 2 间即回此码，问 1 间 code=0 */
    public static final int CHECK_ROOM_STATUS_NOT_AVAILABLE = 3;
    /** 三方产品不可售 */
    public static final int CHECK_PRODUCT_NOT_SELLABLE = 4;
    /** 产品不存在 */
    public static final int CHECK_PRODUCT_NOT_EXIST = 5;
    /** 库存不足 */
    public static final int CHECK_STOCK_INSUFFICIENT = 6;
}
