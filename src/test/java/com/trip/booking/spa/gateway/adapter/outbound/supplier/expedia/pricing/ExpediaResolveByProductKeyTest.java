package com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.pricing;

import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.CheckPriceRespDTO;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.CheckPriceReq;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.checkprice.ExpediaCheckPriceServiceImpl;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.shared.ExpediaContractProfile;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.shared.ExpediaProductKeyDeriver;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.shared.ExpediaRapidProperties;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.shared.model.response.QueryPriceResponse;
import com.trip.booking.spa.gateway.application.checkprice.LiveStock;
import com.trip.booking.spa.gateway.domain.booking.CheckPriceOutcome;
import com.trip.booking.spa.gateway.domain.booking.VerifyLevel;
import com.trip.booking.spa.gateway.domain.product.CancelClass;
import com.trip.booking.spa.gateway.domain.product.MealSignature;
import com.trip.booking.spa.gateway.domain.product.ProductKeyFactory;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 钉住 Expedia 这一家在 resolve ②（docs/product-identity.md §3）里的读法：候选怎么算、键怎么派生、
 * 价格取什么口径。换票的选票与容差门在模板（{@code CheckPriceFlowTest}），这里只把现货喂进
 * 验价入口、把 validate 换成回显所选票，看最终选到哪一张。
 *
 * <p>三条最要紧的：<b>开关关闭时不换票</b>（RATE_DEAD）；<b>键不匹配绝不凑数</b>
 * （R-3.2 硬门，cursor 订单 49046202 含早错配的教训）；<b>超容差拒绝</b>（R-3.3，防静默涨价成交）。
 */
class ExpediaResolveByProductKeyTest {

    private static final String ACCOUNT = "B2B_SA_PKG_MOD_AGENT";
    private static final String HOTEL = "11775754";
    private static final String ROOM = "230410389";
    private static final String OCCUPANCY = "2";

    /** 与被测代码同口径的期望键：无餐食、不可退、2 人 */
    private static final String KEY = ProductKeyFactory.derive(10005, ACCOUNT, HOTEL, ROOM,
            MealSignature.known(false, false, false), CancelClass.NON_REFUNDABLE, OCCUPANCY);

    /** 验价入口：现取换成给定现货，床型自检放行，validate 回显所选票的 rate.id */
    private static ExpediaCheckPriceServiceImpl entry(boolean resolveEnabled, QueryPriceResponse stock) {
        ExpediaPriceServiceImpl service = new ExpediaPriceServiceImpl();
        ExpediaRapidProperties properties = new ExpediaRapidProperties();
        properties.setResolveEnabled(resolveEnabled);
        properties.setResolvePriceTolerance(0.02);
        ExpediaContractProfile profile = new ExpediaContractProfile();
        ReflectionTestUtils.setField(profile, "partnerPointOfSale", ACCOUNT);
        ReflectionTestUtils.setField(service, "contractProfile", profile);
        ReflectionTestUtils.setField(service, "rapidProperties", properties);
        ExpediaProductKeyDeriver deriver = new ExpediaProductKeyDeriver();
        deriver.setContractProfile(profile);
        ReflectionTestUtils.setField(service, "productKeyDeriver", deriver);

        ExpediaCheckPriceServiceImpl entry = new ExpediaCheckPriceServiceImpl() {
            @Override
            protected LiveStock<QueryPriceResponse> fetchLiveStock(CheckPriceReq request, String salesEnvironment) {
                return LiveStock.of(stock);
            }

            @Override
            protected CheckPriceRespDTO inspect(QueryPriceResponse.Rates rate, QueryPriceResponse data, CheckPriceReq request) {
                return null;
            }

            @Override
            protected CheckPriceRespDTO validate(QueryPriceResponse.Rates rate, QueryPriceResponse data, CheckPriceReq request) {
                return CheckPriceRespDTO.builder().outcome(CheckPriceOutcome.BOOKABLE).message(rate.getId())
                        .offerId("offer").offerTtlSeconds(600L).build();
            }
        };
        ReflectionTestUtils.setField(entry, "expediaPriceService", service);
        ReflectionTestUtils.setField(entry, "rapidProperties", properties);
        return entry;
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

    /** 所点令牌 276999999 不在现货里——每个用例都是令牌已死的场景 */
    private static CheckPriceReq request(String productKey, int seenPriceCents) {
        return CheckPriceReq.builder()
                .supplierId(10005).sHotelId(HOTEL).sProductId("276999999")
                .productKey(productKey).priceFlag("hotel_only")
                .checkIn("2026-10-04").checkOut("2026-10-08")
                .roomNum(1).adultCount(2).seenPrice(seenPriceCents)
                .verifyLevel(VerifyLevel.BOOKABLE)
                .build();
    }

    /** 令牌死、键匹配、价格在容差内：换票成功，且多张票选最便宜的 */
    @Test
    void resolvesToCheapestEquivalent() {
        CheckPriceRespDTO resp = entry(true, response(rate("A", "101.00"), rate("B", "99.00")))
                .checkPrice(request(KEY, 10000));

        assertEquals(CheckPriceOutcome.BOOKABLE, resp.getOutcome());
        assertEquals("B", resp.getMessage());
    }

    /** 开关关闭：不换票，让上游拿到 RATE_DEAD */
    @Test
    void disabledFlagKeepsLegacyBehaviour() {
        assertEquals(CheckPriceOutcome.RATE_DEAD,
                entry(false, response(rate("A", "99.00"))).checkPrice(request(KEY, 10000)).getOutcome());
    }

    /** 上游没带 productKey：没有身份就没有等价的定义，不猜 */
    @Test
    void missingProductKeyRefuses() {
        assertEquals(CheckPriceOutcome.RATE_DEAD,
                entry(true, response(rate("A", "99.00"))).checkPrice(request(null, 10000)).getOutcome());
        assertEquals(CheckPriceOutcome.RATE_DEAD,
                entry(true, response(rate("A", "99.00"))).checkPrice(request(" ", 10000)).getOutcome());
    }

    /** 键不匹配（哪怕只差一个成分）：绝不凑数——硬门 R-3.2 */
    @Test
    void mismatchedKeyRefuses() {
        String otherKey = ProductKeyFactory.derive(10005, ACCOUNT, HOTEL, ROOM,
                MealSignature.known(true, false, false), CancelClass.NON_REFUNDABLE, OCCUPANCY);

        assertEquals(CheckPriceOutcome.RATE_DEAD,
                entry(true, response(rate("A", "99.00"))).checkPrice(request(otherKey, 10000)).getOutcome());
    }

    /** 等价票存在但超容差（100 元展示价 vs 103 元现价 > 2%）：拒绝，防静默涨价成交 */
    @Test
    void overToleranceRefuses() {
        assertEquals(CheckPriceOutcome.RATE_DEAD,
                entry(true, response(rate("A", "103.00"))).checkPrice(request(KEY, 10000)).getOutcome());
    }

    /** 该占用没有报价的候选直接跳过，不得抛异常 */
    @Test
    void candidateWithoutPricingIsSkipped() {
        QueryPriceResponse.Rates broken = new QueryPriceResponse.Rates();
        broken.setId("X");
        broken.setNonrefundable_date_ranges(List.of(new QueryPriceResponse.CancelPolicy()));

        CheckPriceRespDTO resp = entry(true, response(broken, rate("B", "99.00"))).checkPrice(request(KEY, 10000));

        assertEquals(CheckPriceOutcome.BOOKABLE, resp.getOutcome());
        assertEquals("B", resp.getMessage());
    }
}
