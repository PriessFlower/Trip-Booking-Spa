package com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.pricing.client;

import com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared.AbstractElongRestAccess;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared.ElongProperties;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared.model.response.ElongHotelDetailResponse;
import com.trip.booking.spa.gateway.domain.supplier.SupplierDataTypeEnum;
import com.trip.booking.spa.platform.exception.ParseException;
import com.trip.booking.spa.platform.observability.MonitorNameEnum;
import com.trip.booking.spa.platform.util.JsonUtils;

/**
 * hotel.detail（查价）通道。限流键 {@code GLOBAL_LIMIT:ELONG:SPA_SUPPLIER_API_PRODUCT_PRICES}。
 */
public class HotelDetailAccess extends AbstractElongRestAccess<ElongHotelDetailResponse> {

    public HotelDetailAccess(ElongProperties properties) {
        super(SupplierDataTypeEnum.PRODUCT_PRICE, MonitorNameEnum.SPA_SUPPLIER_API_PRODUCT_PRICES, properties);
    }

    @Override
    protected ElongHotelDetailResponse parseResponse(String data) {
        try {
            return JsonUtils.readValue(data, ElongHotelDetailResponse.class);
        } catch (Exception e) {
            throw new ParseException(e);
        }
    }
}
