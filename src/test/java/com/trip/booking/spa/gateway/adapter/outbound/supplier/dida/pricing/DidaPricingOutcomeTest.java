package com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.pricing;

import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.ProductRespDTO;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.PriceReq;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.DidaProductKeyDeriver;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.DidaProperties;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.model.DidaError;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.model.DidaHotel;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.model.DidaPriceSearchResponse;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.model.DidaRatePlan;
import com.trip.booking.spa.gateway.application.pricing.PricingResult;
import com.trip.booking.spa.gateway.domain.booking.PricingOutcome;
import com.trip.booking.spa.gateway.domain.product.Occupancy;
import com.trip.booking.spa.platform.util.JsonUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 钉住查价分态与转换口径。夹具是 2026-09-08 生产真实报文。
 *
 * <p>三态的分界是这家供应商最要紧的判据（architecture.md §4.1）：「供应商说没有」要清缓存、
 * 「我们没问出来」不许动缓存。道旅把两者放在<b>同一个 HTTP 200</b> 里，只靠 Error 节点区分，
 * 故本测试逐码钉住。
 */
class DidaPricingOutcomeTest {

    private final DidaPriceServiceImpl service = service();

    @Test
    @DisplayName("真实报文：3 条报价全部出报，总价按分、逐晚价按住期展开")
    void realResponseConvertsToProducts() throws IOException {
        PricingResult result = service.toPricingResult(
                fixture("/dida/price-search-563-1night.json"), request("2026-09-29", "2026-09-30"), "563");

        assertEquals(PricingOutcome.AVAILABLE, result.outcome());
        assertEquals(3, result.products().size());

        ProductRespDTO first = result.products().get(0);
        assertEquals("563", first.getHotelId());
        // 报价码原样透出，身份另立一个字段——身份与令牌永不同字段
        assertEquals("190452504804273758", first.getProductId());
        assertNotNull(first.getProductKey());
        assertEquals(75800, first.getTotalPrice());
        assertEquals("CNY", first.getCurrencyType());
        assertEquals("9566690", first.getRoom().getRoomId());
        assertEquals("标准房(双人床)", first.getRoom().getRoomName());
        assertEquals(1, first.getPriceInfos().size());
        assertEquals("2026-09-29", first.getPriceInfos().get(0).getDate());
        assertEquals(75800, first.getPriceInfos().get(0).getPrice());
        // 逐晚 MealType=1/份数 0：确定无餐（不是 UNKNOWN）
        assertEquals(0, first.getMeal().getCount());
        // IncludedFeeList 已含在 TotalPrice 内，不再当额外税费加减
        assertEquals(0, first.getTotalTaxes());
    }

    @Test
    @DisplayName("同房型不同报价码：productKey 相同——键标识卖法，不标识某一条报价")
    void sameSellingWayShareOneProductKey() throws IOException {
        List<ProductRespDTO> products = service.toPricingResult(
                fixture("/dida/price-search-528-2night.json"), request("2026-09-29", "2026-10-01"), "528")
                .products();

        assertEquals(3, products.size());
        // 前两条：同 RoomTypeID、同餐、同退改、同占用 → 同一个卖法
        assertEquals(products.get(0).getProductKey(), products.get(1).getProductKey());
        // 第三条退改不同（09-06 起即罚全额）→ 不同卖法
        assertTrue(!products.get(0).getProductKey().equals(products.get(2).getProductKey()));
        assertEquals(137800, products.get(0).getTotalPrice());
        assertEquals(2, products.get(0).getPriceInfos().size());
    }

    /**
     * 钉住身份的房型成分是<b>物理房型</b>。艺龙 2026-09-08 (#215) 栽在这上面：它拿销售房型号
     * （RatePlan.RoomTypeId）当身份，与静态目录的物理房型号大多不等，约六成报价在上游按物理号
     * 归组时查不到。道旅只有一个房型号 {@code RatePlan.RoomTypeID}，且 2026-09-08 实测 36 家
     * 225 个 id 全部命中静态内容接口发布的 {@code rooms[].id}（即物理房型主键），本测试防的是
     * 将来有人把它换成别的字段。
     */
    @Test
    @DisplayName("productKey 的房型成分与对外 roomId 同为 RoomTypeID（物理房型）")
    void roomComponentIsThePhysicalRoomId() throws IOException {
        ProductRespDTO first = service.toPricingResult(
                fixture("/dida/price-search-563-1night.json"), request("2026-09-29", "2026-09-30"), "563")
                .products().get(0);

        assertEquals("9566690", first.getIdentity().supplierRoomId());
        assertEquals(first.getRoom().getRoomId(), first.getIdentity().supplierRoomId());
    }

