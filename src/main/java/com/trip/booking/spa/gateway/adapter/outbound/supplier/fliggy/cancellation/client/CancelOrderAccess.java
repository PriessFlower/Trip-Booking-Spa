package com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.cancellation.client;

import com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.shared.AbstractFliggyTopAccess;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.shared.FliggyProperties;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.shared.model.FliggyCancelResponse;
import com.trip.booking.spa.gateway.domain.supplier.SupplierDataTypeEnum;
import com.trip.booking.spa.platform.observability.MonitorNameEnum;

/**
 * {@code taobao.xhotel.trade.international.distribution.cancel}（取消）通道。
 * 写操作重试恒 0。限流键 {@code GLOBAL_LIMIT:FLIGGY:SPA_SUPPLIER_API_CANCEL_ORDER}。
 */
public class CancelOrderAccess extends AbstractFliggyTopAccess<FliggyCancelResponse> {

    public CancelOrderAccess(FliggyProperties properties) {
        super(SupplierDataTypeEnum.CANCEL_ORDER, MonitorNameEnum.SPA_SUPPLIER_API_CANCEL_ORDER, properties);
    }

    @Override
    protected FliggyCancelResponse parseResponse(String data) {
        return FliggyCancelResponse.parse(data);
    }
}
