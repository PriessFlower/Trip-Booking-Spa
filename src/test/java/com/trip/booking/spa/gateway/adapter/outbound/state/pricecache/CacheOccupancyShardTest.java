package com.trip.booking.spa.gateway.adapter.outbound.state.pricecache;

import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.PriceInfo;
import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.ProductRespDTO;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.PriceReq;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.Supplier;
import com.trip.booking.spa.gateway.adapter.outbound.state.catalog.ProductAttributeReader;
import com.trip.booking.spa.gateway.domain.product.CancelClass;
import com.trip.booking.spa.gateway.domain.product.MealSignature;
import com.trip.booking.spa.gateway.domain.product.ProductIdentity;
import com.trip.booking.spa.platform.redis.RedisUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;

/**
 * 价格缓存按<b>占用</b>分片（2026-08-20）。
 *
 * <p>productKey 的成分里有占用，所以 1 人与 2 人的卖法本就是两个不同的 field，
 * 在同一个 Hash 里互不覆盖——结构上不冲突。真正的病在<b>读侧分不出来</b>：
 * 出价是把整个 Hash 端出来、里面有什么报什么，没有任何一处按占用过滤。于是刷价按
 * 1 人刷、客人 2 人来查，1 人的价被原样报了出去（生产实测同店同日 1 人 335.46、
 * 2 人 293.17，差 42 元）。
 *
 * <p>分片后 2 人查询取到的是空片，如实回报无货——宁可少卖，不可卖错（R-1.6）。
 */
class CacheOccupancyShardTest {

    private static final String DATE = "2026-09-01";

    private PriceCacheServiceImpl service;
    private RedisUtils redisUtils;
    private ProductAttributeReader attributeReader;

    @BeforeEach
    void setUp() {
        service = new PriceCacheServiceImpl();
        redisUtils = Mockito.mock(RedisUtils.class);
        attributeReader = Mockito.mock(ProductAttributeReader.class);
        ReflectionTestUtils.setField(service, "redisUtils", redisUtils);
        ReflectionTestUtils.setField(service, "productAttributeReader", attributeReader);
        ReflectionTestUtils.setField(service, "priceCacheTrimmer", new PriceCacheTrimmer());
        ReflectionTestUtils.setField(service, "abnormalPriceGuard", new AbnormalPriceGuard());
        ReflectionTestUtils.setField(service, "priceCacheTtlPolicy", new PriceCacheTtlPolicy());
        Mockito.when(attributeReader.batchGet(Mockito.anyInt(), Mockito.anyList()))
                .thenReturn(Map.of());
    }

    private static PriceReq req(int adults, Integer childNum, List<Integer> ages) {
        return PriceReq.builder().checkIn(DATE).checkout("2026-09-02")
                .roomNum(1).adultNum(adults)
                .childNum(childNum == null ? 0 : childNum)
                .childAges(ages == null ? List.of() : ages)
                .build();
    }

    private static ProductRespDTO productFor(String occupancy) {
        ProductIdentity id = ProductIdentity.of(10010, "acct", "H1", "R1",
                MealSignature.known(true, false, false), CancelClass.FREE_CANCELLABLE, occupancy);
        return ProductRespDTO.builder()
                .hotelId("H1").productId("易腐票").productKey(id.productKey()).identity(id)
                .priceInfos(List.of(PriceInfo.builder().date(DATE).price(29317).build()))
                .build();
    }

    private static Supplier sup() {
        return Supplier.builder().supplierId(10010).sHotelId("H1").build();
    }

    /** 出价读侧走批量取，捕获它请求的那批键 */
    @SuppressWarnings("unchecked")
    private List<String> readKeys() {
        ArgumentCaptor<List<String>> cap = ArgumentCaptor.forClass(List.class);
        Mockito.verify(redisUtils, Mockito.atLeastOnce()).hashMapListAndKey(cap.capture());
        return cap.getValue();
    }

    /** 捕获所有写入批次里出现过的 priceKey */
    private java.util.Set<String> writtenPriceKeys() {
        ArgumentCaptor<Map<String, Map<String, String>>> cap = ArgumentCaptor.forClass(Map.class);
        Mockito.verify(redisUtils, Mockito.atLeastOnce())
                .batchHashMapSetWithExpire(cap.capture(), anyLong(), any(TimeUnit.class));
        java.util.Set<String> keys = new java.util.HashSet<>();
        cap.getAllValues().forEach(b -> keys.addAll(b.keySet()));
        return keys;
    }

    @Test
    @DisplayName("写入的键带占用片，且占用取自 identity（与 productKey 同源）")
    void writeShardsByIdentityOccupancy() {
        service.productToCache(List.of(productFor("2")), req(2, 0, List.of()), sup());

        assertTrue(writtenPriceKeys().contains("price:10010:H1:2:" + DATE),
                "实际写入: " + writtenPriceKeys());
    }

    @Test
    @DisplayName("2 人查询不会读到 1 人那一片——这正是改造前把 1 人价报给 2 人的那条路")
    void twoAdultsNeverReadTheOnePersonShard() {
        service.getPrice(req(2, 0, List.of()), Supplier.builder().supplierId(10010).sHotelId("H1").build());

        assertEquals(List.of("price:10010:H1:2:" + DATE), readKeys(), "2 人查询只许读 2 人片");
    }

    @Test
    @DisplayName("带儿童的占用也各成一片")
    void childAgesFormTheirOwnShard() {
        service.getPrice(req(2, 1, List.of(9)), Supplier.builder().supplierId(10010).sHotelId("H1").build());

        assertEquals(List.of("price:10010:H1:2-9:" + DATE), readKeys());
    }

    @Test
    @DisplayName("写侧与读侧算出同一片——两处拼键必须不可能漂移")
    void writeAndReadAgreeOnTheSameShard() {
        service.productToCache(List.of(productFor("2-9,4")), req(2, 2, List.of(9, 4)), sup());
        java.util.Set<String> written = writtenPriceKeys();

        Mockito.clearInvocations(redisUtils);
        service.getPrice(req(2, 2, List.of(9, 4)), Supplier.builder().supplierId(10010).sHotelId("H1").build());
        List<String> read = readKeys();

        assertTrue(written.containsAll(read),
                "读的键必须在写过的键里。写=" + written + " 读=" + read);
    }
}