    @Test
    @DisplayName("空 HotelList = 供应商说这家这天没得卖 → 确定无货（缓存该被清）")
    void emptyHotelListIsNoInventory() throws IOException {
        assertEquals(PricingOutcome.NO_INVENTORY, service.toPricingResult(
                fixture("/dida/price-search-empty.json"), request("2026-09-22", "2026-09-23"), "1").outcome());
    }

    @Test
    @DisplayName("酒店在、一条报价都没给 → 同样是确定无货")
    void hotelWithoutRatePlansIsNoInventory() {
        DidaPriceSearchResponse resp = new DidaPriceSearchResponse();
        DidaPriceSearchResponse.Success success = new DidaPriceSearchResponse.Success();
        DidaPriceSearchResponse.PriceDetails details = new DidaPriceSearchResponse.PriceDetails();
        DidaHotel hotel = new DidaHotel();
        hotel.setHotelId(563L);
        hotel.setRatePlanList(List.of());
        details.setHotelList(List.of(hotel));
        success.setPriceDetails(details);
        resp.setSuccess(success);

        assertEquals(PricingOutcome.NO_INVENTORY,
                service.toPricingResult(resp, request("2026-09-29", "2026-09-30"), "563").outcome());
    }

    @Test
    @DisplayName("2005 无库存 / 2029 酒店停售 = 确定无货；2017 账号异常 = 没问出来")
    void errorCodesSplitIntoOutcomes() {
        assertEquals(PricingOutcome.NO_INVENTORY, outcomeOf("2005"));
        assertEquals(PricingOutcome.NO_INVENTORY, outcomeOf("2029"));
        assertEquals(PricingOutcome.NO_INVENTORY, outcomeOf("2006"));
        // 账号/权限/频控/超时：房可能还在，说成无房会误清缓存并劝退旅客
        assertEquals(PricingOutcome.INDETERMINATE, outcomeOf("2017"));
        assertEquals(PricingOutcome.INDETERMINATE, outcomeOf("2022"));
        assertEquals(PricingOutcome.INDETERMINATE, outcomeOf("2009"));
        // 表外新码同样不许归并成"无货"
        assertEquals(PricingOutcome.INDETERMINATE, outcomeOf("9999"));
    }

    @Test
    @DisplayName("非即时确认的报价被丢掉；全丢时回不确定，不许说成无房")
    void onRequestPlansAreDroppedNotSold() {
        DidaPriceSearchResponse resp = withPlans(onRequestPlan());
        PricingResult result = service.toPricingResult(resp, request("2026-09-29", "2026-09-30"), "563");
        // 房其实还在（只是要二次确认），说成 NO_INVENTORY 会让上游据此劝退旅客
        assertEquals(PricingOutcome.INDETERMINATE, result.outcome());
    }

    @Test
    @DisplayName("逐晚价条数与住期不符（缺一天）→ 丢弃，不按缺天数报价")
    void missingNightIsDropped() throws IOException {
        // 一晚的报文按两晚住期解析：条数不符，全部丢弃
        PricingResult result = service.toPricingResult(
                fixture("/dida/price-search-563-1night.json"), request("2026-09-29", "2026-10-01"), "563");
        assertEquals(PricingOutcome.INDETERMINATE, result.outcome());
        assertTrue(result.products().isEmpty());
    }

    /**
     * 用 9（渠道 8 = PKG(Room&amp;Ticket)）而不是 7：7 自 2026-09-09 起按官方餐型表判为早+晚，
     * 已不再是"判不出"的例子。9 是表里有、却映射不进本仓早/午/晚模型的那一类。
     */
    @Test
    @DisplayName("餐食判不出的报价照常出报，只是 identity 带 UNKNOWN（不进目录）")
    void unknownMealStillSells() {
        DidaRatePlan plan = plainPlan();
        plan.getPriceList().get(0).setMealType(9);
        plan.getPriceList().get(0).setMealAmount(2);
        PricingResult result = service.toPricingResult(withPlans(plan), request("2026-09-29", "2026-09-30"), "563");

        assertEquals(PricingOutcome.AVAILABLE, result.outcome());
        assertEquals("UNKNOWN", result.products().get(0).getIdentity().mealSignature());
        assertNull(result.products().get(0).getMeal());
    }

