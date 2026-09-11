package com.trip.booking.spa.gateway.application.order;

import com.trip.booking.spa.gateway.domain.booking.OrderPresence;
import com.trip.booking.spa.gateway.domain.order.OrderQueryCommand;
import com.trip.booking.spa.gateway.domain.order.OrderQueryResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 钉死查单模板的兜底语义。
 *
 * <p>这些用例守的是一条资损防线：<b>查不到绝不能表达为「订单不存在」</b>。查单是下单回报
 * UNKNOWN 后的唯一确证手段，上游只在 NOT_FOUND 时才被允许重新下单。若后续有人把兜底改回
 * 返回 null 或 NOT_FOUND，这里必须失败。
 */
class AbstractOrderQuerySyncSupportServiceTest {

    private static OrderQueryCommand command() {
        return OrderQueryCommand.of(10005, "UPSTREAM-ORDER-1", null);
    }

    /** 供应商订单号缺失是常态：下单超时时上游本来就没有它，查单必须照样能调 */
    @Test
    void supplierOrderIdIsOptional() {
        OrderQueryCommand built = command();

        assertNull(built.supplierOrderId(), "供应商单号可空——最需要查单的场景恰恰是拿不到它的时候");
        assertEquals("UPSTREAM-ORDER-1", built.orderId());
    }

    /** 我方单号是唯一坐标，缺了就没法查——构造即失败，不许带着 null 往下走 */
    @Test
    void ownOrderIdIsRequired() {
        assertThrows(NullPointerException.class, () -> OrderQueryCommand.of(10005, null, "S-1"));
    }

    /** 查单无响应不等于订单不存在，必须回报 INDETERMINATE */
    @Test
    void nullSupplierResponseIsReportedAsIndeterminate() {
        OrderQueryResult result = new StubQueryService(Behaviour.RETURN_NULL).orderQuery(command());

        assertNotNull(result, "兜底禁止返回 null：控制层会将其表达为接口错误，上游无从判断能否重下");
        assertEquals(OrderPresence.INDETERMINATE, result.presence());
    }

    /** 查单抛异常同样不足以断定订单不存在 */
    @Test
    void exceptionIsReportedAsIndeterminateNotNotFound() {
        OrderQueryResult result = new StubQueryService(Behaviour.THROW).orderQuery(command());

        assertNotNull(result);
        assertEquals(OrderPresence.INDETERMINATE, result.presence(),
                "异常不足以断定订单不存在，判 NOT_FOUND 会导致重复下单");
    }

    /** 转换器返回空属不可判，不得退化为「订单不存在」 */
    @Test
    void unconvertibleResponseIsReportedAsIndeterminate() {
        OrderQueryResult result = new StubQueryService(Behaviour.CONVERT_TO_NULL).orderQuery(command());

        assertNotNull(result);
        assertEquals(OrderPresence.INDETERMINATE, result.presence());
    }

    /**
     * 三态漏填这件事已经不可能发生——{@link OrderQueryResult} 的三个工厂各自钉死 presence，
     * 没有一条路径能造出 presence 为 null 的结果。
     *
     * <p>此前模板有一道运行期兜底（"实现方漏填就按 INDETERMINATE 处理"），随本次改动删除：
     * 构造上不成立的事，不需要在运行期再查一遍。本用例替代那道兜底的守卫职责。
     */
    @Test
    void presenceCannotBeOmitted() {
        assertEquals(OrderPresence.FOUND, OrderQueryResult.found().build().presence());
        assertEquals(OrderPresence.NOT_FOUND, OrderQueryResult.notFound("x").presence());
        assertEquals(OrderPresence.INDETERMINATE, OrderQueryResult.indeterminate("x").presence());
    }

    /** 供应商明确回答「没有这笔订单」时，实现有权判 NOT_FOUND，模板不得篡改 */
    @Test
    void notFoundIsPreserved() {
        OrderQueryResult result = new StubQueryService(Behaviour.NOT_FOUND).orderQuery(command());

        assertEquals(OrderPresence.NOT_FOUND, result.presence(),
                "唯一允许上游重新下单的一态，不得被模板改写");
    }

    /** 查到订单时原样透传 */
    @Test
    void foundIsPassedThrough() {
        OrderQueryResult result = new StubQueryService(Behaviour.FOUND).orderQuery(command());

        assertEquals(OrderPresence.FOUND, result.presence());
        assertEquals("SUPPLIER-ORDER-1", result.supplierOrderId());
    }

    private enum Behaviour {
        RETURN_NULL, THROW, CONVERT_TO_NULL, NOT_FOUND, FOUND
    }

    /** 以桩替代真实供应商，逐一制造模板需要兜住的情形 */
    private static final class StubQueryService extends AbstractOrderQuerySyncSupportService<String> {

        private final Behaviour behaviour;

        private StubQueryService(Behaviour behaviour) {
            this.behaviour = behaviour;
        }

        @Override
        public String doOrderQuery(OrderQueryCommand command) {
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
        public OrderQueryResult orderQueryRespConvert(String raw) {
            switch (behaviour) {
                case CONVERT_TO_NULL:
                    return null;
                case NOT_FOUND:
                    return OrderQueryResult.notFound("供应商确认无此单");
                default:
                    return OrderQueryResult.found().supplierOrderId("SUPPLIER-ORDER-1").build();
            }
        }
    }
}
