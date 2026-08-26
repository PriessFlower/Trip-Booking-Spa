package com.trip.booking.spa.gateway.application.pricing;

import com.trip.booking.spa.gateway.adapter.inbound.rest.request.PriceReq;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.Supplier;
import com.trip.booking.spa.gateway.domain.supplier.SupplierSourceEnum;
import com.trip.booking.spa.platform.observability.CallStatus;
import com.trip.booking.spa.platform.observability.MetricNames;
import com.trip.booking.spa.platform.observability.MetricTags;
import com.trip.booking.spa.platform.observability.Monitor;
import com.trip.booking.spa.platform.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;

/**
 * 查价模板。
 *
 * <p>{@code pricing_supplier_query} 在此统一打（O-4.3：新接一家自动具备）：一次实时查价
 * 一笔，按分态映射终态。此前它埋在 Expedia 实现里共五处，艺龙零处——埋在各家手里的指标
 * 天然漂移，「每家被查了多少、成了多少」的盘上艺龙恒为 0，与「没流量」无从区分。
 * 实时腿上各家实现<b>不得</b>再自行打这个名字，否则重复计数（O-3.3：一次调用只记一次）；
 * 现存唯一例外是 Expedia 刷价出口的两笔留守，缘由见 {@code queryPricesCache} 内注释。
 */
@Slf4j
public abstract class AbstractProductSyncSupportService implements ProductSyncService {

    @Override
    public PricingResult queryPrice(PriceReq priceReq, Supplier supplier) {
        PricingResult result = safeQuery(priceReq, supplier);
        recordSupplierQuery(supplier, result);
        return result;
    }

    private PricingResult safeQuery(PriceReq priceReq, Supplier supplier) {
        try {
            PricingResult result = querySupplierPrice(priceReq, supplier);
            if (result == null) {
                // 实现方绕过分态直接返回空：不可表达为「无在售」，否则会把「我们不知道」
                // 说成「供应商说没有」
                log.error("查价：实现方返回空结果，按未能确认回报,priceReq={},supplier={}",
                        JsonUtils.writeObject2Json(priceReq), JsonUtils.writeObject2Json(supplier));
                return PricingResult.indeterminate();
            }
            return result;
        } catch (Exception e) {
            log.error("查价：过程异常，按未能确认回报,supplierId={},sHotelId={}",
                    supplier.getSupplierId(), supplier.getSHotelId(), e);
            return PricingResult.indeterminate();
        }
    }

    /**
     * 分态 → 调用终态：AVAILABLE=quoted、NO_INVENTORY=no_inventory、INDETERMINATE=error。
     * 要再细分 throttled/timeout 须先在通道层辨别成因（欠账，同 BaseHttpAccess）。
     * 刷价腿不经本模板，其三态由 {@code refresh_onsale/empty/failed} 覆盖，不在此重复。
     */
    private static void recordSupplierQuery(Supplier supplier, PricingResult result) {
        SupplierSourceEnum source = supplier == null || supplier.getSupplierId() == null
                ? null : SupplierSourceEnum.getEnum(supplier.getSupplierId());
        if (source == null) {
            return;
        }
        CallStatus status = switch (result.outcome()) {
            case AVAILABLE -> CallStatus.QUOTED;
            case NO_INVENTORY -> CallStatus.NO_INVENTORY;
            case INDETERMINATE -> CallStatus.ERROR;
        };
        Monitor.recordOne(MetricNames.PRICING_SUPPLIER_QUERY, MetricTags.of(source, status));
    }

    /**
     * 各家自行实现：调供应商、解析响应、判分态。
     *
     * <p>判据见 {@link com.trip.booking.spa.gateway.domain.booking.PricingOutcome}：
     * 只有供应商<b>明确</b>回答无可售产品时才允许 {@link PricingResult#noInventory()}；
     * 超时、限流、5xx、响应无法判读、凭据缺失一律 {@link PricingResult#indeterminate()}。
     */
    public abstract PricingResult querySupplierPrice(PriceReq priceReq, Supplier supplier);

}
