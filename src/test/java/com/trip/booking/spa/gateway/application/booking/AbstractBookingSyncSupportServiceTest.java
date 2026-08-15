package com.trip.booking.spa.gateway.application.booking;

import com.trip.booking.spa.gateway.domain.booking.BookingOutcome;
import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.BookingRespDTO;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.BookingReq;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 钉死下单模板的兜底语义。
 *
 * <p>这些用例守的是一条资损防线：<b>不确定绝不能表达为失败</b>。上游收到失败会退款并释放库存，
 * 而超时或异常时供应商可能已真实成单。若后续有人把兜底改回返回 null 或 FAILED，这里必须失败。
 */
class AbstractBookingSyncSupportServiceTest {

    /** BookingReq 的必填字段较多，此处只关注 orderId 的回显，其余给合法占位值 */
    private static BookingReq req() {
        return BookingReq.builder()
                .supplierId(10005)
                .orderId("UPSTREAM-ORDER-1")
                .personName("Zhang San")
                .contactName("Zhang San")
                .contactPhone("13800000000")
                .checkIn("2026-09-15")
                .checkOut("2026-09-16")
                .roomNum(1)
                .totalPrice(100000)
                .settlePrice(90000)
                .build();
    }

    /** 供应商无响应（超时、连接中断）不等于未下单，必须回报 UNKNOWN */
    @Test
    void nullSupplierResponseIsReportedAsUnknown() {
        BookingRespDTO resp = new StubBookingService(Behaviour.RETURN_NULL).booking(req());

        assertNotNull(resp, "兜底禁止返回 null：控制层会将其表达为接口错误，上游据此退款");
        assertEquals(BookingOutcome.UNKNOWN, resp.getOutcome());
        assertEquals("UPSTREAM-ORDER-1", resp.getOrderId());
    }

    /** 下单过程抛异常时，本地无从区分「请求未发出」与「已发出但响应丢失」，一律 UNKNOWN */
    @Test
    void exceptionIsReportedAsUnknownNotFailed() {
        BookingRespDTO resp = new StubBookingService(Behaviour.THROW).booking(req());

        assertNotNull(resp);
        assertEquals(BookingOutcome.UNKNOWN, resp.getOutcome(),
                "异常不足以断定未下单，判 FAILED 会导致已退款却仍占房");
    }

    /** 转换器返回空同样属不可判，不得退化为失败 */
    @Test
    void unconvertibleResponseIsReportedAsUnknown() {
        BookingRespDTO resp = new StubBookingService(Behaviour.CONVERT_TO_NULL).booking(req());

        assertNotNull(resp);
        assertEquals(BookingOutcome.UNKNOWN, resp.getOutcome());
    }

    /** 实现漏填三态时按 UNKNOWN 兜底，避免默认值悄悄退化成某一态 */
    @Test
    void missingOutcomeDefaultsToUnknown() {
        BookingRespDTO resp = new StubBookingService(Behaviour.OMIT_OUTCOME).booking(req());

        assertEquals(BookingOutcome.UNKNOWN, resp.getOutcome());
    }

    /** 供应商明确成单时原样透传，并回填上游订单号 */
    @Test
    void successIsPassedThroughAndOrderIdBackfilled() {
        BookingRespDTO resp = new StubBookingService(Behaviour.SUCCEED).booking(req());

        assertEquals(BookingOutcome.SUCCESS, resp.getOutcome());
        assertEquals("SUPPLIER-1", resp.getSOrderId());
        assertEquals("UPSTREAM-ORDER-1", resp.getOrderId(), "实现未回显订单号时应由模板补齐");
    }

    /** 供应商给出业务性拒绝时，实现有权判 FAILED，模板不得篡改 */
    @Test
    void deterministicFailureIsPreserved() {
        BookingRespDTO resp = new StubBookingService(Behaviour.FAIL).booking(req());

        assertEquals(BookingOutcome.FAILED, resp.getOutcome());
        assertEquals("sold_out", resp.getSupplierErrorCode());
    }

    private enum Behaviour {
        RETURN_NULL, THROW, CONVERT_TO_NULL, OMIT_OUTCOME, SUCCEED, FAIL
    }

    /** 以桩替代真实供应商，逐一制造模板需要兜住的六种情形 */
    private static final class StubBookingService extends AbstractBookingSyncSupportService<String> {

        private final Behaviour behaviour;

        private StubBookingService(Behaviour behaviour) {
            this.behaviour = behaviour;
        }

        @Override
        public String doBooking(BookingReq bookingReq) {
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
        public BookingRespDTO bookingRespConvert(String raw) {
            switch (behaviour) {
                case CONVERT_TO_NULL:
                    return null;
                case OMIT_OUTCOME:
                    return BookingRespDTO.builder().orderId("UPSTREAM-ORDER-1").build();
                case FAIL:
                    return BookingRespDTO.builder()
                            .outcome(BookingOutcome.FAILED)
                            .supplierErrorCode("sold_out")
                            .build();
                default:
                    return BookingRespDTO.builder()
                            .outcome(BookingOutcome.SUCCESS)
                            .sOrderId("SUPPLIER-1")
                            .build();
            }
        }
    }
}
