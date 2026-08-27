package com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.order.client;

import com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.shared.AbstractFliggyTopAccess;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.shared.FliggyProperties;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.shared.model.FliggyOrderDetailResponse;
import com.trip.booking.spa.gateway.domain.supplier.SupplierDataTypeEnum;
import com.trip.booking.spa.platform.observability.MonitorNameEnum;

/**
 * {@code taobao.xhotel.order.international.distribution.detail}（查单）通道。
 * 官方明示 {@code dis_order_id}（我方单号）足以定位——B5 成立，不要求飞猪单号。
 * 限流键 {@code GLOBAL_LIMIT:FLIGGY:SPA_SUPPLIER_API_QUERY_ORDER}。
 */
public class QueryOrderAccess extends AbstractFliggyTopAccess<FliggyOrderDetailResponse> {

    public QueryOrderAccess(FliggyProperties properties) {
        super(SupplierDataTypeEnum.QUERY_ORDER, MonitorNameEnum.SPA_SUPPLIER_API_QUERY_ORDER, properties);
    }

    @Override
    protected FliggyOrderDetailResponse parseResponse(String data) {
        return FliggyOrderDetailResponse.parse(data);
    }
}
