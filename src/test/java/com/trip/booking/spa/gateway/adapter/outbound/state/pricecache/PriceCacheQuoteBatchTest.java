package com.trip.booking.spa.gateway.adapter.outbound.state.pricecache;

import com.trip.booking.spa.gateway.adapter.outbound.state.catalog.ProductAttributeReader;
import com.trip.booking.spa.gateway.domain.product.Product;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.PriceReq;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.Supplier;
import com.trip.booking.spa.platform.redis.RedisUtils;
import com.trip.booking.spa.platform.util.RedisKeyUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;

/**
 * 读侧票据详情必须一次 MGET 取齐，不许每个产品一次 GET。
 * 2026-09-10 机器内实测只有一家 0.5~0.9s、上游一页 5 家串行 4.6s，大头就是这 N 次往返。
 */
class PriceCacheQuoteBatchTest {

    private static final String DATE = "2026-09-30";
    private static final String PRICE_KEY = "price:10005:H1:2:" + DATE;   // 2 成人 0 儿童 → 占用片 2

    private PriceCacheServiceImpl service;
    private RedisUtils redisUtils;

    @BeforeEach
    void setUp() {
        service = new PriceCacheServiceImpl();
        redisUtils = Mockito.mock(RedisUtils.class);
        ProductAttributeReader attrs = Mockito.mock(ProductAttributeReader.class);
        Mockito.when(attrs.batchGet(anyInt(), any())).thenReturn(Map.of());
        ReflectionTestUtils.setField(service, "redisUtils", redisUtils);
        ReflectionTestUtils.setField(service, "productAttributeReader", attrs);
    }

    @Test
    @DisplayName("三个产品：票据详情只打一次 MGET，一次 GET 都不许有")
    void quoteDetailsAreFetchedInOneMultiGet() {
        Supplier sup = Supplier.builder().supplierId(10005).sHotelId("H1").build();
        PriceReq req = PriceReq.builder().checkIn(DATE).checkout("2026-10-01")
                .roomNum(1).adultNum(2).childNum(0).childAges(List.of()).build();
        Mockito.when(redisUtils.hashMapListAndKey(any())).thenReturn(Map.of(PRICE_KEY, Map.of(
                "pk1", "{\"price\":10000}", "pk2", "{\"price\":20000}", "pk3", "{\"price\":30000}")));
        Map<String, String> quotes = Map.of(
                RedisKeyUtils.buildQuoteKey(10005, "H1", "pk1"), "{\"productId\":\"P1\",\"hotelId\":\"H1\"}",
                RedisKeyUtils.buildQuoteKey(10005, "H1", "pk2"), "{\"productId\":\"P2\",\"hotelId\":\"H1\"}",
                RedisKeyUtils.buildQuoteKey(10005, "H1", "pk3"), "{\"productId\":\"P3\",\"hotelId\":\"H1\"}");
        Mockito.when(redisUtils.multiGet(any())).thenReturn(quotes);

        List<Product> out = service.getPrice(req, sup, null);

        assertEquals(3, out.size());
        Mockito.verify(redisUtils, Mockito.times(1)).multiGet(any());
        Mockito.verify(redisUtils, Mockito.never()).get(anyString());
    }
}
