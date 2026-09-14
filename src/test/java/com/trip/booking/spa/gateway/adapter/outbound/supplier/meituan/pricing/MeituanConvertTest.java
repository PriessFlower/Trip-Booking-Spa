package com.trip.booking.spa.gateway.adapter.outbound.supplier.meituan.pricing;

import com.trip.booking.spa.gateway.adapter.outbound.supplier.meituan.shared.MeituanProductKeyDeriver;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.meituan.shared.MeituanProperties;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.meituan.shared.MeituanWireNameTest;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.meituan.shared.model.MeituanBatchGoodsResponse;
import com.trip.booking.spa.gateway.application.pricing.PricingResult;
import com.trip.booking.spa.gateway.domain.booking.PricingOutcome;
import com.trip.booking.spa.gateway.domain.pricing.PriceQuery;
import com.trip.booking.spa.gateway.domain.product.Occupancy;
import com.trip.booking.spa.gateway.domain.product.PriceInfo;
import com.trip.booking.spa.gateway.domain.product.Product;
import com.trip.booking.spa.gateway.domain.supplier.SupplierSourceEnum;
import com.trip.booking.spa.platform.util.JsonUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 报价转换：供应商响应 → 可售产品。重点在<b>价格口径（B4）</b>与丢弃判据。
 *
 * <p>B4 要求对外总价统一为「全部间数 × 全部夜 × 含税 × 单一币种」，而本家的 {@code price}
 * 是「每间每晚」。2026-09-14 生产实测坐实了这一点：同一产品两晚，问 1 间与问 2 间返回的
 * 逐日价一模一样，即间数不进单价。
 */
class MeituanConvertTest {

    private static final Instant BEFORE_ALL = Instant.parse("2026-09-14T00:00:00Z");

    private final MeituanPriceServiceImpl service = service();

    private static MeituanPriceServiceImpl service() {
        MeituanProperties props = new MeituanProperties();
        props.setPartnerId("39155");
        props.setCurrency("USD");
        props.setClientNationality("CN");
        MeituanProductKeyDeriver deriver = new MeituanProductKeyDeriver();
        deriver.setProperties(props);
        MeituanPriceServiceImpl s = new MeituanPriceServiceImpl();
        s.setProperties(props);
        s.setProductKeyDeriver(deriver);
        return s;
    }

    private static PriceQuery query(int rooms) {
        PriceQuery q = PriceQuery.builder()
                .supplierId(SupplierSourceEnum.MEITUAN.getCode()).supplierHotelId("967183")
                .checkIn("2026-09-21").checkOut("2026-09-22")
                .roomNum(rooms).adultNum(2).childNum(0).childAges(List.of()).build();
        return q.toBuilder().occupancies(Occupancy.perRoom(rooms, 2, 0, List.of())).build();
    }

    @Test
    @DisplayName("真实报文一间：总价=逐晚之和；房型号进 room、goodsId 进 productId，二者不同字段")
    void singleRoomTotalIsSumOfNights() throws IOException {
        List<Product> products = service.convert(
                MeituanWireNameTest.fixture().hotelOf("967183"), query(1), BEFORE_ALL);

        assertEquals(3, products.size(), "四条产品里那条非即时确认的被挡掉");
        Product p = products.get(0);
        assertEquals(25855, p.getTotalPrice(), "258.55 美元 → 分（报文已是分）");
        assertEquals("USD", p.getCurrencyType(), "币种不在报文里，只在配置里");
        assertEquals("26899457", p.getRoom().getRoomId(), "物理房型号");
        assertEquals("158043997015", p.getProductId(), "报价码=goodsId");
        assertNotEquals(p.getProductId(), p.getProductKey(), "身份与令牌永不同字段（R-2.3）");
        assertEquals(SupplierSourceEnum.MEITUAN.getCode(), p.getSupplierId());
        assertNull(p.getProductInfo().getInventory(), "报文不带可售间数，不许拿'能问到价'当'有货N间'");
    }

