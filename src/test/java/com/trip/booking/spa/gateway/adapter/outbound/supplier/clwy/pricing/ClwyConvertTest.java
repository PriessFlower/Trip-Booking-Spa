package com.trip.booking.spa.gateway.adapter.outbound.supplier.clwy.pricing;

import com.trip.booking.spa.gateway.adapter.outbound.supplier.clwy.shared.ClwyProductKeyDeriver;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.clwy.shared.ClwyProperties;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.clwy.shared.ClwyWireNameTest;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.clwy.shared.model.ClwyDayPrice;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.clwy.shared.model.ClwyPriceResponse;
import com.trip.booking.spa.gateway.application.pricing.PricingResult;
import com.trip.booking.spa.gateway.domain.booking.PricingOutcome;
import com.trip.booking.spa.gateway.domain.pricing.PriceQuery;
import com.trip.booking.spa.gateway.domain.product.Occupancy;
import com.trip.booking.spa.gateway.domain.product.Product;
import com.trip.booking.spa.gateway.domain.supplier.SupplierSourceEnum;
import com.trip.booking.spa.platform.util.JsonUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 报价转换：供应商响应 → 可售产品。重点在<b>价格口径（B4）</b>与丢弃判据。
 *
 * <p>B4 要求对外总价统一为「全部间数 × 全部夜 × 含税 × 单一币种」，而本家的 {@code price}
 * 是「每天每间」——docs/gateway-boundary.md 点名「clwy 单间/全间数致成本落账错」，就是这里。
 */
class ClwyConvertTest {

    private final ClwyPriceServiceImpl service = service();

    private static ClwyPriceServiceImpl service() {
        ClwyProperties props = new ClwyProperties();
        props.setWid("W-TEST");
        ClwyProductKeyDeriver deriver = new ClwyProductKeyDeriver();
        deriver.setProperties(props);
        ClwyPriceServiceImpl s = new ClwyPriceServiceImpl();
        s.setProperties(props);
        s.setProductKeyDeriver(deriver);
        return s;
    }

    private static PriceQuery query(int rooms) {
        PriceQuery q = PriceQuery.builder()
                .supplierId(SupplierSourceEnum.CLWY.getCode()).supplierHotelId("218951")
                .checkIn("2026-09-21").checkOut("2026-09-22")
                .roomNum(rooms).adultNum(2).childNum(0).childAges(List.of()).build();
        return q.toBuilder().occupancies(Occupancy.perRoom(rooms, 2, 0, List.of())).build();
    }

    /**
     * 2026-09-14 生产实测：同一家酒店 T+1/T+60 回 hotelList 各 1 家，T+200/T+330 回
     * {@code code=200} 且 hotelList 为 <b>null</b>、message 为空——即<b>报价档表达"没货"
     * 的常规形态</b>，不是异常。判成 AVAILABLE 会让缓存留着上一轮的僵尸价（B7）。
     */
    @Test
    @DisplayName("报价档 200 + hotelList 为 null：是没货，不是有货、也不是不确定")
    void quoteLaneNullHotelListMeansNoInventory() throws IOException {
        ClwyPriceResponse resp = JsonUtils.readValue("{\"code\":200,\"message\":\"\"}", ClwyPriceResponse.class);

        PricingResult result = service.toPricingResult(resp, query(1));

        assertEquals(PricingOutcome.NO_INVENTORY, result.outcome());
        assertTrue(result.products() == null || result.products().isEmpty());
    }

    @Test
    @DisplayName("真实报文一间：总价=逐晚之和；房型号进 room、报价码进 productId，二者不同字段")
    void singleRoomTotalIsSumOfNights() throws IOException {
        List<Product> products = service.convert(ClwyWireNameTest.fixture().firstHotel(), query(1));

        assertEquals(2, products.size(), "两条报价都可售");
        Product p = products.get(0);
        assertEquals(268264, p.getTotalPrice(), "2682.64 元 → 分");
        assertEquals("CNY", p.getCurrencyType());
        assertEquals("22720265", p.getRoom().getRoomId(), "房型号");
        assertEquals("40f43641489389fe9a8e0d9ec2881e21b9|3", p.getProductId(), "报价码");
        assertNotEquals(p.getProductId(), p.getProductKey(), "身份与令牌永不同字段（R-2.3）");
        assertEquals(SupplierSourceEnum.CLWY.getCode(), p.getSupplierId());
        assertEquals(0, p.getTotalTaxes(), "FeeList 是到店付，不算进我们卖的这个价");
    }

