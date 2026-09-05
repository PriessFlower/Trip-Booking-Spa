package com.trip.booking.spa.gateway.adapter.outbound.state.pricecache;

import com.trip.booking.spa.gateway.adapter.inbound.rest.request.PriceReq;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.Supplier;
import com.trip.booking.spa.gateway.adapter.outbound.state.catalog.ProductAttributeReader;
import com.trip.booking.spa.gateway.application.pricing.PricingResult;
import com.trip.booking.spa.gateway.domain.booking.PricingOutcome;
import com.trip.booking.spa.platform.redis.RedisUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;

/**
 * 「刷过且无货」与「压根没刷过」必须可辨（F-5.1 / F-5.2）。
 *
 * <p>改造前，缓存读侧只有"有产品"和"没有"两种结果，空一律回报 INDETERMINATE，
 * 于是三件不同的事塌成一态：这一片没刷过 / 刷过且供应商明确答无在售 / 已过 TTL。
 *
 * <p>塌了之后有两处代价：① 对确定无货回报"未能确认"，诱发上游无谓重试；
 * ② 「刷价没覆盖到这个占用片」（刷价只按 1 人问）这类缺口在出价侧完全不可见——
 * 它和"供应商真没房"长得一模一样。
 */
class NoInventoryMarkTest {

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
    }

    private static PriceReq req(int adults) {
        return PriceReq.builder().checkIn(D1).checkout(D2)
                .roomNum(1).adultNum(adults).childNum(0).childAges(List.of())
                .build();
    }

    private static Supplier sup() {
        return Supplier.builder().supplierId(10010).sHotelId("H1").build();
    }

    @Test
    @DisplayName("刷到无在售 → 写无货标记，不是什么都不做")
    void emptyRefreshWritesTheMarker() {
        service.productToCache(List.of(), req(1), sup());

        ArgumentCaptor<Map<String, Map<String, String>>> cap = ArgumentCaptor.forClass(Map.class);
        Mockito.verify(redisUtils).batchHashMapSetWithExpire(cap.capture(), anyLong(), any(TimeUnit.class));
        Map<String, Map<String, String>> written = cap.getValue();

        assertTrue(written.containsKey("price:10010:H1:1:" + D1), "实际写入: " + written.keySet());
        assertEquals("1", written.get("price:10010:H1:1:" + D1).get(PriceCacheServiceImpl.NO_INVENTORY_FIELD));
    }

    /**
     * 刷价按 1 人问，就只能替 1 人那片作答。把 2 人也标成无货，是拿"我们没问"
     * 冒充"供应商说没有"。
     */
    @Test
    @DisplayName("只标记本次刷价的那一片占用")
    void marksOnlyTheRefreshedOccupancyShard() {
        service.productToCache(List.of(), req(1), sup());

        ArgumentCaptor<Map<String, Map<String, String>>> cap = ArgumentCaptor.forClass(Map.class);
        Mockito.verify(redisUtils).batchHashMapSetWithExpire(cap.capture(), anyLong(), any(TimeUnit.class));

        assertTrue(cap.getValue().keySet().stream().allMatch(k -> k.startsWith("price:10010:H1:1:")),
                "不该碰别的占用片: " + cap.getValue().keySet());
    }

    @Test
    @DisplayName("读到标记 → NO_INVENTORY（确定事实，重试无用）")
    void markedShardReadsAsNoInventory() {
        Mockito.when(redisUtils.hmGet("price:10010:H1:1:" + D1, PriceCacheServiceImpl.NO_INVENTORY_FIELD))
                .thenReturn("1");

        PricingResult r = service.getPriceResult(req(1), sup());

        assertEquals(PricingOutcome.NO_INVENTORY, r.outcome());
    }

    @Test
    @DisplayName("没标记也没产品 → INDETERMINATE（这一片没刷过）")
    void unmarkedEmptyShardStaysIndeterminate() {
        PricingResult r = service.getPriceResult(req(2), sup());

        assertEquals(PricingOutcome.INDETERMINATE, r.outcome(),
                "2 人那片从没刷过——不能因为它是空的就说供应商没房");
    }

    /**
     * 这条把两件事绑在一起：占用分片 + 分态。1 人那片明确无货，不代表 2 人那片也无货。
     */
    @Test
    @DisplayName("1 人片的无货标记不会外溢到 2 人片")
    void oneAdultMarkDoesNotAnswerForTwoAdults() {
        Mockito.when(redisUtils.hmGet("price:10010:H1:1:" + D1, PriceCacheServiceImpl.NO_INVENTORY_FIELD))
                .thenReturn("1");

        assertEquals(PricingOutcome.NO_INVENTORY, service.getPriceResult(req(1), sup()).outcome());
        assertEquals(PricingOutcome.INDETERMINATE, service.getPriceResult(req(2), sup()).outcome());
    }

    /**
     * 与出价"每一天都得有价才报"同一口径：只有部分日期有标记，说明另一些日期压根没刷过。
     */
    @Test
    @DisplayName("住期内只要有一天没标记，就不算确定无货")
    void partialMarkIsNotEnough() {
        PriceReq threeNights = PriceReq.builder().checkIn(D1).checkout("2026-09-04")
                .roomNum(1).adultNum(1).childNum(0).childAges(List.of())
                .build();
        Mockito.when(redisUtils.hmGet("price:10010:H1:1:" + D1, PriceCacheServiceImpl.NO_INVENTORY_FIELD))
                .thenReturn("1");
        // 第二、三天没标记

        assertEquals(PricingOutcome.INDETERMINATE, service.getPriceResult(threeNights, sup()).outcome());
    }

    /**
     * 标记与真实价格同住一个 Hash，读侧遍历每个 field 当价格 JSON 解析——必须显式跳过标记。
     *
     * <p>2026-08-20 本地实跑抓到：不跳过时 {@code decodeJson("1")} 返回 null，
     * 下一行 {@code priceMap.get("price")} 直接 NPE，出价整个 500。
     * 单测当时全绿——因为没有任何一条喂过"Hash 里同时有标记"这个真实形状。
     */
    @Test
    @DisplayName("读价时必须跳过无货标记，不能拿它当价格解析")
    void theMarkerIsNotParsedAsAPrice() {
        Mockito.when(redisUtils.hashMapListAndKey(Mockito.anyList()))
                .thenReturn(Map.of("price:10010:H1:1:" + D1,
                        Map.of(PriceCacheServiceImpl.NO_INVENTORY_FIELD, "1")));

        PricingResult r = org.junit.jupiter.api.Assertions.assertDoesNotThrow(
                () -> service.getPriceResult(req(1), sup()));

        assertEquals(PricingOutcome.INDETERMINATE, r.outcome(),
                "只有标记没有价：本用例没桩 hmGet，故落未能确认；要点是不许抛异常");
    }

    /** 同一片里既有真实价又有陈留标记时：有货优先，且不因标记而崩 */
    @Test
    @DisplayName("标记与真实价共存时照常出价")
    void realPricesSurviveAlongsideAStaleMarker() {
        Mockito.when(redisUtils.hashMapListAndKey(Mockito.anyList()))
                .thenReturn(Map.of("price:10010:H1:1:" + D1, Map.of(
                        PriceCacheServiceImpl.NO_INVENTORY_FIELD, "1",
                        "a".repeat(64), "{\"price\":12345}")));

        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> service.getPriceResult(req(1), sup()));
    }

    // ---------- 标记之前先摘掉旧报价（2026-09-05） ----------

    /**
     * 只贴标记不删旧价等于没贴：{@code hMSet} 是合并不是覆盖，而读侧 {@code getPriceResult}
     * 先判产品再判标记（有货优先），于是整店卖光的酒店继续挂在渠道列表上。
     *
     * <p>生产实证：春武里 Mybed Chonburi 2026-09-05 当晚整店零报价，19:19:30 与 19:19:34
     * 同一客人连点两次，两次都被告知 SPA_SOLD_OUT——因为列表上的价一直没撤。
     */
    @Test
    @DisplayName("刷到无在售 → 先摘掉这一片的旧报价，再贴标记")
    void emptyRefreshDropsStalePricesBeforeMarking() {
        Mockito.when(redisUtils.hashMapGet("price:10010:H1:1:" + D1))
                .thenReturn(Map.of("a".repeat(64), "{\"price\":12345}",
                        "b".repeat(64), "{\"price\":23456}"));

        service.productToCache(List.of(), req(1), sup());

        ArgumentCaptor<Map<String, Set<String>>> cap = ArgumentCaptor.forClass(Map.class);
        Mockito.verify(redisUtils).batchHashDelete(cap.capture());
        assertEquals(Set.of("a".repeat(64), "b".repeat(64)), cap.getValue().get("price:10010:H1:1:" + D1));
    }

    /** 先删后标：中间态是"没刷过"（读侧回不确定），比"既有价又有标记"=照报有货保守 */
    @Test
    @DisplayName("删除必须发生在写标记之前")
    void deletionHappensBeforeTheMarkerIsWritten() {
        Mockito.when(redisUtils.hashMapGet("price:10010:H1:1:" + D1))
                .thenReturn(Map.of("a".repeat(64), "{\"price\":12345}"));

        service.productToCache(List.of(), req(1), sup());

        InOrder order = Mockito.inOrder(redisUtils);
        order.verify(redisUtils).batchHashDelete(any());
        order.verify(redisUtils).batchHashMapSetWithExpire(any(), anyLong(), any(TimeUnit.class));
    }

    /** 标记自己不是旧报价，别把刚写的标记又删一遍 */
    @Test
    @DisplayName("已有的标记不进删除名单")
    void theExistingMarkerIsNotDeleted() {
        Mockito.when(redisUtils.hashMapGet("price:10010:H1:1:" + D1))
                .thenReturn(Map.of(PriceCacheServiceImpl.NO_INVENTORY_FIELD, "1"));

        service.productToCache(List.of(), req(1), sup());

        Mockito.verify(redisUtils, Mockito.never()).batchHashDelete(any());
    }

    /** 本来就没有旧价时不发无谓的 HDEL */
    @Test
    @DisplayName("这一片本来就是空的 → 不调删除")
    void nothingToDropMeansNoDeleteCall() {
        service.productToCache(List.of(), req(1), sup());

        Mockito.verify(redisUtils, Mockito.never()).batchHashDelete(any());
    }

    /**
     * 删除范围与标记同一口径：只碰本次问过的那一片。没问过的占用片里的价是活的，
     * 删了就是拿"我们没问"当"供应商说没有"（R-1.6）。
     */
    @Test
    @DisplayName("只摘本次问过的那一片，不外溢到别的占用")
    void deletionStaysInsideTheRefreshedShard() {
        Mockito.when(redisUtils.hashMapGet(Mockito.anyString()))
                .thenReturn(Map.of("a".repeat(64), "{\"price\":12345}"));

        service.productToCache(List.of(), req(1), sup());

        ArgumentCaptor<Map<String, Set<String>>> cap = ArgumentCaptor.forClass(Map.class);
        Mockito.verify(redisUtils).batchHashDelete(cap.capture());
        assertTrue(cap.getValue().keySet().stream().allMatch(k -> k.startsWith("price:10010:H1:1:")),
                "不该碰别的占用片: " + cap.getValue().keySet());
    }
}
