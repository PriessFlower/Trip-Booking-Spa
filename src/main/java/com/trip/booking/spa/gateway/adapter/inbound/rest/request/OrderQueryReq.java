package com.trip.booking.spa.gateway.adapter.inbound.rest.request;

import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

@Getter
@Setter
@Builder
public class OrderQueryReq {

    @NonNull
    private Integer supplierId;//供应商ID

    /**
     * 供应商订单号，<b>可选</b>。
     *
     * <p>刻意不设为必填：查单最要紧的用途是下单回报 {@code UNKNOWN} 后的确证，
     * 而那种场景下上游<b>恰恰没有</b>供应商订单号——正因为拿不到响应才需要查单。
     * 若强制必填，唯一真正需要查单的场景就调不通，三态契约等于空头承诺。
     *
     * <p>因此本服务以我方单号 {@link #orderId} 为查单的唯一坐标
     * （Expedia 侧即 {@code affiliate_reference_id}）。供应商订单号仅在供应商不支持
     * 按我方单号反查时才需要，届时由该供应商实现自行要求。
     */
    private String supplierOrderId;

    @NonNull
    private String orderId;//自有订单Id

}
