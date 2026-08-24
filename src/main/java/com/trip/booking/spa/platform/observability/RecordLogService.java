package com.trip.booking.spa.platform.observability;

/**
 * 按小时把供应商调用数 INCR 进 Redis。
 *
 * <p>2026-08-21 删去 daolv/aichotels/travelconnect/huitravel 四个方法：那四家未接入，
 * 方法全仓零调用者。
 *
 * <p>与 Micrometer 的 {@code supplier_io_access} 是两套并行的 QPS 口径（本类只有
 * Expedia 在写，艺龙没有），docs/observability.md §10 已记为待评估——下线本类前须先
 * 确认无人查 {@code record:expedia:qps:*} 这批键。
 */
public interface RecordLogService {

    void recordExpediaQps();
}