    /**
     * B4 的核心断言。它红了说明总价又变回单间口径——而这种错在单间查询下永远不会暴露
     * （刷价恒 1 间），只在客人订多间时才变成成本落账错。
     */
    @Test
    @DisplayName("三间：总价必须是单间的三倍；逐晚明细仍按单间给，两者刻意不同口径")
    void multiRoomTotalMultipliesByRoomCount() throws IOException {
        List<Product> one = service.convert(MeituanWireNameTest.fixture().hotelOf("967183"), query(1), BEFORE_ALL);
        List<Product> three = service.convert(MeituanWireNameTest.fixture().hotelOf("967183"), query(3), BEFORE_ALL);

        assertEquals(25855, one.get(0).getTotalPrice());
        assertEquals(25855 * 3, three.get(0).getTotalPrice(), "B4：全部间数 × 全部夜");

        int detail = three.get(0).getPriceInfos().stream().mapToInt(PriceInfo::getPrice).sum();
        assertEquals(25855, detail, "逐晚明细是单间口径，故三间时它不等于总价");
    }

    @Test
    @DisplayName("非即时确认一律不卖：它有价、有房型，只能靠 confirmType 挡")
    void onRequestGoodsAreDropped() throws IOException {
        List<Product> products = service.convert(
                MeituanWireNameTest.fixture().hotelOf("967183"), query(1), BEFORE_ALL);

        assertTrue(products.stream().noneMatch(p -> "804665179".equals(p.getProductId())),
                "confirmType=2 的那条不许出现在可售结果里");
    }

    @Test
    @DisplayName("餐食：无早判成 0 份、含早判成报文份数，两者的 productKey 必须不同")
    void mealSplitsTheIdentity() throws IOException {
        List<Product> products = service.convert(
                MeituanWireNameTest.fixture().hotelOf("967183"), query(1), BEFORE_ALL);

        Product noBreakfast = byId(products, "158043997015");
        Product breakfast = byId(products, "158043997016");
        assertEquals(0, noBreakfast.getMeal().getCount());
        assertEquals(2, breakfast.getMeal().getCount());
        assertNotEquals(noBreakfast.getProductKey(), breakfast.getProductKey(),
                "含不含早是两种卖法，压成一个键会让上游拿错货");
    }

    @Test
    @DisplayName("逐晚价条数与住期不符 → 整条丢弃，不猜缺的那晚")
    void nightCountMismatchDropsTheGoods() throws IOException {
        // 夹具是一晚的报文，按两晚的住期去转换
        PriceQuery twoNights = query(1).toBuilder().checkOut("2026-09-23").build();

        List<Product> products = service.convert(
                MeituanWireNameTest.fixture().hotelOf("967183"), twoNights, BEFORE_ALL);

        assertTrue(products.isEmpty());
    }

    @Test
    @DisplayName("code=0 但该酒店不在结果里：是没货，不是有货、也不是不确定")
    void missingHotelMeansNoInventory() throws IOException {
        MeituanBatchGoodsResponse resp = JsonUtils.readValue(
                "{\"code\":0,\"message\":\"success\",\"result\":[]}", MeituanBatchGoodsResponse.class);

        PricingResult result = service.toPricingResult(resp, query(1));

        assertEquals(PricingOutcome.NO_INVENTORY, result.outcome());
    }

    /**
     * 本家的非零码全是系统层面的失败，<b>没有一个表示"这家这住期没货"</b>。
     * 把它读成无货就会去清缓存，等于让一次内部错误抹掉在售价（F-5.1）。
     */
    @Test
    @DisplayName("code!=0 一律不确定，绝不当成无货去清缓存")
    void businessFailureIsIndeterminate() throws IOException {
        MeituanBatchGoodsResponse resp = JsonUtils.readValue(
                "{\"code\":2000,\"message\":\"内部服务错误:系统处理错误\"}", MeituanBatchGoodsResponse.class);

        assertEquals(PricingOutcome.INDETERMINATE, service.toPricingResult(resp, query(1)).outcome());
    }

    private static Product byId(List<Product> products, String productId) {
        return products.stream().filter(p -> productId.equals(p.getProductId())).findFirst().orElseThrow();
    }
}
