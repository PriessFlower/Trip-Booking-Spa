package com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.checkprice.client;

import com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared.AbstractElongRestAccess;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared.ElongProperties;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared.model.response.ElongDataValidateResponse;
import com.trip.booking.spa.gateway.domain.supplier.SupplierDataTypeEnum;
import com.trip.booking.spa.platform.exception.ParseException;
import com.trip.booking.spa.platform.observability.MonitorNameEnum;
import com.trip.booking.spa.platform.util.JsonUtils;

/**
 * hotel.data.validate（验价）通道。限流键 {@code GLOBAL_LIMIT:ELONG:SPA_SUPPLIER_API_ORDER_PRICE}。
 */
public class DataValidateAccess extends AbstractElongRestAccess<ElongDataValidateResponse> {

    public DataValidateAccess(ElongProperties properties) {
        super(SupplierDataTypeEnum.CHECK_PRICE, MonitorNameEnum.SPA_SUPPLIER_API_ORDER_PRICE, properties);
    }

    @Override
    protected ElongDataValidateResponse parseResponse(String data) {
        try {
            return JsonUtils.readValue(data, ElongDataValidateResponse.class);
        } catch (Exception e) {
            throw new ParseException(e);
        }
    }
}
