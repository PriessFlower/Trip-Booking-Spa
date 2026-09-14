package com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared;

import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.model.DidaBookingCancelConfirmResponse;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.model.DidaBookingCancelResponse;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.model.DidaBookingConfirmResponse;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.model.DidaBookingDetails;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.model.DidaBookingSearchResponse;
import com.trip.booking.spa.platform.util.JsonUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 钉住道旅<b>订单族</b>报文的线上字段名（§4.2.3/§4.2.6，理由见 {@link DidaWireNameTest}）。
 *
 * <p>夹具全部是生产账号（ClientID 与 cursor 共用）的真实报文，两处来源：
 * <ul>
 *   <li>tg-trip-cursor {@code DidaTravelTest} 注释里留存的 2025-11-27/28 与 2026-01-31 报文
 *       （酒店 5287，BookingID 15801485798；入住人姓名本就只有缩写）</li>
 *   <li>cursor 生产库 {@code supplier_request_log} 2026-09-10~14 的行（姓名/电话/邮箱已替换为 ***，
 *       其余字节原样）</li>
 * </ul>
 * 本仓自己尚未下过道旅真单（闸 dida.booking-enabled 默认关）——首单跑通后按同名规则补本仓夹具。
 */
public class DidaOrderWireNameTest {

    @Test
    @DisplayName("HotelBookingConfirm 成功：BookingID/Status/总价/币种/酒店/退改都从真实报文读得出")
    void bookingConfirmSuccessParses() throws IOException {
        DidaBookingConfirmResponse resp = read("/dida/booking-confirm-real-20251128.json", DidaBookingConfirmResponse.class);
        assertTrue(resp.isSucc());
        DidaBookingDetails d = resp.bookingDetails();
        assertNotNull(d);
        assertEquals("15801485798", d.getBookingId());
        assertEquals(2, d.getStatus());
        assertTrue(d.isFinal());
        assertEquals("2025-11-28 18:26:28.510", d.getOrderDate());
        assertEquals(1, d.getNumOfRooms());
        assertEquals(0, d.getTotalPrice().compareTo(new BigDecimal("6849")));
        assertEquals("CNY", d.currency());
        assertEquals("4a218db95273469591f43023a4dd87c4", d.getClientReference());
        assertNull(d.getConfirmationCode(), "该单酒店未回 HCN，字段缺席就该是 null 而不是空串");
        assertEquals(5287L, d.getHotel().getHotelId());
        assertEquals(11529484L, d.getHotel().getRatePlanList().get(0).getRoomTypeId());
        assertEquals("2025-11-26T00:00:00+08:00", d.getHotel().getCancellationPolicyList().get(0).getFromDate());
    }

    @Test
    @DisplayName("HotelBookingConfirm 成功（2026-09-13 生产）：含 RoomOccupancy.ChildAgeDetails 空数组形态")
    void bookingConfirmSuccessProdParses() throws IOException {
        DidaBookingConfirmResponse resp = read("/dida/booking-confirm-real-20260913.json", DidaBookingConfirmResponse.class);
        assertTrue(resp.isSucc());
        assertEquals("18698545882", resp.bookingDetails().getBookingId());
        assertEquals(2, resp.bookingDetails().getStatus());
        assertEquals("2609132116452e66310fe7ac", resp.bookingDetails().getClientReference());
    }

    @Test
    @DisplayName("HotelBookingConfirm 错误信封：Code/Message 读得出，且没有 BookingID")
    void bookingConfirmErrorParses() throws IOException {
        DidaBookingConfirmResponse r3005 = read("/dida/booking-confirm-error-3005-real.json", DidaBookingConfirmResponse.class);
        assertFalse(r3005.isSucc());
        assertEquals("3005", r3005.errorCode());
        assertEquals("Incorrect occupancy information specified in one room.", r3005.errorMessage());
        assertNull(r3005.getError().getBookingId());

        DidaBookingConfirmResponse minus2 = read("/dida/booking-confirm-error-minus2-real.json", DidaBookingConfirmResponse.class);
        assertFalse(minus2.isSucc());
        assertEquals("-2", minus2.errorCode());
    }

