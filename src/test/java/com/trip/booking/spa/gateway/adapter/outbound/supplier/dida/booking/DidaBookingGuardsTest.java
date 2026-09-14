package com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.booking;

import com.trip.booking.spa.gateway.domain.booking.BookingResult;
import com.trip.booking.spa.gateway.domain.booking.BookingCommand;
import com.trip.booking.spa.gateway.adapter.outbound.state.offer.Offer;
import com.trip.booking.spa.gateway.adapter.outbound.state.offer.OfferStore;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.DidaOfferCredentials;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.DidaProperties;
import com.trip.booking.spa.gateway.domain.booking.BookingOutcome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 钉住下单前的本地判定：这些分支都在向道旅发出任何请求之前结束，供应商侧什么都没发生，
 * 故必须是 FAILED（上游可直接退款）而不是 UNKNOWN（上游白跑一次反查）。全程不打 HTTP。
 */
class DidaBookingGuardsTest {

    @Test
    @DisplayName("安全护栏 dida.booking-enabled 关闭 → FAILED booking_disabled，且不解析句柄")
    void gateClosedIsDeterministicFailure() {
        DidaBookingSyncServiceImpl service = DidaBookingRequestShapeTest.service();
        ((DidaProperties) ReflectionTestUtils.getField(service, "properties")).setBookingEnabled(false);
        OfferStore store = mock(OfferStore.class);
        ReflectionTestUtils.setField(service, "offerStore", store);

        BookingResult resp = service.booking(validReq());

        assertEquals(BookingOutcome.FAILED, resp.outcome());
        assertEquals("booking_disabled", resp.supplierErrorCode());
        verify(store, never()).resolve(anyString());
    }

    @Test
    @DisplayName("凭证未配置 → FAILED credentials_missing")
    void credentialsMissing() {
        DidaBookingSyncServiceImpl service = DidaBookingRequestShapeTest.service();
        ((DidaProperties) ReflectionTestUtils.getField(service, "properties")).setLicenseKey("");
        ReflectionTestUtils.setField(service, "offerStore", mock(OfferStore.class));

        assertEquals("credentials_missing", service.booking(validReq()).supplierErrorCode());
    }

    @Test
    @DisplayName("句柄缺失 / 取不回 / 不属道旅 / 缺必需凭据 → 各自的确定失败码")
    void offerGuards() {
        DidaBookingSyncServiceImpl service = DidaBookingRequestShapeTest.service();
        OfferStore store = mock(OfferStore.class);
        ReflectionTestUtils.setField(service, "offerStore", store);

        BookingCommand noOffer = DidaBookingRequestShapeTest.req(
                "张/三", "李/四", "13800000000", "2026-10-01", "2026-10-03", 1, null);
        assertEquals("missing_offer_id", service.booking(noOffer).supplierErrorCode());

        when(store.resolve("of_test")).thenReturn(null);
        assertEquals("offer_unresolvable", service.booking(validReq()).supplierErrorCode());

        when(store.resolve("of_test")).thenReturn(Offer.builder().supplierId(10015).credentials(fullCredentials())
                .expiresAt(System.currentTimeMillis() + 60_000).build());
        assertEquals("offer_supplier_mismatch", service.booking(validReq()).supplierErrorCode());

        Map<String, String> missing = fullCredentials();
        missing.remove(DidaOfferCredentials.REFERENCE_NO);
        when(store.resolve("of_test")).thenReturn(DidaBookingRequestShapeTest.offer(missing));
        BookingResult resp = service.booking(validReq());
        assertEquals("offer_credential_missing", resp.supplierErrorCode());
        assertEquals(BookingOutcome.FAILED, resp.outcome());
    }

    @Test
    @DisplayName("上游住期与句柄不一致 → FAILED stay_mismatch（道旅也必报 3001，拒于本地）")
    void stayMismatch() {
        DidaBookingSyncServiceImpl service = DidaBookingRequestShapeTest.service();
        OfferStore store = mock(OfferStore.class);
        ReflectionTestUtils.setField(service, "offerStore", store);
        when(store.resolve("of_test")).thenReturn(DidaBookingRequestShapeTest.offer(fullCredentials()));

        // 住期与句柄不一致：BookingCommand 不可变，直接建一条离店日不同的指令
        BookingCommand req = DidaBookingRequestShapeTest.req(
                "张/三", "李/四", "13800000000", "2026-10-01", "2026-10-04", 1, "of_test");
        BookingResult resp = service.booking(req);

        assertEquals(BookingOutcome.FAILED, resp.outcome());
        assertEquals("stay_mismatch", resp.supplierErrorCode());
        assertNull(resp.supplierOrderId());
    }

    private static BookingCommand validReq() {
        return DidaBookingRequestShapeTest.req("张/三", "李/四", "13800000000", "2026-10-01", "2026-10-03", 1);
    }

    private static Map<String, String> fullCredentials() {
        Map<String, String> m = new HashMap<>();
        m.put(DidaOfferCredentials.REFERENCE_NO, "18698545882");
        m.put(DidaOfferCredentials.HOTEL_ID, "875535");
        m.put(DidaOfferCredentials.CHECK_IN, "2026-10-01");
        m.put(DidaOfferCredentials.CHECK_OUT, "2026-10-03");
        m.put(DidaOfferCredentials.ROOM_NUM, "1");
        m.put(DidaOfferCredentials.ADULT_COUNT, "1");
        m.put(DidaOfferCredentials.CHILD_AGES, "");
        m.put(DidaOfferCredentials.DECLARED_TOTAL, "212");
        m.put(DidaOfferCredentials.CURRENCY, "CNY");
        return m;
    }
}
