package com.trip.booking.spa.gateway.application.cancellation;

import com.trip.booking.spa.gateway.domain.booking.CancelOutcome;
import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.CancelRespDTO;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.CancelReq;
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
 * <p>改造前该模板一律返回 null，上游无从区分「取消成功」「确定失败」「不确定」。
 */
class AbstractCancelSyncSupportServiceTest {

    private static final String ORDER_ID = "TB20260814001";
    private static final String SUPPLIER_ORDER_ID = "7330509295286";

    /** 供应商无响应：不得判失败，必须 UNKNOWN */
    @Test
    void reportsUnknownWhenSupplierReturnsNothing() {
        CancelRespDTO resp = new StubService(null, null).cancel(request());

        assertNotNull(resp, "禁止返回 null——上游无从区分三态");
        assertEquals(CancelOutcome.UNKNOWN, resp.getOutcome());
        assertTrue(resp.getMessage().contains("查单"), "必须引导上游查单确证");
    }

    /** 转换失败同样不得判失败 */
    @Test
    void reportsUnknownWhenConvertReturnsNull() {
        CancelRespDTO resp = new StubService("已取消", null).cancel(request());

        assertEquals(CancelOutcome.UNKNOWN, resp.getOutcome());
    }

    /** 抛异常同样不得判失败——异常不等于「没生效」 */
    @Test
    void reportsUnknownWhenImplementationThrows() {
        CancelRespDTO resp = new ThrowingService().cancel(request());

        assertEquals(CancelOutcome.UNKNOWN, resp.getOutcome());
        assertTrue(resp.getMessage().contains("查单"));
    }

    /** 实现忘了填 outcome 时，按最安全的一态兜底，而不是当成成功 */
    @Test
    void defaultsToUnknownWhenImplementationOmitsOutcome() {
        CancelRespDTO resp = new StubService("ok",
                CancelRespDTO.builder().build()).cancel(request());

        assertEquals(CancelOutcome.UNKNOWN, resp.getOutcome());
    }

    /** 供应商实现明确判定的结果必须原样透出，不被模板改写 */
    @Test
    void keepsOutcomeDecidedByImplementation() {
        CancelRespDTO decided = CancelRespDTO.builder()
                .outcome(CancelOutcome.FAILED)
                .message("订单不存在")
                .build();

        CancelRespDTO resp = new StubService("ok", decided).cancel(request());

        assertEquals(CancelOutcome.FAILED, resp.getOutcome());
        assertEquals("订单不存在", resp.getMessage());
    }

    /** 兜底时必须带上我方单号，否则上游无从关联是哪一笔 */
    @Test
    void carriesOrderIdentifiersOnFallback() {
        CancelRespDTO resp = new StubService(null, null).cancel(request());

        assertEquals(ORDER_ID, resp.getOrderId());
        assertEquals(SUPPLIER_ORDER_ID, resp.getSOrderId());
    }

    /** 实现未回填我方单号时，模板补齐 */
    @Test
    void backfillsOrderIdWhenImplementationOmitsIt() {
        CancelRespDTO resp = new StubService("ok",
                CancelRespDTO.builder().outcome(CancelOutcome.SUCCESS).build()).cancel(request());

        assertEquals(ORDER_ID, resp.getOrderId());
    }

    private CancelReq request() {
        return CancelReq.builder()
                .supplierId(10005)
                .supplierOrderId(SUPPLIER_ORDER_ID)
                .orderId(ORDER_ID)
                .build();
    }

    private static class StubService extends AbstractCancelSyncSupportService<String> {
        private final String raw;
        private final CancelRespDTO converted;

        StubService(String raw, CancelRespDTO converted) {
            this.raw = raw;
            this.converted = converted;
        }

        @Override
        public String doCancel(CancelReq cancelReq) {
            return raw;
        }

        @Override
        public CancelRespDTO cancelRespConvert(String s) {
            return converted;
        }
    }

    private static class ThrowingService extends AbstractCancelSyncSupportService<String> {
        @Override
        public String doCancel(CancelReq cancelReq) {
            throw new IllegalStateException("供应商连接中断");
        }

        @Override
        public CancelRespDTO cancelRespConvert(String s) {
            return null;
        }
    }
}
