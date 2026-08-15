package com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.cancellation.client;

import com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared.AbstractElongRestAccess;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared.ElongProperties;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared.model.response.ElongOrderCancelResponse;
import com.trip.booking.spa.gateway.domain.supplier.SupplierDataTypeEnum;
import com.trip.booking.spa.platform.exception.ParseException;
import com.trip.booking.spa.platform.observability.MonitorNameEnum;
import com.trip.booking.spa.platform.util.JsonUtils;

/**
 * hotel.order.cancel（取消订单）通道。限流键 {@code GLOBAL_LIMIT:ELONG:SPA_SUPPLIER_API_CANCEL_ORDER}。
 * 写操作重试恒 0（基类钉死）。
 */
public class CancelOrderAccess extends AbstractElongRestAccess<ElongOrderCancelResponse> {

    public CancelOrderAccess(ElongProperties properties) {
        super(SupplierDataTypeEnum.CANCEL_ORDER, MonitorNameEnum.SPA_SUPPLIER_API_CANCEL_ORDER, properties);
    }

    @Override
    protected ElongOrderCancelResponse parseResponse(String data) {
        try {
            return JsonUtils.readValue(data, ElongOrderCancelResponse.class);
        } catch (Exception e) {
            throw new ParseException(e);
        }
    }
}
