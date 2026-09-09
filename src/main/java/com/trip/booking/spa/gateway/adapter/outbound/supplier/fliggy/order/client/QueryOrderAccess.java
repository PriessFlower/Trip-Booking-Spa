package com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.order.client;

import com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.shared.AbstractFliggyTopAccess;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.shared.FliggyProperties;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.shared.FliggyTopCall;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.shared.model.FliggyOrderDetailResponse;
import com.trip.booking.spa.gateway.domain.supplier.SupplierDataTypeEnum;
import com.trip.booking.spa.platform.observability.MonitorNameEnum;
import com.trip.booking.spa.platform.util.JsonUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@code taobao.xhotel.order.international.distribution.detail}（查单）通道。
 * 官方明示 {@code dis_order_id}（我方单号）足以定位——B5 成立，不要求飞猪单号。
 * 限流键 {@code GLOBAL_LIMIT:FLIGGY:SPA_SUPPLIER_API_QUERY_ORDER}。
 */
public class QueryOrderAccess extends AbstractFliggyTopAccess<FliggyOrderDetailResponse> {

    /** 查单方法名。取消也要查它拿罚金（官方：罚金以订单详情为准），故收在通道上共用 */
    public static final String METHOD = "taobao.xhotel.order.international.distribution.detail";

    /**
     * 查单调用。入参<b>必须包在 {@code order_base_req} 里</b>——官方 §5 标它为 Object，
     * cursor 生产报文同形；此前 SPA 平铺发送，是没验过的猜测。
     * 单号只给我方单号（B5，官方称两个单号给一即可）〔未实证：生产样本都带的是飞猪单号〕。
     */
    public static FliggyTopCall callByOrderId(String orderId, String distributor) {
        Map<String, Object> baseReq = new LinkedHashMap<>();
        baseReq.put("dis_order_id", orderId);
        baseReq.put("distributor", distributor);
        return new FliggyTopCall(METHOD, Map.of("order_base_req", JsonUtils.writeObject2Json(baseReq)));
    }

    public QueryOrderAccess(FliggyProperties properties) {
        super(SupplierDataTypeEnum.QUERY_ORDER, MonitorNameEnum.SPA_SUPPLIER_API_QUERY_ORDER, properties);
    }

    @Override
    protected FliggyOrderDetailResponse parseResponse(String data) {
        return FliggyOrderDetailResponse.parse(data);
    }
}