    @Test
    @DisplayName("HotelBookingSearch：单笔命中 → single() 给出；Status 2/3/4/5 各态读得出")
    void bookingSearchParses() throws IOException {
        DidaBookingSearchResponse s2 = read("/dida/booking-search-status2-real-20251128.json", DidaBookingSearchResponse.class);
        assertTrue(s2.isSucc());
        assertEquals(1, s2.bookings().size());
        assertEquals("15801485798", s2.single().getBookingId());
        assertEquals(2, s2.single().getStatus());
        assertEquals("CNY", s2.single().currency());
        assertEquals(0, s2.single().getTotalPrice().compareTo(new BigDecimal("6849")));

        assertEquals(3, read("/dida/booking-search-status3-real-20251128.json", DidaBookingSearchResponse.class).single().getStatus());
        assertEquals(4, read("/dida/booking-search-status4-real-20260910.json", DidaBookingSearchResponse.class).single().getStatus());

        DidaBookingDetails s5 = read("/dida/booking-search-status5-real-20260913.json", DidaBookingSearchResponse.class).single();
        assertEquals(5, s5.getStatus());
        assertFalse(s5.isFinal());
        assertEquals("18698545882", s5.getBookingId());
        assertEquals("2026-09-13 21:16:46.862", s5.getOrderDate());
    }

    @Test
    @DisplayName("HotelBookingSearch：酒店确认号 ConfirmationCode 读得出")
    void bookingSearchConfirmationCodeParses() throws IOException {
        DidaBookingDetails d = read("/dida/booking-search-hcn-real-20260914.json", DidaBookingSearchResponse.class).single();
        assertEquals("18577514927", d.getBookingId());
        assertEquals("78112572", d.getConfirmationCode());
        assertEquals(2, d.getStatus());
    }

    /** 2026-09-12 生产：按 ClientReference 紧跟下单之后查，回空列表——它是成功信封，不是错误 */
    @Test
    @DisplayName("HotelBookingSearch 空列表：isSucc=true、bookings 为空、single() 为 null")
    void bookingSearchEmptyIsSuccessEnvelope() throws IOException {
        DidaBookingSearchResponse resp = read("/dida/booking-search-empty-real-20260912.json", DidaBookingSearchResponse.class);
        assertTrue(resp.isSucc());
        assertTrue(resp.bookings().isEmpty());
        assertNull(resp.single());
        assertFalse(resp.isEmptyResult(), "订单族不进 NO_INVENTORY 那一档计数");
    }

    @Test
    @DisplayName("HotelBookingCancel（预取消）：ConfirmID/Amount/Currency/BookingID 读得出；3018 是错误信封")
    void preCancelParses() throws IOException {
        DidaBookingCancelResponse resp = read("/dida/booking-cancel-real-20251128.json", DidaBookingCancelResponse.class);
        assertTrue(resp.isSucc());
        assertEquals("DCC251128184502074", resp.confirmId());
        assertEquals("15801485798", resp.bookingId());
        assertEquals("CNY", resp.currency());
        assertEquals(0, resp.amount().compareTo(new BigDecimal("6849")));

        DidaBookingCancelResponse free = read("/dida/booking-cancel-free-real-20260910.json", DidaBookingCancelResponse.class);
        assertEquals(0, free.amount().signum());

        DidaBookingCancelResponse already = read("/dida/booking-cancel-error-3018-real-20260910.json", DidaBookingCancelResponse.class);
        assertFalse(already.isSucc());
        assertEquals("3018", already.errorCode());
        assertEquals("Booking is already canceled.", already.errorMessage());
    }

    @Test
    @DisplayName("HotelBookingCancelConfirm 成功是空对象 {\"Success\":{}}：必须判成功而不是判空")
    void cancelConfirmEmptySuccessParses() throws IOException {
        DidaBookingCancelConfirmResponse resp = read("/dida/booking-cancel-confirm-real-20260131.json", DidaBookingCancelConfirmResponse.class);
        assertTrue(resp.isSucc());
        assertNull(resp.errorCode());
    }

    public static <T> T read(String resource, Class<T> type) throws IOException {
        try (InputStream in = DidaOrderWireNameTest.class.getResourceAsStream(resource)) {
            assertNotNull(in, "夹具缺失: " + resource);
            return JsonUtils.readValue(new String(in.readAllBytes(), StandardCharsets.UTF_8), type);
        }
    }
}
