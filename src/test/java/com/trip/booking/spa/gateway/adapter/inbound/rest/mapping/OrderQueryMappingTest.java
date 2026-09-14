package com.trip.booking.spa.gateway.adapter.inbound.rest.mapping;

import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.OrderRespDTO;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.OrderQueryReq;
import com.trip.booking.spa.gateway.domain.booking.OrderPresence;
import com.trip.booking.spa.gateway.domain.booking.OrderState;
import com.trip.booking.spa.gateway.domain.order.OrderQueryCommand;
import com.trip.booking.spa.gateway.domain.order.OrderQueryResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 钉住对外形状：这一层是 JSON 契约与领域模型之间唯一的翻译处。
 *
 * <p>重点在<b>那张订单状态数字码表</b>——它此前由艺龙与 Expedia 各写一遍（两套
 * {@code ORDER_STATUS_*} 常量、两个 {@code mapOrderStatus}），码表一改就得改两处，
 * 接第三家就是第三份。收进这里之后，这些断言是它唯一的守卫。
 */
class OrderQueryMappingTest {

    @Test
    void commandCarriesBothCoordinates() {
        OrderQueryCommand command = OrderQueryMapping.toCommand(OrderQueryReq.builder()
                .supplierId(10005).orderId("UPSTREAM-1").supplierOrderId("S-1").build());

        assertEquals(10005, command.supplierId());
        assertEquals("UPSTREAM-1", command.orderId());
        assertEquals("S-1", command.supplierOrderId());
    }

    /** 供应商单号缺失是查单最要紧的场景，翻译不得在这里把它变成异常或空串 */
    @Test
    void commandKeepsMissingSupplierOrderIdAsNull() {
        OrderQueryCommand command = OrderQueryMapping.toCommand(OrderQueryReq.builder()
                .supplierId(10005).orderId("UPSTREAM-1").build());

        assertNull(command.supplierOrderId());
    }

    /** 七个语义状态与对外码表逐一对齐——码表是对上游的契约，改一个值就是破坏性变更 */
    @Test
    void everyStateHasItsWireCode() {
        assertEquals(10, codeOf(OrderState.CREATED));
        assertEquals(20, codeOf(OrderState.BOOKING));
        assertEquals(21, codeOf(OrderState.BOOKED));
        assertEquals(22, codeOf(OrderState.BOOK_FAILED));
        assertEquals(30, codeOf(OrderState.CANCELING));
        assertEquals(31, codeOf(OrderState.CANCELED));
        assertEquals(32, codeOf(OrderState.CANCEL_FAILED));
    }

    /** 新增语义状态却忘了给码：这条会失败，逼作者回来补——不许静默落空 */
    @Test
    void noStateIsLeftWithoutACode() {
        for (OrderState state : OrderState.values()) {
            assertEquals(true, codeOf(state) != null,
                    state + " 没有对外码：新增状态必须同时在 OrderQueryMapping 登记");
        }
    }

    /** 识别不出供应商状态时留空，不取默认值——猜一个等于把未知说成已知 */
    @Test
    void unmappedStateLeavesTheCodeEmpty() {
        OrderRespDTO dto = OrderQueryMapping.toDto(OrderQueryResult.found()
                .supplierOrderStatus("SOME-NEW-STATUS").build());

        assertNull(dto.getOrderStatus());
        assertEquals("SOME-NEW-STATUS", dto.getSupplierOrderStatus());
    }

    @Test
    void foundResultIsMappedFieldForField() {
        OrderRespDTO dto = OrderQueryMapping.toDto(OrderQueryResult.found()
                .supplierOrderId("S-1")
                .supplierProductId("P-1")
                .totalPrice(12345)
                .settlePrice(11000)
                .createTime("2026-09-11 10:00:00")
                .state(OrderState.BOOKED)
                .supplierOrderStatus("booked")
                .confirmationNumber("C-1")
                .build());

        assertEquals(OrderPresence.FOUND, dto.getPresence());
        assertEquals("S-1", dto.getSupplierOrderId());
        assertEquals("P-1", dto.getSupplierProductId());
        assertEquals(12345, dto.getTotalPrice());
        assertEquals(11000, dto.getSettlePrice());
        assertEquals("2026-09-11 10:00:00", dto.getCreateTime());
        assertEquals(21, dto.getOrderStatus());
        assertEquals("booked", dto.getSupplierOrderStatus());
        assertEquals("C-1", dto.getConfirmationNumber());
    }

    @Test
    void notFoundAndIndeterminateCarryTheirMessage() {
        OrderRespDTO notFound = OrderQueryMapping.toDto(OrderQueryResult.notFound("确认无此单"));
        assertEquals(OrderPresence.NOT_FOUND, notFound.getPresence());
        assertEquals("确认无此单", notFound.getMessage());
        assertNull(notFound.getOrderStatus(), "没查到就没有状态可报");

        OrderRespDTO unknown = OrderQueryMapping.toDto(OrderQueryResult.indeterminate("超时"));
        assertEquals(OrderPresence.INDETERMINATE, unknown.getPresence());
        assertEquals("超时", unknown.getMessage());
    }

    private static Integer codeOf(OrderState state) {
        return OrderQueryMapping.toDto(OrderQueryResult.found().state(state).build()).getOrderStatus();
    }
}
