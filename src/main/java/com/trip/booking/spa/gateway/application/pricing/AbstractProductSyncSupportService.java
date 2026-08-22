package com.trip.booking.spa.gateway.application.pricing;

import com.trip.booking.spa.gateway.adapter.inbound.rest.request.PriceReq;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.Supplier;
import com.trip.booking.spa.platform.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;

/**
 * 查价模板。
 */
@Slf4j
public abstract class AbstractProductSyncSupportService implements ProductSyncService {

    @Override
    public PricingResult queryPrice(PriceReq priceReq, Supplier supplier) {
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
     * 各家自行实现：调供应商、解析响应、判分态。
     *
     * <p>判据见 {@link com.trip.booking.spa.gateway.domain.booking.PricingOutcome}：
     * 只有供应商<b>明确</b>回答无可售产品时才允许 {@link PricingResult#noInventory()}；
     * 超时、限流、5xx、响应无法判读、凭据缺失一律 {@link PricingResult#indeterminate()}。
     */
    public abstract PricingResult querySupplierPrice(PriceReq priceReq, Supplier supplier);

}
