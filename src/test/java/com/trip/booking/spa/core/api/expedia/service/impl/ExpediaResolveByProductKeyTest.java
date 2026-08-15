package com.trip.booking.spa.core.api.expedia.service.impl;

import com.trip.booking.spa.core.api.common.identity.CancelClass;
import com.trip.booking.spa.core.api.common.identity.MealSignature;
import com.trip.booking.spa.core.api.common.identity.ProductKeyFactory;
import com.trip.booking.spa.core.api.expedia.bean.response.QueryPriceResponse;
import com.trip.booking.spa.core.api.expedia.config.ExpediaContractProfile;
import com.trip.booking.spa.core.api.expedia.config.ExpediaRapidProperties;
import com.trip.booking.spa.core.api.request.CheckPriceReq;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 钉住 resolve ② 的行为（docs/product-identity.md §3）：令牌死后按 productKey 换票。
 *
 * <p>三条最要紧的：<b>开关关闭时行为与旧实现完全一致</b>（返回 null → RATE_DEAD）；
 * <b>键不匹配绝不凑数</b>（R-3.2 硬门，cursor 订单 49046202 含早错配的教训）；
 * <b>超容差拒绝</b>（R-3.3，防静默涨价成交）。
 */
class ExpediaResolveByProductKeyTest {

    private static final String ACCOUNT = "B2B_SA_PKG_MOD_AGENT";
    private static final String HOTEL = "11775754";
    private static final String ROOM = "230410389";
    private static final String OCCUPANCY = "2";

    /** 与被测代码同口径的期望键：无餐食、不可退、2 人 */
    private static final String KEY = ProductKeyFactory.derive(10005, ACCOUNT, HOTEL, ROOM,
            MealSignature.known(false, false, false), CancelClass.NON_REFUNDABLE, OCCUPANCY);

    private static ExpediaPriceServiceImpl service(boolean resolveEnabled) {
        ExpediaPriceServiceImpl service = new ExpediaPriceServiceImpl();
        ExpediaRapidProperties properties = new ExpediaRapidProperties();
        properties.setResolveEnabled(resolveEnabled);
        properties.setResolvePriceTolerance(0.02);
        ExpediaContractProfile profile = new ExpediaContractProfile();
        ReflectionTestUtils.setField(profile, "partnerPointOfSale", ACCOUNT);
        ReflectionTestUtils.setField(service, "contractProfile", profile);
        ReflectionTestUtils.setField(service, "rapidProperties", properties);
        return service;
    }

    /** 一条不可退、无餐食的现货报价，价格单位元（如 "100.00" → 上游口径 10000 分） */
    private static QueryPriceResponse.Rates rate(String rateId, String priceYuan) {
        QueryPriceResponse.CurrencyInfo money = new QueryPriceResponse.CurrencyInfo();
        money.setValue(priceYuan);
        money.setCurrency("CNY");
        QueryPriceResponse.AmountInfo inclusive = new QueryPriceResponse.AmountInfo();
        inclusive.setRequest_currency(money);
        QueryPriceResponse.Totals totals = new QueryPriceResponse.Totals();
        totals.setInclusive(inclusive);
        QueryPriceResponse.Occupancy_pricing pricing = new QueryPriceResponse.Occupancy_pricing();
        pricing.setTotals(totals);

        QueryPriceResponse.Rates rate = new QueryPriceResponse.Rates();
        rate.setId(rateId);
        rate.setOccupancy_pricing(Map.of(OCCUPANCY, pricing));
        rate.setNonrefundable_date_ranges(List.of(new QueryPriceResponse.CancelPolicy()));
        return rate;
    }

    private static QueryPriceResponse response(QueryPriceResponse.Rates... rates) {
        QueryPriceResponse.Rooms room = new QueryPriceResponse.Rooms();
        room.setId(ROOM);
        room.setRates(List.of(rates));
        QueryPriceResponse.HotelPrice hotelPrice = new QueryPriceResponse.HotelPrice();
        hotelPrice.setProperty_id(HOTEL);
        hotelPrice.setRooms(List.of(room));
        QueryPriceResponse resp = new QueryPriceResponse();
        resp.setHotelPrices(List.of(hotelPrice));
        return resp;
    }

    private static CheckPriceReq request(String productKey, int seenPriceCents) {
        return CheckPriceReq.builder()
                .supplierId(10005).sHotelId(HOTEL).sProductId("276999999")
                .productKey(productKey)
                .checkIn("2026-10-04").checkOut("2026-10-08")
                .roomNum(1).adultCount(2).totalPrice(seenPriceCents)
                .build();
    }

    /** 令牌死、键匹配、价格在容差内：换票成功，且多张票选最便宜的 */
    @Test
    void resolvesToCheapestEquivalent() {
        QueryPriceResponse.Rates resolved = service(true).tryResolveByProductKey(
                response(rate("A", "101.00"), rate("B", "99.00")), request(KEY, 10000), OCCUPANCY);

        assertNotNull(resolved);
        assertEquals("B", resolved.getId());
    }

    /** 开关关闭：行为与旧实现完全一致——不换票，让上游拿到 RATE_DEAD */
    @Test
    void disabledFlagKeepsLegacyBehaviour() {
        assertNull(service(false).tryResolveByProductKey(
                response(rate("A", "99.00")), request(KEY, 10000), OCCUPANCY));
    }

    /** 上游没带 productKey：没有身份就没有等价的定义，不猜 */
    @Test
    void missingProductKeyRefuses() {
        assertNull(service(true).tryResolveByProductKey(
                response(rate("A", "99.00")), request(null, 10000), OCCUPANCY));
        assertNull(service(true).tryResolveByProductKey(
                response(rate("A", "99.00")), request(" ", 10000), OCCUPANCY));
    }

    /** 键不匹配（哪怕只差一个成分）：绝不凑数——硬门 R-3.2 */
    @Test
    void mismatchedKeyRefuses() {
        String otherKey = ProductKeyFactory.derive(10005, ACCOUNT, HOTEL, ROOM,
                MealSignature.known(true, false, false), CancelClass.NON_REFUNDABLE, OCCUPANCY);

        assertNull(service(true).tryResolveByProductKey(
                response(rate("A", "99.00")), request(otherKey, 10000), OCCUPANCY));
    }

    /** 等价票存在但超容差（100 元展示价 vs 103 元现价 > 2%）：拒绝，防静默涨价成交 */
    @Test
    void overToleranceRefuses() {
        assertNull(service(true).tryResolveByProductKey(
                response(rate("A", "103.00")), request(KEY, 10000), OCCUPANCY));
    }

    /** 该占用没有报价的候选直接跳过，不得抛异常 */
    @Test
    void candidateWithoutPricingIsSkipped() {
        QueryPriceResponse.Rates broken = new QueryPriceResponse.Rates();
        broken.setId("X");
        broken.setNonrefundable_date_ranges(List.of(new QueryPriceResponse.CancelPolicy()));

        QueryPriceResponse.Rates resolved = service(true).tryResolveByProductKey(
                response(broken, rate("B", "99.00")), request(KEY, 10000), OCCUPANCY);

        assertNotNull(resolved);
        assertEquals("B", resolved.getId());
    }
}
