package com.trip.booking.spa.gateway.adapter.outbound.state.pricecache;

import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.PriceInfo;
import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.ProductRespDTO;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.PriceReq;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.Supplier;
import com.trip.booking.spa.platform.redis.RedisUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;

/**
 * 下架置 0 的判据钉死（docs/price-refresh.md F-5.2）。
 *
 * <p>核心命题：同一 {@code price:{hotelId}:{date}} 下<b>多个产品同时下架时必须全部置 0</b>。
 * 曾经因为在 productId 循环里新建 map 再整体覆盖，只有最后一条真被置 0，其余保留旧价
 * 直到 TTL 过期，期间照常对外报价、照常可被下单（issue #96）。
 */
class CachePriceServiceImplTest {

    private static final String DATE = "2026-09-01";
    /**
     * 价格键自 2026-08-20 起带占用分片：{@code price:{hotel}:{occupancy}:{date}}。
     * 本用例的 {@code oneNight()} 是 1 成人 0 儿童，故占用片为 {@code 1}。
     */
    private static final String PRICE_KEY = "price:H1:1:" + DATE;

    private CachePriceServiceImpl service;
    private RedisUtils redisUtils;

    @BeforeEach
    void setUp() {
        service = new CachePriceServiceImpl();
        redisUtils = Mockito.mock(RedisUtils.class);

        PriceCacheTrimmer trimmer = Mockito.mock(PriceCacheTrimmer.class);
        // 裁剪不是本类的关注点：原样返回，让下架判断面对完整入参
        Mockito.when(trimmer.trim(any())).thenAnswer(inv -> inv.getArgument(0));

        AbnormalPriceGuard guard = Mockito.mock(AbnormalPriceGuard.class);
        Mockito.when(guard.isAbnormalDrop(any(), any())).thenReturn(false);

        PriceCacheTtlPolicy ttlPolicy = Mockito.mock(PriceCacheTtlPolicy.class);
        Mockito.when(ttlPolicy.ttlSeconds(anyString())).thenReturn(86400L);

        ReflectionTestUtils.setField(service, "redisUtils", redisUtils);
        ReflectionTestUtils.setField(service, "priceCacheTrimmer", trimmer);
        ReflectionTestUtils.setField(service, "abnormalPriceGuard", guard);
        ReflectionTestUtils.setField(service, "priceCacheTtlPolicy", ttlPolicy);
    }

    /** 缓存中该日期已有 p1~p3 三条在售 */
    private void givenCached(String... productIds) {
        Map<String, String> cached = new HashMap<>();
        for (String id : productIds) {
            cached.put(id, "{\"price\":12345}");
        }
        Mockito.when(redisUtils.hashMapGet(PRICE_KEY)).thenReturn(cached);
    }

    private static Supplier sup() {
        return Supplier.builder().supplierId(10010).sHotelId("H1").build();
    }

    private static PriceReq oneNight() {
        // PriceReq 的这些字段带 @NonNull，缺一个就在 build() 抛 NPE；取值与本用例无关
        return PriceReq.builder().checkIn(DATE).checkout("2026-09-02")
                .roomNum(1).adultNum(1).childNum(0).childAges(List.of()).guestType(0).build();
    }

    private static ProductRespDTO product(String productId, Integer price) {
        ProductRespDTO.ProductRespDTOBuilder b = ProductRespDTO.builder().hotelId("H1").productId(productId);
        if (price != null) {
            b.priceInfos(List.of(PriceInfo.builder().date(DATE).price(price).build()));
        }
        return b.build();
    }

    /** 取所有写入批次中该 priceKey 下值为 0（即下架标记）的 productId */
    private Set<String> zeroedProductIds() {
        ArgumentCaptor<Map<String, Map<String, String>>> captor = ArgumentCaptor.forClass(Map.class);
        Mockito.verify(redisUtils, Mockito.atLeastOnce())
                .batchHashMapSetWithExpire(captor.capture(), anyLong(), any(TimeUnit.class));
        return captor.getAllValues().stream()
                .map(batch -> batch.get(PRICE_KEY))
                .filter(java.util.Objects::nonNull)
                .flatMap(m -> m.entrySet().stream())
                .filter(e -> e.getValue().contains("\"price\":0"))
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    /**
     * 本类存在的理由：整店该日期全部下架时，三条都要置 0。
     * 缺陷版本只会置 0 最后一条，另两条保留旧价至 TTL 过期。
     */
    @Test
    void zeroesEveryVanishedProductWhenNothingIsOnSale() {
        givenCached("p1", "p2", "p3");

        // 入参产品无任何报价 → 该日期本轮零在售
        service.productToCache(List.of(product("p1", null)), oneNight(), sup());

        assertEquals(Set.of("p1", "p2", "p3"), zeroedProductIds());
    }

    /** 部分仍在售时，缺席的那些也要全部置 0，而非只置最后一条 */
    @Test
    void zeroesEveryVanishedProductWhenSomeRemainOnSale() {
        givenCached("p1", "p2", "p3");

        service.productToCache(List.of(product("p1", 12345)), oneNight(), sup());

        assertEquals(Set.of("p2", "p3"), zeroedProductIds());
    }

    /**
     * F-7 拦下的产品不得被当成"本轮无价"置 0——那会把"疑似错价"恶化成"确定无货"。
     * 与上一条一并断言，防止为修下架累加而误伤拦截豁免。
     */
    @Test
    void doesNotZeroProductsHeldBackByTheAbnormalPriceGuard() {
        givenCached("p1", "p2", "p3");
        AbnormalPriceGuard guard = (AbnormalPriceGuard) ReflectionTestUtils.getField(service, "abnormalPriceGuard");
        Mockito.when(guard.isAbnormalDrop(any(), any())).thenReturn(true);

        // p1 报了新价但被拦下：它既不进 dataMap，也不该被置 0；p2/p3 是真下架
        service.productToCache(List.of(product("p1", 1)), oneNight(), sup());

        assertEquals(Set.of("p2", "p3"), zeroedProductIds());
    }
}
