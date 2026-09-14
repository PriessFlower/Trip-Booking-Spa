package com.trip.booking.spa.gateway.adapter.outbound.state.pricecache;

import com.trip.booking.spa.gateway.domain.product.PriceInfo;
import com.trip.booking.spa.gateway.domain.product.Product;
import com.trip.booking.spa.gateway.domain.pricing.PriceQuery;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.Supplier;
import com.trip.booking.spa.gateway.adapter.outbound.state.catalog.ProductAttributeReader;
import com.trip.booking.spa.platform.redis.RedisUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 每日价明细的 {@code date} 必须是日期，不能是键里的别的段。
 *
 * <p><b>由来</b>：价格键 2026-08-20 从 {@code price:{hotel}:{date}} 变成
 * {@code price:{hotel}:{occupancy}:{date}}，而取日期的实现是写死的
 * {@code split(":")[2]}——改键时漏了这一处。生产实测：两晚的 priceInfos 里
 * {@code date} 全变成 {@code "1"}（占用串），两条彼此无法区分。总价是对的
 * （逐日累加不看这个字段），错的只有对外的日期标签，所以既不报错也不失败。
 *
 * <p>这类"只错标签不错金额"的缺陷最难被发现，故单独钉一组。
 */
class PriceInfoDateTest {

    private static final String D1 = "2026-09-01";
    private static final String D2 = "2026-09-02";

    private PriceCacheServiceImpl service;
    private RedisUtils redisUtils;

    @BeforeEach
    void setUp() {
        service = new PriceCacheServiceImpl();
        ReflectionTestUtils.setField(service, "productCatalogService", Mockito.mock(com.trip.booking.spa.gateway.adapter.outbound.state.catalog.ProductCatalogService.class));
        redisUtils = Mockito.mock(RedisUtils.class);
        ReflectionTestUtils.setField(service, "redisUtils", redisUtils);
        ReflectionTestUtils.setField(service, "productAttributeReader",
                Mockito.mock(ProductAttributeReader.class));
        ReflectionTestUtils.setField(service, "priceCacheTrimmer", new PriceCacheTrimmer());
        ReflectionTestUtils.setField(service, "abnormalPriceGuard", new AbnormalPriceGuard());
        ReflectionTestUtils.setField(service, "priceCacheTtlPolicy", new PriceCacheTtlPolicy());
        Mockito.when(((ProductAttributeReader) ReflectionTestUtils.getField(service, "productAttributeReader"))
                .batchGet(Mockito.anyInt(), Mockito.anyList())).thenReturn(Map.of());
    }

    private static PriceQuery twoNights(int adults) {
        return PriceQuery.builder().supplierId(10010).supplierHotelId("H1").checkIn(D1).checkOut("2026-09-03")
                .roomNum(1).adultNum(adults).childNum(0).childAges(List.of())
                .build();
    }

    private static Supplier sup() {
        return Supplier.builder().supplierId(10010).sHotelId("H1").build();
    }

    /** 缓存里两天各一条价，field 是同一个 productKey */
    private void givenTwoNightsCached(String occupancy) {
        String pk = "a".repeat(64);
        Map<String, Map<String, String>> hashes = new LinkedHashMap<>();
        hashes.put("price:10010:H1:" + occupancy + ":" + D1, Map.of(pk, "{\"price\":65940,\"taxes\":0,\"roomPrice\":65940}"));
        hashes.put("price:10010:H1:" + occupancy + ":" + D2, Map.of(pk, "{\"price\":59609,\"taxes\":0,\"roomPrice\":59609}"));
        Mockito.when(redisUtils.hashMapListAndKey(Mockito.anyList())).thenReturn(hashes);
        // 读侧 2026-09-10 起一次 MGET 取齐票据（此前逐产品 GET）：要什么键就给什么键，载荷同一份
        Mockito.when(redisUtils.multiGet(Mockito.anyCollection())).thenAnswer(inv -> {
            java.util.Collection<String> keys = inv.getArgument(0);
            Map<String, String> m = new LinkedHashMap<>();
            keys.forEach(k -> m.put(k, "{\"productId\":\"易腐票\",\"productKey\":\"" + pk + "\"}"));
            return m;
        });
    }

    @Test
    @DisplayName("每日价的 date 必须是真实日期，不能是占用串")
    void dateIsTheDateNotTheOccupancy() {
        givenTwoNightsCached("1");

        List<Product> products = service.getPrice(twoNights(1));

        assertEquals(1, products.size(), "应出一条产品");
        List<PriceInfo> infos = products.get(0).getPriceInfos();
        assertEquals(2, infos.size(), "两晚应有两条明细");
        List<String> dates = infos.stream().map(PriceInfo::getDate).sorted().toList();
        assertEquals(List.of(D1, D2), dates,
                "date 取错段时这里会是 [1, 1]——占用串，且两条无法区分");
    }

    /**
     * 占用是多段串（带儿童年龄）时，日期仍必须落在最后一段。
     * 固定下标的实现在这种键上错得更隐蔽：{@code price:H1:2-9,4:2026-09-01} 的 [2] 是 {@code 2-9,4}。
     */
    @Test
    @DisplayName("带儿童年龄的占用串不影响取日期")
    void multiPartOccupancyStillYieldsTheDate() {
        givenTwoNightsCached("2-9,4");
        PriceQuery req = PriceQuery.builder().supplierId(10010).supplierHotelId("H1").checkIn(D1).checkOut("2026-09-03")
                .roomNum(1).adultNum(2).childNum(2).childAges(List.of(9, 4)).build();

        List<Product> products = service.getPrice(req);

        assertTrue(!products.isEmpty(), "应出产品");
        List<String> dates = products.get(0).getPriceInfos().stream().map(PriceInfo::getDate).sorted().toList();
        assertEquals(List.of(D1, D2), dates);
    }

    @Test
    @DisplayName("总价仍是逐日累加——修日期不能动金额")
    void totalStillSumsTheNights() {
        givenTwoNightsCached("1");

        List<Product> products = service.getPrice(twoNights(1));

        assertEquals(65940 + 59609, products.get(0).getTotalPrice());
    }
}
