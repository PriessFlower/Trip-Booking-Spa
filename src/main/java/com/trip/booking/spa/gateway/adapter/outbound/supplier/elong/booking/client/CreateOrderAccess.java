package com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.booking.client;

import com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared.AbstractElongRestAccess;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared.ElongProperties;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared.model.response.ElongOrderCreateResponse;
import com.trip.booking.spa.gateway.domain.supplier.SupplierDataTypeEnum;
import com.trip.booking.spa.platform.exception.ParseException;
import com.trip.booking.spa.platform.observability.MonitorNameEnum;
import com.trip.booking.spa.platform.util.JsonUtils;

/**
 * hotel.order.create（创建订单）通道。限流键 {@code GLOBAL_LIMIT:ELONG:SPA_SUPPLIER_API_CREATE_ORDER}。
 * 写操作重试恒 0（基类钉死）——重发由 AffiliateConfirmationId 幂等兜底，但那是上游的决定，
 * 不是通道层的。
 */
public class CreateOrderAccess extends AbstractElongRestAccess<ElongOrderCreateResponse> {

    public CreateOrderAccess(ElongProperties properties) {
        super(SupplierDataTypeEnum.CREATE_ORDER, MonitorNameEnum.SPA_SUPPLIER_API_CREATE_ORDER, properties);
    }

    @Override
    protected ElongOrderCreateResponse parseResponse(String data) {
        try {
            return JsonUtils.readValue(data, ElongOrderCreateResponse.class);
        } catch (Exception e) {
            throw new ParseException(e);
        }
    }
}
