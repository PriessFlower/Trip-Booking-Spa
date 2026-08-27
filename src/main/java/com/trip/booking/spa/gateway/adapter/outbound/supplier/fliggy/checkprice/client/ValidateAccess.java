package com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.checkprice.client;

import com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.shared.AbstractFliggyTopAccess;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.shared.FliggyProperties;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.shared.model.FliggyValidateResponse;
import com.trip.booking.spa.gateway.domain.supplier.SupplierDataTypeEnum;
import com.trip.booking.spa.platform.observability.MonitorNameEnum;

/**
 * {@code taobao.xhotel.order.international.distribution.validate}（验价）通道。
 * 限流键 {@code GLOBAL_LIMIT:FLIGGY:SPA_SUPPLIER_API_ORDER_PRICE}。
 */
public class ValidateAccess extends AbstractFliggyTopAccess<FliggyValidateResponse> {

    public ValidateAccess(FliggyProperties properties) {
        super(SupplierDataTypeEnum.CHECK_PRICE, MonitorNameEnum.SPA_SUPPLIER_API_ORDER_PRICE, properties);
    }

    @Override
    protected FliggyValidateResponse parseResponse(String data) {
        return FliggyValidateResponse.parse(data);
    }
}
