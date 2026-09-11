package com.trip.booking.spa.gateway.adapter.inbound.rest.mapping;

import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.OrderRespDTO;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.OrderQueryReq;
import com.trip.booking.spa.gateway.domain.booking.OrderState;
import com.trip.booking.spa.gateway.domain.order.OrderQueryCommand;
import com.trip.booking.spa.gateway.domain.order.OrderQueryResult;

import java.util.Map;

/**
 * 查单能力的对外形状翻译：JSON 契约 ↔ 领域模型，只此一处。
 *
 * <p>①层持有翻译，②③不知道 JSON 长什么样——继取消之后第二个矫正依赖方向的能力面。
 * 对外那张订单状态数字码表也收在这里：此前艺龙与 Expedia 各自定义一套
 * {@code ORDER_STATUS_*} 常量、各写一个 {@code mapOrderStatus}，同一张码表两种笔迹，
 * 飞猪则第三种写法（直接给 public 字段赋值）。接第四家就是第四份。
 */
public final class OrderQueryMapping {

    /**
     * 对外订单状态码。取值含义见 {@link OrderRespDTO#orderStatus}——那里是给上游读的契约说明，
     * 这里是唯一一处产出它的地方。
     */
    private static final Map<OrderState, Integer> STATUS_CODES = Map.of(
            OrderState.CREATED, 10,
            OrderState.BOOKING, 20,
            OrderState.BOOKED, 21,
            OrderState.BOOK_FAILED, 22,
            OrderState.CANCELING, 30,
            OrderState.CANCELED, 31,
            OrderState.CANCEL_FAILED, 32);

    private OrderQueryMapping() {
    }

    public static OrderQueryCommand toCommand(OrderQueryReq req) {
        return OrderQueryCommand.of(req.getSupplierId(), req.getOrderId(), req.getSupplierOrderId());
    }

    public static OrderRespDTO toDto(OrderQueryResult result) {
        return OrderRespDTO.builder()
                .presence(result.presence())
                .message(result.message())
                .supplierOrderId(result.supplierOrderId())
                .supplierProductId(result.supplierProductId())
                .totalPrice(result.totalPrice())
                .settlePrice(result.settlePrice())
                .createTime(result.createTime())
                .orderStatus(statusCodeOf(result.state()))
                .supplierOrderStatus(result.supplierOrderStatus())
                .confirmationNumber(result.confirmationNumber())
                .build();
    }

    /** state 为 null（供应商状态识别不出）时留空，不取默认值——见 {@link OrderState} 类注释 */
    private static Integer statusCodeOf(OrderState state) {
        return state == null ? null : STATUS_CODES.get(state);
    }
}