    /**
     * B4 的核心断言。它红了说明总价又变回单间口径——那正是 cursor 侧成本落账错的成因，
     * 而这种错在单间查询下永远不会暴露（刷价恒 1 间）。
     */
    @Test
    @DisplayName("三间：总价必须是单间的三倍；逐晚明细仍按单间给，两者刻意不同口径")
    void multiRoomTotalMultipliesByRoomCount() throws IOException {
        List<Product> one = service.convert(ClwyWireNameTest.fixture().firstHotel(), query(1));
        List<Product> three = service.convert(ClwyWireNameTest.fixture().firstHotel(), query(3));

        assertEquals(268264, one.get(0).getTotalPrice());
        assertEquals(268264 * 3, three.get(0).getTotalPrice(), "B4：全部间数 × 全部夜");
        assertEquals(one.get(0).getPriceInfos().get(0).getPrice(),
                three.get(0).getPriceInfos().get(0).getPrice(),
                "逐晚明细是单间口径，不随间数变——它是明细不是总价");
    }

    @Test
    @DisplayName("逐晚币种不一致 → 丢弃：总价无法在单一币种下相加，不猜汇率")
    void mixedCurrencyIsDropped() {
        assertNull(ClwyPriceServiceImpl.singleCurrency(List.of(day("CNY", "100"), day("USD", "100"))));
        assertEquals("CNY", ClwyPriceServiceImpl.singleCurrency(List.of(day("CNY", "100"), day("cny", "100"))),
                "大小写不同不算不一致");
        assertNull(ClwyPriceServiceImpl.singleCurrency(List.of(day(null, "100"))));
    }

    @Test
    @DisplayName("可售间数取逐晚最小值；任一晚缺失即未知，不拿其余晚的数去填")
    void inventoryIsTheNightlyMinimum() {
        ClwyDayPrice a = day("CNY", "100");
        a.setRoomCount(5);
        ClwyDayPrice b = day("CNY", "100");
        b.setRoomCount(2);
        assertEquals(2, ClwyPriceServiceImpl.minRoomCount(List.of(a, b)));
        ClwyDayPrice missing = day("CNY", "100");
        missing.setRoomCount(null);
        assertNull(ClwyPriceServiceImpl.minRoomCount(List.of(a, missing)));
    }

    @Test
    @DisplayName("总价换算：Σ逐日 × 间数，元→分不丢精度")
    void totalCentsRounding() {
        List<ClwyDayPrice> days = List.of(day("CNY", "2682.64"), day("CNY", "0.01"));
        assertEquals(268265, ClwyPriceServiceImpl.totalCents(days, 1, "CNY"));
        assertEquals(536530, ClwyPriceServiceImpl.totalCents(days, 2, "CNY"));
        ClwyDayPrice noPrice = day("CNY", null);
        assertNull(ClwyPriceServiceImpl.totalCents(List.of(noPrice), 1, "CNY"));
    }

    @Test
    @DisplayName("code=200 但空列表在报价档是会发生的（实测），按无货处理而不是当成有货")
    void emptyHotelListOnQuoteIsNoInventory() {
        ClwyPriceResponse resp = new ClwyPriceResponse();
        resp.setCode(200);
        resp.setMessage("");
        assertTrue(resp.isEmptyResult());
        assertTrue(service.toPricingResult(resp, query(1)).outcome().name().equals("NO_INVENTORY"));
    }

    @Test
    @DisplayName("500 No Availability → 无货；500 其他文案 → 不确定，绝不当成无货去清缓存")
    void onlyTheProvenMessageMeansNoInventory() {
        ClwyPriceResponse noAvail = new ClwyPriceResponse();
        noAvail.setCode(500);
        noAvail.setMessage("No Availability");
        assertEquals("NO_INVENTORY", service.toPricingResult(noAvail, query(1)).outcome().name());

        ClwyPriceResponse other = new ClwyPriceResponse();
        other.setCode(500);
        other.setMessage("Some new server error");
        assertEquals("INDETERMINATE", service.toPricingResult(other, query(1)).outcome().name());
    }

    private static ClwyDayPrice day(String currency, String price) {
        ClwyDayPrice d = new ClwyDayPrice();
        d.setDate("2026-09-21");
        d.setCurrency(currency);
        d.setPrice(price == null ? null : new BigDecimal(price));
        d.setRoomCount(1);
        d.setFlag(1);
        return d;
    }
}
