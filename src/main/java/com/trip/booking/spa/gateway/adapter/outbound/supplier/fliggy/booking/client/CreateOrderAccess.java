package com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.booking.client;

import com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.shared.AbstractFliggyTopAccess;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.shared.FliggyProperties;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.shared.model.FliggyCreateResponse;
import com.trip.booking.spa.gateway.domain.supplier.SupplierDataTypeEnum;
import com.trip.booking.spa.platform.observability.MonitorNameEnum;

/**
 * {@code taobao.xhotel.order.international.distribution.create}（创单）通道。
 * 写操作重试恒 0（基类即 0）：重试烧配额且可能双单。
 * 限流键 {@code GLOBAL_LIMIT:FLIGGY:SPA_SUPPLIER_API_CREATE_ORDER}。
 */
public class CreateOrderAccess extends AbstractFliggyTopAccess<FliggyCreateResponse> {

    public CreateOrderAccess(FliggyProperties properties) {
        super(SupplierDataTypeEnum.CREATE_ORDER, MonitorNameEnum.SPA_SUPPLIER_API_CREATE_ORDER, properties);
    }

    @Override
    protected FliggyCreateResponse parseResponse(String data) {
        return FliggyCreateResponse.parse(data);
    }
}
