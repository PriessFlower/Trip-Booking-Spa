package com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.cancellation;

import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.cancellation.client.BookingCancelAccess;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.cancellation.client.BookingCancelConfirmAccess;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.order.client.BookingSearchAccess;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.DidaOrderWireNameTest;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.DidaProperties;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.model.DidaBookingCancelConfirmRequest;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.model.DidaBookingCancelConfirmResponse;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.model.DidaBookingCancelRequest;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.model.DidaBookingCancelResponse;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.model.DidaBookingSearchRequest;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.model.DidaBookingSearchResponse;
import com.trip.booking.spa.gateway.domain.booking.CancelOutcome;
import com.trip.booking.spa.gateway.domain.cancellation.CancelCommand;
import com.trip.booking.spa.gateway.domain.cancellation.CancelPenalty.PenaltySource;
import com.trip.booking.spa.gateway.domain.cancellation.CancelResult;
import com.trip.booking.spa.platform.http.asynchttp.BaseResponse;
import com.trip.booking.spa.platform.http.asynchttp.ResponseResult;
import com.trip.booking.spa.platform.ratelimit.CallPurpose;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 取消<b>编排</b>的守护：预取消 → 确认取消 → 查单确证三步的分支，全部用生产真实报文夹具驱动，
 * 不打 HTTP（通道由子类钩子替换）。此前只测了分类器与罚金推算这些零件，装配线本身没跑过；
 * 而道旅在 SPA 侧尚无流量，真单闭环没有日期，这条测试就是开闸前唯一能挡住"分支接反"的东西。
 *
 * <p>三份真实报文来自 cursor 生产账号：预取消免罚（2026-09-10，Amount 0）、确认取消空 Success
 * （2026-01-31）、查单 Status=3（2025-11-28）；另用 2026-09-10 的 3018「已取消」错误信封。
 */
class DidaCancelFlowTest {

    private static final String BOOKING_ID = "18667668003";

    /** 子类钩子：三条通道各返回一份预设响应（null=无响应），并记录调用顺序 */
    private static final class Harness extends DidaCancelSyncServiceImpl {
        DidaBookingCancelResponse pre;
        DidaBookingCancelConfirmResponse confirm;
        List<DidaBookingSearchResponse> searches = new ArrayList<>();
        final List<String> calls = new ArrayList<>();
        DidaBookingCancelConfirmRequest sentConfirm;

        Harness() {
            DidaProperties props = new DidaProperties();
            props.setClientId("BJ-TEST");
            props.setLicenseKey("KEY-TEST");
            ReflectionTestUtils.setField(this, "properties", props);
        }

        @Override
        protected BookingCancelAccess cancelAccess() {
            return new BookingCancelAccess(props()) {
                @Override
                public ResponseResult<DidaBookingCancelResponse> access(DidaBookingCancelRequest request, CallPurpose purpose) {
                    calls.add("pre:" + request.getBookingId());
                    return wrap(pre);
                }
            };
        }

        @Override
        protected BookingCancelConfirmAccess cancelConfirmAccess() {
            return new BookingCancelConfirmAccess(props()) {
                @Override
                public ResponseResult<DidaBookingCancelConfirmResponse> access(DidaBookingCancelConfirmRequest request, CallPurpose purpose) {
                    calls.add("confirm:" + request.getConfirmId());
                    sentConfirm = request;
                    return wrap(confirm);
                }
            };
        }

        @Override
        protected BookingSearchAccess searchAccess() {
            return new BookingSearchAccess(props()) {
                @Override
                public ResponseResult<DidaBookingSearchResponse> access(DidaBookingSearchRequest request, CallPurpose purpose) {
                    calls.add("search:" + (request.getSearchBy().getBookingId() != null
                            ? request.getSearchBy().getBookingId() : "ref=" + request.getSearchBy().getBookingInfo().getClientReference()));
                    return wrap(searches.isEmpty() ? null : searches.remove(0));
                }
            };
        }

        private DidaProperties props() {
            return (DidaProperties) ReflectionTestUtils.getField(this, "properties");
        }

        private static <T extends BaseResponse> ResponseResult<T> wrap(T data) {
            return data == null ? null : new ResponseResult<>(200, "", data);
        }
    }

    @Test
    @DisplayName("主路径：预取消 Amount 0 → 确认取消空 Success → 查单 Status=3 → SUCCESS，罚金 FIELD 0 CNY，Description=行程变更")
    void happyPathVerifiedByStatus3() throws IOException {
        Harness h = new Harness();
        h.pre = read("/dida/booking-cancel-free-real-20260910.json", DidaBookingCancelResponse.class);
        h.confirm = read("/dida/booking-cancel-confirm-real-20260131.json", DidaBookingCancelConfirmResponse.class);
        h.searches.add(read("/dida/booking-search-status3-real-20251128.json", DidaBookingSearchResponse.class));

        CancelResult r = h.cancel(CancelCommand.of(10020, "ORDER-1", BOOKING_ID));

        assertEquals(CancelOutcome.SUCCESS, r.outcome());
        assertEquals(BOOKING_ID, r.supplierOrderId());
        assertEquals(PenaltySource.FIELD, r.penalty().source());
        assertEquals(0L, r.penalty().amount().amountCents());
        assertEquals("CNY", r.penalty().amount().currency());
        assertEquals(List.of("pre:" + BOOKING_ID, "confirm:DCC260910220812033", "search:" + BOOKING_ID), h.calls);
        assertEquals("行程变更", h.sentConfirm.getDescription());
    }

