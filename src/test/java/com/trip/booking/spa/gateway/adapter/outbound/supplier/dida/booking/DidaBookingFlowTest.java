package com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.booking;

import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.BookingRespDTO;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.BookingReq;
import com.trip.booking.spa.gateway.adapter.outbound.state.offer.Offer;
import com.trip.booking.spa.gateway.adapter.outbound.state.offer.OfferStore;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.booking.client.BookingConfirmAccess;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.order.client.BookingSearchAccess;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.DidaOfferCredentials;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.DidaOrderWireNameTest;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.DidaProperties;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.model.DidaBookingConfirmRequest;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.model.DidaBookingConfirmResponse;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.model.DidaBookingSearchRequest;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.model.DidaBookingSearchResponse;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.model.DidaError;
import com.trip.booking.spa.gateway.domain.booking.BookingOutcome;
import com.trip.booking.spa.platform.http.asynchttp.BaseResponse;
import com.trip.booking.spa.platform.http.asynchttp.ResponseResult;
import com.trip.booking.spa.platform.ratelimit.CallPurpose;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 下单<b>编排</b>的守护（理由同 DidaCancelFlowTest）：成单核销句柄、非终态带单号回 UNKNOWN、
 * 疑似重复反查收敛、无响应 UNKNOWN。夹具是生产真实报文（2025-11-28 成单、2026-09-13 成单、
 * 3005 错误、查单 Status 2/3/5）。
 */
class DidaBookingFlowTest {

    private static final class Harness extends DidaBookingSyncServiceImpl {
        DidaBookingConfirmResponse confirm;
        DidaBookingSearchResponse search;
        final List<String> calls = new ArrayList<>();
        final OfferStore store = mock(OfferStore.class);

        Harness() throws IOException {
            DidaProperties props = new DidaProperties();
            props.setClientId("BJ-TEST");
            props.setLicenseKey("KEY-TEST");
            props.setBookingEnabled(true);
            ReflectionTestUtils.setField(this, "properties", props);
            ReflectionTestUtils.setField(this, "offerStore", store);
            when(store.resolve("of_test")).thenReturn(offer());
        }

        @Override
        protected BookingConfirmAccess confirmAccess() {
            return new BookingConfirmAccess(props()) {
                @Override
                public ResponseResult<DidaBookingConfirmResponse> access(DidaBookingConfirmRequest request, CallPurpose purpose) {
                    calls.add("confirm:" + request.getClientReference());
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
                    return wrap(search);
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
    @DisplayName("Status=2 成单 → SUCCESS、sOrderId=BookingID、句柄核销")
    void confirmedConsumesOffer() throws IOException {
        Harness h = new Harness();
        h.confirm = read("/dida/booking-confirm-real-20260913.json", DidaBookingConfirmResponse.class);

        BookingRespDTO r = h.booking(req());

        assertEquals(BookingOutcome.SUCCESS, r.getOutcome());
        assertEquals("18698545882", r.getSOrderId());
        assertNull(r.getSConfirmationNumber(), "该单未回 HCN");
        verify(h.store).consume("of_test");
        assertEquals(List.of("confirm:ORDER-1"), h.calls);
    }

    @Test
    @DisplayName("成功信封但 Status=5 → UNKNOWN 带单号，句柄不核销")
    void pendingIsUnknownWithBookingId() throws IOException {
        Harness h = new Harness();
        h.confirm = read("/dida/booking-confirm-real-20260913.json", DidaBookingConfirmResponse.class);
        h.confirm.bookingDetails().setStatus(5);

        BookingRespDTO r = h.booking(req());

        assertEquals(BookingOutcome.UNKNOWN, r.getOutcome());
        assertEquals("18698545882", r.getSOrderId());
        verify(h.store, never()).consume("of_test");
    }

    @Test
    @DisplayName("3005 入住人信息不正确（真实报文）→ FAILED 带原码；无响应 → UNKNOWN")
    void deterministicFailureAndTimeout() throws IOException {
        Harness h = new Harness();
        h.confirm = read("/dida/booking-confirm-error-3005-real.json", DidaBookingConfirmResponse.class);
        BookingRespDTO r = h.booking(req());
        assertEquals(BookingOutcome.FAILED, r.getOutcome());
        assertEquals("3005", r.getSupplierErrorCode());
        verify(h.store, never()).consume("of_test");

        Harness h2 = new Harness();
        h2.confirm = null;
        assertEquals(BookingOutcome.UNKNOWN, h2.booking(req()).getOutcome());
    }

    @Test
    @DisplayName("3019 客户订单号重复 → 按我方单号反查：Status=2 收敛为 SUCCESS 并核销；Status=3 → FAILED 提示换单号；空列表 → UNKNOWN")
    void duplicateSuspectRequery() throws IOException {
        Harness h = new Harness();
        h.confirm = error("3019");
        h.search = read("/dida/booking-search-status2-real-20251128.json", DidaBookingSearchResponse.class);
        BookingRespDTO r = h.booking(req());
        assertEquals(BookingOutcome.SUCCESS, r.getOutcome());
        assertEquals("15801485798", r.getSOrderId());
        verify(h.store).consume("of_test");
        assertEquals(List.of("confirm:ORDER-1", "search:ref=ORDER-1"), h.calls);

        Harness canceled = new Harness();
        canceled.confirm = error("3018");
        canceled.search = read("/dida/booking-search-status3-real-20251128.json", DidaBookingSearchResponse.class);
        BookingRespDTO r2 = canceled.booking(req());
        assertEquals(BookingOutcome.FAILED, r2.getOutcome());
        assertEquals("3018", r2.getSupplierErrorCode());
        assertEquals("15801485798", r2.getSOrderId());

        Harness empty = new Harness();
        empty.confirm = error("3039");
        empty.search = read("/dida/booking-search-empty-real-20260912.json", DidaBookingSearchResponse.class);
        assertEquals(BookingOutcome.UNKNOWN, empty.booking(req()).getOutcome());
    }

    private static DidaBookingConfirmResponse error(String code) {
        DidaError e = new DidaError();
        e.setCode(code);
        e.setMessage("test");
        DidaBookingConfirmResponse resp = new DidaBookingConfirmResponse();
        resp.setError(e);
        return resp;
    }

    private static BookingReq req() {
        return DidaBookingRequestShapeTest.req("张/三", "李/四", "13800000000", "2026-10-01", "2026-10-03", 1);
    }

    private static Offer offer() {
        Map<String, String> m = new HashMap<>();
        m.put(DidaOfferCredentials.REFERENCE_NO, "18698545882");
        m.put(DidaOfferCredentials.HOTEL_ID, "875535");
        m.put(DidaOfferCredentials.CHECK_IN, "2026-10-01");
        m.put(DidaOfferCredentials.CHECK_OUT, "2026-10-03");
        m.put(DidaOfferCredentials.ROOM_NUM, "1");
        m.put(DidaOfferCredentials.ADULT_COUNT, "1");
        m.put(DidaOfferCredentials.CHILD_AGES, "");
        m.put(DidaOfferCredentials.DECLARED_TOTAL, "106");
        m.put(DidaOfferCredentials.CURRENCY, "CNY");
        return DidaBookingRequestShapeTest.offer(m);
    }

    private static <T> T read(String resource, Class<T> type) throws IOException {
        return DidaOrderWireNameTest.read(resource, type);
    }
}
