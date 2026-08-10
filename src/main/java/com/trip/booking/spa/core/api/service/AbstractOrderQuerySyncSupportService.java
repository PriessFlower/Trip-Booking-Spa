package com.trip.booking.spa.core.api.service;

import com.trip.booking.spa.core.api.common.enums.OrderPresence;
import com.trip.booking.spa.core.api.dto.OrderRespDTO;
import com.trip.booking.spa.core.api.request.OrderQueryReq;
import com.trip.booking.spa.core.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;

/**
 * 查单模板。
 *
 * <p><b>本类的核心职责是「绝不把查不到说成没有」</b>。原实现把一切异常与空响应统一吞成
 * null，控制层再转成一条错误响应——上游无从区分「确实没有这笔订单」与「这次没查出来」。
 * 而查单恰恰是下单回报 {@code UNKNOWN} 时的唯一确证手段：把「没查出来」当成「订单不存在」
 * 会导致重复下单，反之会让订单永久悬空，两者都是资损。
 *
 * <p>故本类的兜底一律回报 {@link OrderPresence#INDETERMINATE}。判
 * {@link OrderPresence#NOT_FOUND} 的权力只交给各供应商实现——只有它能读懂供应商
 * 「查无此单」的确切表达（如 Expedia 的空数组），而这与「调用失败」在 HTTP 层往往同形。
 *
 * @see AbstractBookingSyncSupportService 下单侧的同类纪律
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
