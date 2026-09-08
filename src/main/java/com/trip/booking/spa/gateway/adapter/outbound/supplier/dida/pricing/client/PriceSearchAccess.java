package com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.pricing.client;

import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.AbstractDidaJsonAccess;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.DidaProperties;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.model.DidaPriceSearchRequest;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.model.DidaPriceSearchResponse;
import com.trip.booking.spa.gateway.domain.supplier.SupplierDataTypeEnum;
import com.trip.booking.spa.platform.exception.ParseException;
import com.trip.booking.spa.platform.observability.MonitorNameEnum;
import com.trip.booking.spa.platform.util.JsonUtils;

/**
 * pricesearch（查价）通道。限流键 {@code GLOBAL_LIMIT:DIDA:SPA_SUPPLIER_API_PRODUCT_PRICES}。
 * 路径大小写照官方文档原样（{@code pricesearch} 全小写，验价那个是 {@code PriceConfirm}）。
 */
public class PriceSearchAccess extends AbstractDidaJsonAccess<DidaPriceSearchRequest, DidaPriceSearchResponse> {

    private static final String PATH = "/api/rate/pricesearch?$format=json";

    public PriceSearchAccess(DidaProperties properties) {
        super(SupplierDataTypeEnum.PRODUCT_PRICE, MonitorNameEnum.SPA_SUPPLIER_API_PRODUCT_PRICES, properties, PATH);
    }

    @Override
    protected String errorCode(DidaPriceSearchResponse response) {
        return response == null ? null : response.errorCode();
    }

    @Override
    protected DidaPriceSearchResponse parseResponse(String data) {
        try {
            return JsonUtils.readValue(data, DidaPriceSearchResponse.class);
        } catch (Exception e) {
            throw new ParseException(e);
        }
    }
}
