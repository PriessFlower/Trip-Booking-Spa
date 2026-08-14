package com.trip.booking.spa.core.api.request;

import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

@Getter
@Setter
@Builder
public class CancelReq {

    @NonNull
    private Integer supplierId;//供应商ID

    /**
     * 供应商订单号，<b>可选</b>。
     *
     * <p>与 {@code OrderQueryReq.supplierOrderId} 同理：最需要取消的场景恰恰是下单结果
     * 不确定的时候——请求发出但响应丢失，上游怀疑订单已成立、想撤掉它。而那种场景下上游
     * <b>恰恰没有</b>供应商订单号。若强制必填，最该用的时候反而调不通。
     *
     * <p>因此本服务以我方单号 {@link #orderId} 为取消的唯一坐标（Expedia 侧即
     * {@code affiliate_reference_id}）。供应商订单号仅在供应商不支持按我方单号反查时
     * 才需要，届时由该供应商实现自行要求。
     */
    private String supplierOrderId;

    @NonNull
    private String orderId;//自有订单Id

}
