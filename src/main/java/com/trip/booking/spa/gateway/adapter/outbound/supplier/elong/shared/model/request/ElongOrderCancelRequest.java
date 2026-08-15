package com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared.model.request;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * hotel.order.cancel 的 Request 节点。
 *
 * <p>PenaltyAmount 是罚金控制位（官方文档）：0=不校验罚金、有罚金也取消；
 * -1=有罚金即拒绝取消；&gt;0=校验与艺龙计算值一致（不一致报 H001139）。
 * 产品链路取 0——取消由上游发起，罚金已在上游与旅客确认，网关只管执行。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.UpperCamelCaseStrategy.class)
public class ElongOrderCancelRequest {

    private Long orderId;

    /** 取消类型，取自官方枚举文案（如"行程变更"） */
    private String cancelCode;

    private String reason;

    private BigDecimal penaltyAmount;
}
