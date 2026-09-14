package com.trip.booking.spa.gateway.adapter.outbound.supplier.clwy.pricing.client;

import com.trip.booking.spa.gateway.adapter.outbound.supplier.clwy.shared.AbstractClwyJsonAccess;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.clwy.shared.ClwyProperties;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.clwy.shared.ClwyTokenProvider;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.clwy.shared.model.ClwyPriceRequest;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.clwy.shared.model.ClwyPriceResponse;
import com.trip.booking.spa.gateway.domain.supplier.SupplierDataTypeEnum;
import com.trip.booking.spa.platform.exception.ParseException;
import com.trip.booking.spa.platform.observability.MonitorNameEnum;
import com.trip.booking.spa.platform.util.JsonUtils;

/**
 * 报价／试单通道（{@code POST /api/v2/HotelProduct/GetPrice}）。
 *
 * <p><b>同一个端点承两档</b>（官方 05-product-price）：不传 {@code RatePlanId} 是报价，
 * 传了就是「预定页试单」并回 {@code RateKey}。两档的额度口径不同——报价是后台刷价的大头，
 * 试单只在客人点订时发生——故<b>限流键按用途分</b>：报价走
 * {@code SPA_SUPPLIER_API_PRODUCT_PRICES}，试单走 {@code SPA_SUPPLIER_API_ORDER_PRICE}
 * （与另三家的验价接口同键位），由 {@link #forQuote} / {@link #forTrial} 两个工厂选定。
 */
public final class PriceAccess extends AbstractClwyJsonAccess<ClwyPriceRequest, ClwyPriceResponse> {

    private static final String PATH = "/api/v2/HotelProduct/GetPrice";

    private PriceAccess(SupplierDataTypeEnum dataType, MonitorNameEnum monitorKey,
                        ClwyProperties properties, ClwyTokenProvider tokenProvider) {
        super(dataType, monitorKey, properties, tokenProvider, PATH);
    }

    /** 报价档：不传 RatePlanId，拿整店现货 */
    public static PriceAccess forQuote(ClwyProperties properties, ClwyTokenProvider tokenProvider) {
        return new PriceAccess(SupplierDataTypeEnum.PRODUCT_PRICE,
                MonitorNameEnum.SPA_SUPPLIER_API_PRODUCT_PRICES, properties, tokenProvider);
    }

    /** 试单档：传 RatePlanId，拿 RateKey（10 分钟有效，下单后即过期） */
    public static PriceAccess forTrial(ClwyProperties properties, ClwyTokenProvider tokenProvider) {
        return new PriceAccess(SupplierDataTypeEnum.CHECK_PRICE,
                MonitorNameEnum.SPA_SUPPLIER_API_ORDER_PRICE, properties, tokenProvider);
    }

    @Override
    protected String errorCode(ClwyPriceResponse response) {
        return response == null || response.getCode() == null ? null : String.valueOf(response.getCode());
    }

    @Override
    protected ClwyPriceResponse parseResponse(String data) {
        try {
            return JsonUtils.readValue(data, ClwyPriceResponse.class);
        } catch (Exception e) {
            throw new ParseException(e);
        }
    }
}
