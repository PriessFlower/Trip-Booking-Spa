package com.trip.booking.spa.gateway.adapter.outbound.supplier.meituan.checkprice.client;

import com.trip.booking.spa.gateway.adapter.outbound.supplier.meituan.shared.AbstractMeituanJsonAccess;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.meituan.shared.MeituanProperties;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.meituan.shared.model.MeituanOrderCheckRequest;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.meituan.shared.model.MeituanOrderCheckResponse;
import com.trip.booking.spa.gateway.domain.supplier.SupplierDataTypeEnum;
import com.trip.booking.spa.platform.exception.ParseException;
import com.trip.booking.spa.platform.observability.MonitorNameEnum;
import com.trip.booking.spa.platform.util.JsonUtils;

/**
 * 下单前校验通道（{@code method=hotel.oversea.order.check}，官方限流 1000 次/分钟）。
 *
 * <p><b>不建单不占房</b>：接口只做可订与库存校验并回最新价，故用作验价是安全的。
 * 限流键与另几家的验价接口同键位（{@code SPA_SUPPLIER_API_ORDER_PRICE}）。
 */
public final class OrderCheckAccess extends AbstractMeituanJsonAccess<MeituanOrderCheckRequest, MeituanOrderCheckResponse> {

    private static final String METHOD = "hotel.oversea.order.check";

    public OrderCheckAccess(MeituanProperties properties) {
        super(SupplierDataTypeEnum.CHECK_PRICE, MonitorNameEnum.SPA_SUPPLIER_API_ORDER_PRICE,
                properties, METHOD);
    }

    @Override
    protected String errorCode(MeituanOrderCheckResponse response) {
        return response == null || response.getCode() == null ? null : String.valueOf(response.getCode());
    }

    @Override
    protected MeituanOrderCheckResponse parseResponse(String data) {
        try {
            return JsonUtils.readValue(data, MeituanOrderCheckResponse.class);
        } catch (Exception e) {
            throw new ParseException(e);
        }
    }
}
