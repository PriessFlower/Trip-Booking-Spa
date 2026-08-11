package com.trip.booking.spa.core.api.service;

import com.trip.booking.spa.core.api.common.enums.OrderPresence;
import com.trip.booking.spa.core.api.dto.OrderRespDTO;
import com.trip.booking.spa.core.api.request.OrderQueryReq;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 钉死查单模板的兜底语义。
 *
 * <p>这些用例守的是一条资损防线：<b>查不到绝不能表达为「订单不存在」</b>。查单是下单回报
 * UNKNOWN 后的唯一确证手段，上游只在 NOT_FOUND 时才被允许重新下单。若后续有人把兜底改回
 * 返回 null 或 NOT_FOUND，这里必须失败。
 */
class AbstractOrderQuerySyncSupportServiceTest {

    private static OrderQueryReq req() {
        return OrderQueryReq.builder()
                .supplierId(10005)
                .orderId("UPSTREAM-ORDER-1")
                .build();
    }

    /** 供应商订单号缺失是常态：下单超时时上游本来就没有它，查单必须照样能调 */
    @Test
    void supplierOrderIdIsOptional() {
        OrderQueryReq built = req();

        assertNotNull(built, "supplierOrderId 若为必填，唯一真正需要查单的场景就调不通");
        assertEquals("UPSTREAM-ORDER-1", built.getOrderId());
    }

    /** 查单无响应不等于订单不存在，必须回报 INDETERMINATE */
    @Test
    void nullSupplierResponseIsReportedAsIndeterminate() {
        OrderRespDTO resp = new StubQueryService(Behaviour.RETURN_NULL).orderQuery(req());

        assertNotNull(resp, "兜底禁止返回 null：控制层会将其表达为接口错误，上游无从判断能否重下");
        assertEquals(OrderPresence.INDETERMINATE, resp.getPresence());
    }

    /** 查单抛异常同样不足以断定订单不存在 */
    @Test
    void exceptionIsReportedAsIndeterminateNotNotFound() {
        OrderRespDTO resp = new StubQueryService(Behaviour.THROW).orderQuery(req());

        assertNotNull(resp);
        assertEquals(OrderPresence.INDETERMINATE, resp.getPresence(),
                "异常不足以断定订单不存在，判 NOT_FOUND 会导致重复下单");
    }

    /** 转换器返回空属不可判，不得退化为「订单不存在」 */
    @Test
    void unconvertibleResponseIsReportedAsIndeterminate() {
        OrderRespDTO resp = new StubQueryService(Behaviour.CONVERT_TO_NULL).orderQuery(req());

        assertNotNull(resp);
        assertEquals(OrderPresence.INDETERMINATE, resp.getPresence());
    }

    /** 实现漏填三态时按 INDETERMINATE 兜底，避免默认值悄悄退化成「订单不存在」 */
    @Test
    void missingPresenceDefaultsToIndeterminate() {
        OrderRespDTO resp = new StubQueryService(Behaviour.OMIT_PRESENCE).orderQuery(req());

        assertEquals(OrderPresence.INDETERMINATE, resp.getPresence());
    }

    /** 供应商明确回答「没有这笔订单」时，实现有权判 NOT_FOUND，模板不得篡改 */
    @Test
    void notFoundIsPreserved() {
        OrderRespDTO resp = new StubQueryService(Behaviour.NOT_FOUND).orderQuery(req());

        assertEquals(OrderPresence.NOT_FOUND, resp.getPresence(),
                "唯一允许上游重新下单的一态，不得被模板改写");
    }

    /** 查到订单时原样透传 */
    @Test
    void foundIsPassedThrough() {
        OrderRespDTO resp = new StubQueryService(Behaviour.FOUND).orderQuery(req());

        assertEquals(OrderPresence.FOUND, resp.getPresence());
        assertEquals("SUPPLIER-ORDER-1", resp.getSupplierOrderId());
    }

    private enum Behaviour {
        RETURN_NULL, THROW, CONVERT_TO_NULL, OMIT_PRESENCE, NOT_FOUND, FOUND
    }

    /** 以桩替代真实供应商，逐一制造模板需要兜住的情形 */
    private static final class StubQueryService extends AbstractOrderQuerySyncSupportService<String> {

        private final Behaviour behaviour;

        private StubQueryService(Behaviour behaviour) {
            this.behaviour = behaviour;
        }

        @Override
        public String doOrderQuery(OrderQueryReq orderQueryReq) {
            switch (behaviour) {
                case RETURN_NULL:
                    return null;
                case THROW:
                    throw new IllegalStateException("read timed out");
                default:
                    return "raw-supplier-response";
            }
        }

        @Override
        public OrderRespDTO orderQueryRespConvert(String raw) {
            switch (behaviour) {
                case CONVERT_TO_NULL:
                    return null;
                case OMIT_PRESENCE:
                    return OrderRespDTO.builder().supplierOrderId("SUPPLIER-ORDER-1").build();
                case NOT_FOUND:
                    return OrderRespDTO.builder().presence(OrderPresence.NOT_FOUND).build();
                default:
                    return OrderRespDTO.builder()
                            .presence(OrderPresence.FOUND)
                            .supplierOrderId("SUPPLIER-ORDER-1")
                            .build();
            }
        }
    }
}
