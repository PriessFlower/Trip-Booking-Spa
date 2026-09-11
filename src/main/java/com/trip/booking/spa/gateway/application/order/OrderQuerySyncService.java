package com.trip.booking.spa.gateway.application.order;

import com.trip.booking.spa.gateway.domain.order.OrderQueryCommand;
import com.trip.booking.spa.gateway.domain.order.OrderQueryResult;

/**
 * 查单能力。入参出参是领域模型，不是对外 JSON——对外形状的翻译收在 ① 的 OrderQueryMapping。
 * 继取消之后第二个矫正依赖方向的能力面（此前五个能力接口全部直接吃 REST DTO，②③被①绑架）。
 */
public interface OrderQuerySyncService {

    OrderQueryResult orderQuery(OrderQueryCommand command);
}
