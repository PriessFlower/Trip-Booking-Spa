package com.trip.booking.spa.gateway.application.order;

import com.trip.booking.spa.gateway.domain.booking.OrderPresence;
import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.OrderRespDTO;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.OrderQueryReq;
import com.trip.booking.spa.platform.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;

/**
 * 查单模板。
 */
@Slf4j
public abstract class AbstractOrderQuerySyncSupportService<T> implements OrderQuerySyncService {

    @Override
    public OrderRespDTO orderQuery(OrderQueryReq orderQueryReq) {
        try {
            T t = doOrderQuery(orderQueryReq);

            log.info("OrderQuerySyncService orderQueryReq : {}, orderQueryResp:{}", JsonUtils.writeObject2Json(orderQueryReq),
                    JsonUtils.writeObject2Json(t));

            if (t == null) {
                log.error("OrderQuerySyncService doOrderQuery 无响应，回报 INDETERMINATE, orderId={}",
                        orderQueryReq.getOrderId());
                return indeterminate("查单无响应，未能确证订单是否存在，请稍后重试查单");
            }

            OrderRespDTO orderRespDTO = orderQueryRespConvert(t);

            if (orderRespDTO == null) {
                log.error("OrderQuerySyncService orderQueryRespConvert 返回空，回报 INDETERMINATE, orderId={}, 原始响应={}",
                        orderQueryReq.getOrderId(), JsonUtils.writeObject2Json(t));
                return indeterminate("查单响应无法解析，未能确证订单是否存在，请稍后重试查单");
            }
            if (orderRespDTO.getPresence() == null) {
                // 实现方漏填三态即视为不确定，避免默认值悄悄退化成「订单不存在」
                log.error("OrderQuerySyncService 实现未填 presence，按 INDETERMINATE 处理, orderId={}",
                        orderQueryReq.getOrderId());
                orderRespDTO.setPresence(OrderPresence.INDETERMINATE);
            }
            return orderRespDTO;
        } catch (Exception e) {
            log.error("OrderQuerySyncService 异常，回报 INDETERMINATE, orderId={}", orderQueryReq.getOrderId(), e);
            return indeterminate("查单过程异常，未能确证订单是否存在，请稍后重试查单：" + e.getClass().getSimpleName());
        }
    }

    private OrderRespDTO indeterminate(String message) {
        return OrderRespDTO.builder()
                .presence(OrderPresence.INDETERMINATE)
                .message(message)
                .build();
    }

    public abstract T doOrderQuery(OrderQueryReq orderQueryReq);

    public abstract OrderRespDTO orderQueryRespConvert(T t);

}
