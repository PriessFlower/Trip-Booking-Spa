package com.trip.booking.spa.gateway.application.booking;

import com.trip.booking.spa.gateway.domain.booking.BookingCommand;
import com.trip.booking.spa.gateway.domain.booking.BookingOutcome;
import com.trip.booking.spa.gateway.domain.booking.BookingResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 钉死下单模板的兜底语义。
 *
 * <p>这些用例守的是一条资损防线：<b>不确定绝不能表达为失败</b>。上游收到失败会退款并释放库存，
 * 而超时或异常时供应商可能已真实成单。若后续有人把兜底改回返回 null 或 FAILED，这里必须失败。
 */
class AbstractBookingSyncSupportServiceTest {

    private static BookingCommand command() {
        return BookingCommand.builder()
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

    /** 我方单号是幂等与对账的坐标，缺了就没法下单——构造即失败 */
    @Test
    void ownOrderIdIsRequired() {
        assertThrows(NullPointerException.class, () -> BookingCommand.builder().supplierId(10005).build());
    }

    /** 供应商无响应（超时、连接中断）不等于未下单，必须回报 UNKNOWN */
    @Test
    void nullSupplierResponseIsReportedAsUnknown() {
        BookingResult result = new StubBookingService(Behaviour.RETURN_NULL).booking(command());

        assertNotNull(result, "兜底禁止返回 null：控制层会将其表达为接口错误，上游据此退款");
        assertEquals(BookingOutcome.UNKNOWN, result.outcome());
        assertEquals("UPSTREAM-ORDER-1", result.orderId());
    }

    /** 下单过程抛异常时，本地无从区分「请求未发出」与「已发出但响应丢失」，一律 UNKNOWN */
    @Test
    void exceptionIsReportedAsUnknownNotFailed() {
        BookingResult result = new StubBookingService(Behaviour.THROW).booking(command());

        assertNotNull(result);
        assertEquals(BookingOutcome.UNKNOWN, result.outcome(),
                "异常不足以断定未下单，判 FAILED 会导致已退款却仍占房");
    }

    /**
     * 三态漏填这件事已经不可能发生——{@link BookingResult} 的三个工厂各自钉死 outcome。
     *
     * <p>此前模板有一道运行期兜底（"实现方漏填就按 UNKNOWN 处理"），随本次切领域模型删除：
     * 构造上不成立的事，不需要在运行期再查一遍。本用例替代那道兜底的守卫职责。
     */
    @Test
    void outcomeCannotBeOmitted() {
        assertEquals(BookingOutcome.SUCCESS,
                BookingResult.success("O-1", "S-1", null, null).outcome());
        assertEquals(BookingOutcome.FAILED,
                BookingResult.failed("O-1", "c", "m", "m").outcome());
        assertEquals(BookingOutcome.UNKNOWN, BookingResult.unknown("O-1", "m").outcome());
    }

    /** SUCCESS 必须带供应商单号：没有单号的"成功"无法对账，也无法查单复核 */
    @Test
    void successWithoutSupplierOrderIdIsRejected() {
        assertThrows(NullPointerException.class,
                () -> BookingResult.success("O-1", null, null, null));
    }

    /** 供应商明确成单时原样透传，并回填上游订单号 */
    @Test
    void successIsPassedThroughAndOrderIdBackfilled() {
        BookingResult result = new StubBookingService(Behaviour.SUCCEED).booking(command());

        assertEquals(BookingOutcome.SUCCESS, result.outcome());
        assertEquals("SUPPLIER-1", result.supplierOrderId());
        assertEquals("UPSTREAM-ORDER-1", result.orderId(), "实现未回显订单号时应由模板补齐");
    }

    /** 供应商给出业务性拒绝时，实现有权判 FAILED，模板不得篡改 */
    @Test
    void deterministicFailureIsPreserved() {
        BookingResult result = new StubBookingService(Behaviour.FAIL).booking(command());

        assertEquals(BookingOutcome.FAILED, result.outcome());
        assertEquals("sold_out", result.supplierErrorCode());
    }

    private enum Behaviour {
        RETURN_NULL, THROW, SUCCEED, FAIL
    }

    /** 以桩替代真实供应商，逐一制造模板需要兜住的情形 */
    private static final class StubBookingService extends AbstractBookingSyncSupportService {

        private final Behaviour behaviour;

        private StubBookingService(Behaviour behaviour) {
            this.behaviour = behaviour;
        }

        @Override
        protected String bookingGateKey() {
            return "stub.booking-enabled";
        }

        /** 本类验的是三态兜底，不是闸口；放行以免每条用例都被闸挡在门外 */
        @Override
        protected boolean bookingAllowed() {
            return true;
        }

        @Override
        protected BookingResult doBooking(BookingCommand command) {
            switch (behaviour) {
                case RETURN_NULL:
                    return null;
                case THROW:
                    throw new IllegalStateException("read timed out");
                case FAIL:
                    return BookingResult.failed(null, "sold_out", "已售罄", "已售罄");
                default:
                    // 不回显 orderId，验模板补齐
                    return BookingResult.success(null, "SUPPLIER-1", null, null);
            }
        }
    }
}