    @Test
    @DisplayName("确认取消无响应（cursor 实证：超时却已生效）→ UNKNOWN，不查单、不判败")
    void confirmTimeoutIsUnknown() throws IOException {
        Harness h = new Harness();
        h.pre = read("/dida/booking-cancel-real-20251128.json", DidaBookingCancelResponse.class);
        h.confirm = null;

        CancelResult r = h.cancel(CancelCommand.of(10020, "ORDER-1", BOOKING_ID));

        assertEquals(CancelOutcome.UNKNOWN, r.outcome());
        assertEquals(PenaltySource.NONE, r.penalty().source(), "结果不确定时不许申报罚金");
        assertEquals(2, h.calls.size());
    }

    @Test
    @DisplayName("确认成功但查单仍是 Status=2（状态落后）→ UNKNOWN 引导稍后查单；查单无响应同样 UNKNOWN")
    void confirmedButStatusNotYet3IsUnknown() throws IOException {
        Harness h = new Harness();
        h.pre = read("/dida/booking-cancel-free-real-20260910.json", DidaBookingCancelResponse.class);
        h.confirm = read("/dida/booking-cancel-confirm-real-20260131.json", DidaBookingCancelConfirmResponse.class);
        h.searches.add(read("/dida/booking-search-status2-real-20251128.json", DidaBookingSearchResponse.class));
        CancelResult r = h.cancel(CancelCommand.of(10020, "ORDER-1", BOOKING_ID));
        assertEquals(CancelOutcome.UNKNOWN, r.outcome());
        assertEquals("status:2", r.supplierErrorCode());

        Harness h2 = new Harness();
        h2.pre = h.pre;
        h2.confirm = h.confirm;
        assertEquals(CancelOutcome.UNKNOWN, h2.cancel(CancelCommand.of(10020, "ORDER-1", BOOKING_ID)).outcome());
    }

    @Test
    @DisplayName("预取消回 3018「已取消」→ 直接 SUCCESS（幂等），不再确认、不查单，罚金 NONE")
    void preCancelAlreadyCanceledIsIdempotentSuccess() throws IOException {
        Harness h = new Harness();
        h.pre = read("/dida/booking-cancel-error-3018-real-20260910.json", DidaBookingCancelResponse.class);

        CancelResult r = h.cancel(CancelCommand.of(10020, "ORDER-1", BOOKING_ID));

        assertEquals(CancelOutcome.SUCCESS, r.outcome());
        assertEquals(PenaltySource.NONE, r.penalty().source());
        assertEquals(List.of("pre:" + BOOKING_ID), h.calls);
    }

    @Test
    @DisplayName("预取消无响应 → UNKNOWN；预取消成功却无 ConfirmID → FAILED，都不发确认")
    void preCancelDegenerateCases() throws IOException {
        Harness h = new Harness();
        h.pre = null;
        assertEquals(CancelOutcome.UNKNOWN, h.cancel(CancelCommand.of(10020, "ORDER-1", BOOKING_ID)).outcome());

        Harness h2 = new Harness();
        h2.pre = read("/dida/booking-cancel-free-real-20260910.json", DidaBookingCancelResponse.class);
        h2.pre.getSuccess().setConfirmId(null);
        CancelResult r = h2.cancel(CancelCommand.of(10020, "ORDER-1", BOOKING_ID));
        assertEquals(CancelOutcome.FAILED, r.outcome());
        assertEquals("cancel_confirm_id_missing", r.supplierErrorCode());
        assertEquals(List.of("pre:" + BOOKING_ID), h2.calls);
    }

    @Test
    @DisplayName("无供应商单号：按我方单号反查取回 BookingID 再走两步；反查到 Status=3 直接幂等成功；反查空列表 → UNKNOWN 不发取消")
    void lookupByClientReference() throws IOException {
        Harness h = new Harness();
        h.searches.add(read("/dida/booking-search-status2-real-20251128.json", DidaBookingSearchResponse.class));
        h.pre = read("/dida/booking-cancel-real-20251128.json", DidaBookingCancelResponse.class);
        h.confirm = read("/dida/booking-cancel-confirm-real-20260131.json", DidaBookingCancelConfirmResponse.class);
        h.searches.add(read("/dida/booking-search-status3-real-20251128.json", DidaBookingSearchResponse.class));
        CancelResult r = h.cancel(CancelCommand.of(10020, "4a218db95273469591f43023a4dd87c4", null));
        assertEquals(CancelOutcome.SUCCESS, r.outcome());
        assertEquals("15801485798", r.supplierOrderId());
        assertEquals(684900L, r.penalty().amount().amountCents());
        assertEquals("search:ref=4a218db95273469591f43023a4dd87c4", h.calls.get(0));
        assertEquals("pre:15801485798", h.calls.get(1));

        Harness already = new Harness();
        already.searches.add(read("/dida/booking-search-status3-real-20251128.json", DidaBookingSearchResponse.class));
        CancelResult r2 = already.cancel(CancelCommand.of(10020, "4a218db95273469591f43023a4dd87c4", null));
        assertEquals(CancelOutcome.SUCCESS, r2.outcome());
        assertEquals(1, already.calls.size());

        Harness empty = new Harness();
        empty.searches.add(read("/dida/booking-search-empty-real-20260912.json", DidaBookingSearchResponse.class));
        CancelResult r3 = empty.cancel(CancelCommand.of(10020, "never-booked", null));
        assertEquals(CancelOutcome.UNKNOWN, r3.outcome());
        assertNull(r3.supplierOrderId());
        assertEquals(1, empty.calls.size(), "查不到唯一订单时取消不许发出");
    }

    private static <T> T read(String resource, Class<T> type) throws IOException {
        return DidaOrderWireNameTest.read(resource, type);
    }
}