    @Test
    @DisplayName("早+晚的报价必须带上晚餐进 identity——落成仅含早就是卖错")
    void breakfastAndDinnerReachesIdentity() {
        DidaRatePlan plan = plainPlan();
        plan.getPriceList().get(0).setMealType(7);
        plan.getPriceList().get(0).setMealAmount(2);
        PricingResult result = service.toPricingResult(withPlans(plan), request("2026-09-29", "2026-09-30"), "563");

        assertEquals("B1L0D1", result.products().get(0).getIdentity().mealSignature());
        assertEquals(2, result.products().get(0).getMeal().getDinnerCount());
    }

    private PricingOutcome outcomeOf(String code) {
        DidaPriceSearchResponse resp = new DidaPriceSearchResponse();
        DidaError error = new DidaError();
        error.setCode(code);
        error.setMessage("test");
        resp.setError(error);
        return service.toPricingResult(resp, request("2026-09-29", "2026-09-30"), "563").outcome();
    }

    private static DidaPriceSearchResponse withPlans(DidaRatePlan... plans) {
        DidaPriceSearchResponse resp = new DidaPriceSearchResponse();
        DidaPriceSearchResponse.Success success = new DidaPriceSearchResponse.Success();
        DidaPriceSearchResponse.PriceDetails details = new DidaPriceSearchResponse.PriceDetails();
        DidaHotel hotel = new DidaHotel();
        hotel.setHotelId(563L);
        hotel.setRatePlanList(List.of(plans));
        details.setHotelList(List.of(hotel));
        success.setPriceDetails(details);
        resp.setSuccess(success);
        return resp;
    }

    private static DidaRatePlan plainPlan() {
        DidaRatePlan plan = new DidaRatePlan();
        plan.setRoomTypeId(9566690L);
        plan.setRatePlanId("190452504804273758");
        plan.setRoomName("Standard Double Room");
        plan.setCurrency("CNY");
        plan.setTotalPrice(new java.math.BigDecimal("758"));
        plan.setIsOnRequest(false);
        com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.model.DidaPriceItem night =
                new com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.model.DidaPriceItem();
        night.setPrice(new java.math.BigDecimal("758"));
        night.setStayDate("2026-09-29 00:00:00");
        night.setMealType(1);
        night.setMealAmount(0);
        plan.setPriceList(new java.util.ArrayList<>(List.of(night)));
        return plan;
    }

    private static DidaRatePlan onRequestPlan() {
        DidaRatePlan plan = plainPlan();
        plan.setIsOnRequest(true);
        return plan;
    }

    private static PriceReq request(String checkIn, String checkOut) {
        PriceReq req = PriceReq.builder().checkIn(checkIn).checkout(checkOut).roomNum(1)
                .adultNum(2).childNum(0).childAges(List.of()).build();
        req.setOccupancies(Occupancy.perRoom(1, 2, 0, List.of()));
        return req;
    }

    private static DidaPriceSearchResponse fixture(String resource) throws IOException {
        try (InputStream in = DidaPricingOutcomeTest.class.getResourceAsStream(resource)) {
            assertNotNull(in, "夹具缺失: " + resource);
            return JsonUtils.readValue(new String(in.readAllBytes(), StandardCharsets.UTF_8),
                    DidaPriceSearchResponse.class);
        }
    }

    private static DidaPriceServiceImpl service() {
        DidaProperties properties = new DidaProperties();
        properties.setClientId("BJNSW");
        properties.setLicenseKey("BJNSW");
        DidaProductKeyDeriver deriver = new DidaProductKeyDeriver();
        deriver.setProperties(properties);
        DidaPriceServiceImpl impl = new DidaPriceServiceImpl();
        ReflectionTestUtils.setField(impl, "properties", properties);
        ReflectionTestUtils.setField(impl, "productKeyDeriver", deriver);
        return impl;
    }
}
