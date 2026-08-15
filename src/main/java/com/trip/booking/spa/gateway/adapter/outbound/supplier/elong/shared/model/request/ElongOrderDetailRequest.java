package com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared.model.request;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * hotel.order.detail 的 Request 节点。
 *
 * <p>OrderId 优先；按我方单号（AffiliateConfirmationId）反查时 OrderId <b>必须显式传 0</b>
 * ——缺省会得到语义不清的 H001054（cursor 生产教训）。官方另提示：按我方单号反查涉及
 * 底层同步，订单生成初期可能有延迟——此窗口内的"查无单"由 AffiliateConfirmationId
 * 幂等兜底（误判 NOT_FOUND 后重下单不会重复建单，供应商返回原单号）。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.UpperCamelCaseStrategy.class)
public class ElongOrderDetailRequest {

    private Long orderId;

    private String affiliateConfirmationId;
}
