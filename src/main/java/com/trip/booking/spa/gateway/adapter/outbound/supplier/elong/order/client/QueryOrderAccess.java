package com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.order.client;

import com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared.AbstractElongRestAccess;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared.ElongProperties;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared.model.response.ElongOrderDetailResponse;
import com.trip.booking.spa.gateway.domain.supplier.SupplierDataTypeEnum;
import com.trip.booking.spa.platform.exception.ParseException;
import com.trip.booking.spa.platform.observability.MonitorNameEnum;
import com.trip.booking.spa.platform.util.JsonUtils;

/**
 * hotel.order.detail（查单）通道。限流键 {@code GLOBAL_LIMIT:ELONG:SPA_SUPPLIER_API_QUERY_ORDER}。
 */
public class QueryOrderAccess extends AbstractElongRestAccess<ElongOrderDetailResponse> {

    public QueryOrderAccess(ElongProperties properties) {
        super(SupplierDataTypeEnum.QUERY_ORDER, MonitorNameEnum.SPA_SUPPLIER_API_QUERY_ORDER, properties);
    }

    @Override
    protected ElongOrderDetailResponse parseResponse(String data) {
        try {
            return JsonUtils.readValue(data, ElongOrderDetailResponse.class);
        } catch (Exception e) {
            throw new ParseException(e);
        }
    }
}
