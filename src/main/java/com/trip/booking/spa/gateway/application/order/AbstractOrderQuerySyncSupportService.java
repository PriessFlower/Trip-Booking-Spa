package com.trip.booking.spa.gateway.application.order;

import com.trip.booking.spa.gateway.domain.order.OrderQueryCommand;
import com.trip.booking.spa.gateway.domain.order.OrderQueryResult;
import com.trip.booking.spa.platform.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;

/**
 * 查单模板：判定纪律在这里，不在各家实现里。
 *
 * <p>纪律只有一条——<b>没确证订单不在，就不许说不在</b>。无响应、解析不了、抛异常，
 * 一律回 INDETERMINATE 让上游重试查单；只有供应商明确回答"没有这张单"才是 NOT_FOUND。
 * 说反了的后果是上游按"订单不存在"去退款，而房还占着。
 *
 * <p>此前还有第四道兜底——"实现方漏填 presence 就按 INDETERMINATE 处理"。现在
 * {@link OrderQueryResult} 的三态由工厂钉死，漏填在构造上就不成立，那道运行期检查随之删除。
 */
@Slf4j
public abstract class AbstractOrderQuerySyncSupportService<T> implements OrderQuerySyncService {

    @Override
    public OrderQueryResult orderQuery(OrderQueryCommand command) {
        try {
            T raw = doOrderQuery(command);

            log.info("OrderQuerySyncService orderId={}, orderQueryResp:{}",
                    command.orderId(), JsonUtils.writeObject2Json(raw));

            if (raw == null) {
                log.error("OrderQuerySyncService doOrderQuery 无响应，回报 INDETERMINATE, orderId={}",
                        command.orderId());
                return OrderQueryResult.indeterminate("查单无响应，未能确证订单是否存在，请稍后重试查单");
            }

            OrderQueryResult result = orderQueryRespConvert(raw);

            if (result == null) {
                log.error("OrderQuerySyncService orderQueryRespConvert 返回空，回报 INDETERMINATE, orderId={}, 原始响应={}",
                        command.orderId(), JsonUtils.writeObject2Json(raw));
                return OrderQueryResult.indeterminate("查单响应无法解析，未能确证订单是否存在，请稍后重试查单");
            }
            return result;
        } catch (Exception e) {
            log.error("OrderQuerySyncService 异常，回报 INDETERMINATE, orderId={}", command.orderId(), e);
            return OrderQueryResult.indeterminate(
                    "查单过程异常，未能确证订单是否存在，请稍后重试查单：" + e.getClass().getSimpleName());
        }
    }

    public abstract T doOrderQuery(OrderQueryCommand command);

    public abstract OrderQueryResult orderQueryRespConvert(T raw);

}
