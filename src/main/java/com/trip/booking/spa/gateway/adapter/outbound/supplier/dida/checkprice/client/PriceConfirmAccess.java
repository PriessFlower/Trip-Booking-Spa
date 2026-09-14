package com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.checkprice.client;

import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.AbstractDidaJsonAccess;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.DidaProperties;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.model.DidaPriceConfirmRequest;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.model.DidaPriceConfirmResponse;
import com.trip.booking.spa.gateway.domain.supplier.SupplierDataTypeEnum;
import com.trip.booking.spa.platform.exception.ParseException;
import com.trip.booking.spa.platform.observability.MonitorNameEnum;
import com.trip.booking.spa.platform.util.JsonUtils;

/**
 * PriceConfirm（验价）通道。限流键 {@code GLOBAL_LIMIT:DIDA:SPA_SUPPLIER_API_ORDER_PRICE}
 * （与另两家的验价接口同键位）。
 */
public class PriceConfirmAccess extends AbstractDidaJsonAccess<DidaPriceConfirmRequest, DidaPriceConfirmResponse> {

    private static final String PATH = "/api/rate/PriceConfirm?$format=json";

    public PriceConfirmAccess(DidaProperties properties) {
        super(SupplierDataTypeEnum.CHECK_PRICE, MonitorNameEnum.SPA_SUPPLIER_API_ORDER_PRICE, properties, PATH);
    }

    @Override
    protected String errorCode(DidaPriceConfirmResponse response) {
        return response == null ? null : response.errorCode();
    }

    @Override
    protected DidaPriceConfirmResponse parseResponse(String data) {
        try {
            return JsonUtils.readValue(data, DidaPriceConfirmResponse.class);
        } catch (Exception e) {
            throw new ParseException(e);
        }
    }
}
