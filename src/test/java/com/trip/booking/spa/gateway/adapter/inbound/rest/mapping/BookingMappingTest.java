package com.trip.booking.spa.gateway.adapter.inbound.rest.mapping;

import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.BookingRespDTO;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.BookingReq;
import com.trip.booking.spa.gateway.domain.booking.BookingCommand;
import com.trip.booking.spa.gateway.domain.booking.BookingOutcome;
import com.trip.booking.spa.gateway.domain.booking.BookingResult;
import com.trip.booking.spa.platform.util.JsonUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 钉住下单的对外形状：这一层是 JSON 契约与领域模型之间唯一的翻译处。
 *
 * <p>重点在 {@code sOrderId} 那个线上字段名——Jackson 会把 {@code sOrderId} 序列化成
 * {@code sorderId}，而入站方向大小写不敏感仍认，两个方向不对称，只看 Java 字段名发现不了
 * （2026-08-11 沙箱实测）。领域侧用 {@code supplierOrderId} 这样的正常名字，线上名由本层钉住。
 */
class BookingMappingTest {

    private static BookingReq req() {
        return BookingReq.builder()
                .supplierId(10010).sHotelId("H-1").sProductId("P-1").orderId("O-1")
                .personName("ZHANG SAN").contactName("LI SI").contactPhone("13800000000")
                .checkIn("2026-10-10").checkOut("2026-10-11")
                .roomNum(2).totalPrice(20000).settlePrice(18000)
                .offerId("OFFER-1")
                .build();
    }

    @Test
    @DisplayName("入站：十三个字段逐一过桥，一个都不许漏")
    void commandCarriesEveryField() {
        BookingCommand c = BookingMapping.toCommand(req());

        assertEquals(10010, c.supplierId());
        assertEquals("O-1", c.orderId());
        assertEquals("OFFER-1", c.offerId());
        assertEquals("H-1", c.supplierHotelId());
        assertEquals("P-1", c.supplierProductId());
        assertEquals("ZHANG SAN", c.personName());
        assertEquals("LI SI", c.contactName());
        assertEquals("13800000000", c.contactPhone());
        assertEquals("2026-10-10", c.checkIn());
        assertEquals("2026-10-11", c.checkOut());
        assertEquals(2, c.roomNum());
        assertEquals(20000, c.totalPrice());
        assertEquals(18000, c.settlePrice());
    }

    @Test
    @DisplayName("出站：成功态带供应商单号与确认号")
    void successIsMapped() {
        BookingRespDTO dto = BookingMapping.toDto(
                BookingResult.success("O-1", "S-1", "C-1", "下单成功"));

        assertEquals(BookingOutcome.SUCCESS, dto.getOutcome());
        assertEquals("O-1", dto.getOrderId());
        assertEquals("S-1", dto.getSOrderId());
        assertEquals("C-1", dto.getSConfirmationNumber());
        assertEquals("下单成功", dto.getOrderDesc());
        assertNull(dto.getSupplierErrorCode());
    }

    @Test
    @DisplayName("出站：失败态带原生错误码，且没有供应商单号——没下成单就不该有单号")
    void failureIsMapped() {
        BookingRespDTO dto = BookingMapping.toDto(
                BookingResult.failed("O-1", "sold_out", "已售罄", "已售罄"));

        assertEquals(BookingOutcome.FAILED, dto.getOutcome());
        assertEquals("sold_out", dto.getSupplierErrorCode());
        assertEquals("已售罄", dto.getSupplierErrorMessage());
        assertNull(dto.getSOrderId());
    }

    @Test
    @DisplayName("出站：不确定态不得带任何像是成功的痕迹")
    void unknownIsMapped() {
        BookingRespDTO dto = BookingMapping.toDto(
                BookingResult.unknown("O-1", "结果不确定，请查单确证"));

        assertEquals(BookingOutcome.UNKNOWN, dto.getOutcome());
        assertNull(dto.getSOrderId(), "UNKNOWN 时没有单号，上游必须去查单");
        assertNull(dto.getSConfirmationNumber());
    }

    /**
     * 线上字段名必须是 {@code sOrderId}，不是 Jackson 默认推断的 {@code sorderId}。
     * 这条只有读序列化后的 JSON 才验得到——{@code new} 出对象再取字段是验不出来的。
     */
    @Test
    @DisplayName("线上字段名钉死：序列化后必须出现 sOrderId 与 sConfirmationNumber")
    void wireFieldNamesArePinned() {
        String json = JsonUtils.writeObject2Json(BookingMapping.toDto(
                BookingResult.success("O-1", "S-1", "C-1", null)));

        assertTrue(json.contains("\"sOrderId\""), "实际序列化结果：" + json);
        assertTrue(json.contains("\"sConfirmationNumber\""), "实际序列化结果：" + json);
        assertTrue(!json.contains("\"sorderId\""), "Jackson 默认推断会压平成 sorderId：" + json);
    }
}
