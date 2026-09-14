package com.trip.booking.spa.gateway.adapter.outbound.supplier.meituan.pricing.client;

import com.trip.booking.spa.gateway.adapter.outbound.supplier.meituan.shared.AbstractMeituanJsonAccess;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.meituan.shared.MeituanProperties;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.meituan.shared.model.MeituanBatchGoodsRequest;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.meituan.shared.model.MeituanBatchGoodsResponse;
import com.trip.booking.spa.gateway.domain.supplier.SupplierDataTypeEnum;
import com.trip.booking.spa.platform.exception.ParseException;
import com.trip.booking.spa.platform.observability.MonitorNameEnum;
import com.trip.booking.spa.platform.util.JsonUtils;

/** 批量查价通道（{@code method=hotel.oversea.batch.goods.rp}）。刷价与出报共用 */
public final class BatchGoodsAccess extends AbstractMeituanJsonAccess<MeituanBatchGoodsRequest, MeituanBatchGoodsResponse> {

    private static final String METHOD = "hotel.oversea.batch.goods.rp";

    public BatchGoodsAccess(MeituanProperties properties) {
        super(SupplierDataTypeEnum.PRODUCT_PRICE, MonitorNameEnum.SPA_SUPPLIER_API_PRODUCT_PRICES,
                properties, METHOD);
    }

    @Override
    protected String errorCode(MeituanBatchGoodsResponse response) {
        return response == null || response.getCode() == null ? null : String.valueOf(response.getCode());
    }

    @Override
    protected MeituanBatchGoodsResponse parseResponse(String data) {
        try {
            return JsonUtils.readValue(data, MeituanBatchGoodsResponse.class);
        } catch (Exception e) {
            throw new ParseException(e);
        }
    }
}
