package com.trip.booking.spa.gateway.adapter.outbound.supplier.clwy.pricing;

import com.trip.booking.spa.gateway.adapter.outbound.supplier.clwy.shared.model.ClwyRatePlan;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.clwy.shared.model.ClwyRoomType;

/**
 * 验价链路里的「一张票」：价格计划 + 它所在的房型。
 *
 * <p>为什么必须成对：本家的报价嵌在房型下（{@code roomTypeList[].ratePlanList[]}），而
 * <b>房型号是 productKey 的成分</b>、报价码不是。只传价格计划就丢了房型号，resolve 按 key
 * 找等价票时会全盘落空——键分叉即身份分叉。艺龙的 {@code PlanWithRoom} 是同一个理由。
 */
public record ClwyPlan(ClwyRoomType roomType, ClwyRatePlan plan) {

    public String roomTypeId() {
        return roomType == null ? null : roomType.getRoomTypeId();
    }

    public String ratePlanId() {
        return plan == null ? null : plan.getRatePlanId();
    }
}
