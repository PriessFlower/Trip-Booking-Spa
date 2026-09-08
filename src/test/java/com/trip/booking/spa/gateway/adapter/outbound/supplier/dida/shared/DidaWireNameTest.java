package com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared;

import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.model.DidaHotel;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.model.DidaPriceConfirmResponse;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.model.DidaPriceItem;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.model.DidaPriceSearchResponse;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.model.DidaRatePlan;
import com.trip.booking.spa.platform.util.JsonUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 钉住道旅报文的<b>线上字段名</b>（§4.2.3：wire 字面量是供应商契约，不是本仓词汇）。
 *
 * <p><b>为什么必须读原始 JSON</b>：字段名写错时 Jackson 不报错，只是值恒为 null——
 * 本仓已被这种静默坑过（艺龙 {@code mealCopyWriting} 被当本仓词汇改名，餐食恒 null、
 * productKey 全量轮转、67,709 行档案失联）。夹具是 2026-09-08 生产真实报文
 * （腾讯云 trip-offline 直打 api.didatravel.com，酒店 563/528，住期 09-29）。
 */
class DidaWireNameTest {

    @Test
    @DisplayName("pricesearch：每个我方读取的字段都必须从真实报文里读出非空值")
    void priceSearchFieldsParse() throws IOException {
        DidaPriceSearchResponse resp = read("/dida/price-search-563-1night.json", DidaPriceSearchResponse.class);
        assertTrue(resp.isSucc());
        assertFalse(resp.isEmptyResult());

        DidaHotel hotel = resp.firstHotel();
        assertEquals(563L, hotel.getHotelId());
        assertEquals(3, hotel.getRatePlanList().size());

        DidaRatePlan plan = hotel.getRatePlanList().get(0);
        assertEquals(9566690L, plan.getRoomTypeId());
        assertEquals("190452504804273758", plan.getRatePlanId());
        assertEquals("标准房(双人床)", plan.getRoomNameCn());
        assertEquals("Standard Double Room", plan.getRoomName());
        assertEquals("Standard Double Room", plan.getRatePlanName());
        assertEquals("CNY", plan.getCurrency());
        assertEquals(0, plan.getTotalPrice().compareTo(new java.math.BigDecimal("758")));
        assertEquals(9, plan.getInventoryCount());
        assertEquals(2, plan.getBedType());
        assertEquals(1, plan.getBreakfastType());
        assertFalse(plan.onRequest());

        DidaPriceItem night = plan.getPriceList().get(0);
        assertEquals(0, night.getPrice().compareTo(new java.math.BigDecimal("758")));
        assertEquals("2026-09-29 00:00:00", night.getStayDate());
        assertEquals(1, night.getMealType());
        assertEquals(0, night.getMealAmount());

        assertEquals("2026-09-25T00:00:00+08:00",
                plan.getRatePlanCancellationPolicyList().get(0).getFromDate());
        assertEquals(0, plan.getRatePlanCancellationPolicyList().get(0).getAmount()
                .compareTo(new java.math.BigDecimal("758")));

        // 税费挂在第三条报价上（IncludedFeeList 已含在 TotalPrice 内，不再运算）
        assertEquals("TAXESANDFEES",
                hotel.getRatePlanList().get(2).getIncludedFeeList().get(0).getFeeTypeName());
    }

    @Test
    @DisplayName("两晚报文：逐晚价按住期展开，条数即晚数")
    void twoNightPriceListParses() throws IOException {
        DidaRatePlan plan = read("/dida/price-search-528-2night.json", DidaPriceSearchResponse.class)
                .firstHotel().getRatePlanList().get(0);
        assertEquals(2, plan.getPriceList().size());
        assertEquals("2026-09-30 00:00:00", plan.getPriceList().get(1).getStayDate());
        assertEquals(0, plan.getTotalPrice().compareTo(new java.math.BigDecimal("1378")));
    }

    @Test
    @DisplayName("PriceConfirm：ReferenceNo 与酒店级 CancellationPolicyList 必须读得出")
    void priceConfirmFieldsParse() throws IOException {
        DidaPriceConfirmResponse resp = read("/dida/price-confirm-563-prebook.json", DidaPriceConfirmResponse.class);
        assertTrue(resp.isSucc());
        assertEquals("18643313476", resp.referenceNo());
        assertNotNull(resp.firstRatePlan());
        assertEquals("190452504804273758", resp.firstRatePlan().getRatePlanId());
        assertEquals("2026-09-25T00:00:00+08:00",
                resp.firstHotel().getCancellationPolicyList().get(0).getFromDate());
    }

    @Test
    @DisplayName("错误信封：Code/Message 读得出，且 isSucc=false")
    void errorEnvelopeParses() throws IOException {
        DidaPriceSearchResponse resp = read("/dida/price-search-error-2017.json", DidaPriceSearchResponse.class);
        assertFalse(resp.isSucc());
        assertEquals("2017", resp.errorCode());
        assertEquals("Invalid LicenseKey WRONGKEY", resp.errorMessage());
    }

    /** 不存在的酒店 id：道旅回 Success + 空 HotelList，不报错（2026-09-08 实测） */
    @Test
    @DisplayName("空 HotelList 是成功响应，不是错误")
    void emptyHotelListIsSuccess() throws IOException {
        DidaPriceSearchResponse resp = read("/dida/price-search-empty.json", DidaPriceSearchResponse.class);
        assertTrue(resp.isSucc());
        assertTrue(resp.isEmptyResult());
    }

    private static <T> T read(String resource, Class<T> type) throws IOException {
        try (InputStream in = DidaWireNameTest.class.getResourceAsStream(resource)) {
            assertNotNull(in, "夹具缺失: " + resource);
            return JsonUtils.readValue(new String(in.readAllBytes(), StandardCharsets.UTF_8), type);
        }
    }
}
