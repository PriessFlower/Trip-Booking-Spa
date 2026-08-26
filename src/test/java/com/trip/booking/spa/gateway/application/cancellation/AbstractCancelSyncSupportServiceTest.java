package com.trip.booking.spa.gateway.application.cancellation;

import com.trip.booking.spa.gateway.domain.booking.CancelOutcome;
import com.trip.booking.spa.gateway.domain.cancellation.CancelCommand;
import com.trip.booking.spa.gateway.domain.cancellation.CancelResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 钉住取消模板的判定纪律：<b>无法证明取消未生效时，一律回报 UNKNOWN，绝不回报失败</b>。
 *
 * <p>取消的两个方向误判都是资损：判失败而实际已取消，上游继续持有一个已不存在的订单，
 * 旅客到店无房；判成功而实际未取消，上游据此退款放行，房仍占着、费用仍在产生。
 *
 * <p>与旧版（REST DTO 两段式）的差异：出参改领域 {@link CancelResult}，只能经三态工厂
 * 构造——旧测试里"实现忘了填 outcome"与"convert 返回 null"两个形态在构造上已不成立，
 * 对应用例随之删除，不是漏守。
 */
class AbstractCancelSyncSupportServiceTest {

    private static final String ORDER_ID = "TB20260814001";
    private static final String SUPPLIER_ORDER_ID = "7330509295286";

    /** 供应商无响应（实现返回 null）：不得判失败，必须 UNKNOWN */
    @Test
    void reportsUnknownWhenSupplierReturnsNothing() {
        CancelResult result = new StubService(null).cancel(command());

        assertNotNull(result, "禁止返回 null——上游无从区分三态");
        assertEquals(CancelOutcome.UNKNOWN, result.outcome());
        assertTrue(result.message().contains("查单"), "必须引导上游查单确证");
    }

    /** 抛异常同样不得判失败——异常不等于「没生效」 */
    @Test
    void reportsUnknownWhenImplementationThrows() {
        CancelResult result = new ThrowingService().cancel(command());

        assertEquals(CancelOutcome.UNKNOWN, result.outcome());
        assertTrue(result.message().contains("查单"));
    }

    /** 供应商实现明确判定的结果必须原样透出，不被模板改写 */
    @Test
    void keepsOutcomeDecidedByImplementation() {
        CancelResult decided = CancelResult.failed(ORDER_ID, SUPPLIER_ORDER_ID, "E123", "订单不存在");

        CancelResult result = new StubService(decided).cancel(command());

        assertEquals(CancelOutcome.FAILED, result.outcome());
        assertEquals("订单不存在", result.message());
        assertEquals("E123", result.supplierErrorCode());
    }

    /** 兜底时必须带上双方单号，否则上游无从关联是哪一笔 */
    @Test
    void carriesOrderIdentifiersOnFallback() {
        CancelResult result = new StubService(null).cancel(command());

        assertEquals(ORDER_ID, result.orderId());
        assertEquals(SUPPLIER_ORDER_ID, result.supplierOrderId());
    }

    /** 实现未回填我方单号时，模板补齐 */
    @Test
    void backfillsOrderIdWhenImplementationOmitsIt() {
        CancelResult decided = CancelResult.unknown(null, SUPPLIER_ORDER_ID, null, "请查单");

        CancelResult result = new StubService(decided).cancel(command());

        assertEquals(ORDER_ID, result.orderId());
        assertEquals(SUPPLIER_ORDER_ID, result.supplierOrderId());
    }

    private CancelCommand command() {
        return CancelCommand.of(10005, ORDER_ID, SUPPLIER_ORDER_ID);
    }

    private static class StubService extends AbstractCancelSyncSupportService {
        private final CancelResult result;

        StubService(CancelResult result) {
            this.result = result;
        }

        @Override
        protected CancelResult doCancel(CancelCommand command) {
            return result;
        }
    }

    private static class ThrowingService extends AbstractCancelSyncSupportService {
        @Override
        protected CancelResult doCancel(CancelCommand command) {
            throw new IllegalStateException("供应商连接中断");
        }
    }
}
