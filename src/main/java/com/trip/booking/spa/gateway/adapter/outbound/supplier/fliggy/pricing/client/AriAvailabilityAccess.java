package com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.pricing.client;

import com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.shared.AbstractFliggyTopAccess;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.shared.FliggyProperties;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.shared.model.FliggyAriResponse;
import com.trip.booking.spa.gateway.domain.supplier.SupplierDataTypeEnum;
import com.trip.booking.spa.platform.observability.MonitorNameEnum;

/**
 * {@code taobao.xhotel.distribution.ari.availability}（查价）通道。
 * 限流键 {@code GLOBAL_LIMIT:FLIGGY:SPA_SUPPLIER_API_PRODUCT_PRICES}。
 */
public class AriAvailabilityAccess extends AbstractFliggyTopAccess<FliggyAriResponse> {

    public AriAvailabilityAccess(FliggyProperties properties) {
        super(SupplierDataTypeEnum.PRODUCT_PRICE, MonitorNameEnum.SPA_SUPPLIER_API_PRODUCT_PRICES, properties);
    }

    @Override
    protected FliggyAriResponse parseResponse(String data) {
        return FliggyAriResponse.parse(data);
    }
}
