package com.trip.booking.spa.gateway.application.checkprice;

import com.trip.booking.spa.gateway.application.checkprice.CheckPriceResult;
import com.trip.booking.spa.gateway.domain.product.Product;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.CheckPriceReq;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.Supplier;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.PriceReq;
import com.trip.booking.spa.gateway.adapter.outbound.state.pricecache.PriceCacheService;
import com.trip.booking.spa.gateway.domain.booking.CheckPriceOutcome;
import com.trip.booking.spa.gateway.domain.booking.VerifyLevel;
import com.trip.booking.spa.gateway.domain.supplier.SupplierSourceEnum;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 钉死验价流程模板的顺序与分支：现取 → 找票 → 换票 → 自检 → 分档 → 验价。
 *
 * <p>现货是「令牌→价格」表，票就是令牌字串。每个钩子都记录自己被调过没有——
 * 「AVAILABILITY 不打 validate」「BOOKABLE 必打 validate」「令牌死了先换票」这三条
 * 是用例的骨架，其它用例覆盖换票的五种未命中成因与多售卖环境的续试规则。
 */
class CheckPriceFlowTest {

    private static final String KEY = "k".repeat(64);

    /** 现货：令牌 → (productKey, 价格分) */
    record Offer(String productKey, int priceCents) {
    }

    static class Props implements ResolveProperties {
        boolean enabled = true;
        double tolerance = 0.02;
        int capCents = 2000;

        @Override
        public boolean isResolveEnabled() {
            return enabled;
        }

        @Override
        public double getResolvePriceTolerance() {
            return tolerance;
        }

        @Override
        public int getResolvePriceCapCents() {
            return capCents;
        }
    }

    static class StubFlow extends AbstractCheckPriceFlow<Map<String, Offer>, String> {
        final Props props = new Props();
        /** 每个售卖环境各一份现货；null 环境用 stocks.get(null) */
        final Map<String, LiveStock<Map<String, Offer>>> stocks = new LinkedHashMap<>();
        List<String> environments = java.util.Collections.singletonList(null);
        CheckPriceResult precondition;
        CheckPriceResult inspection;
        final List<String> calls = new ArrayList<>();

        /** 验价即刷：转换器返回什么由用例设定；null=不挂（该家没有即刷） */
        java.util.function.Function<PriceReq, List<Product>> freshConverter;

        StubFlow stock(String env, Map<String, Offer> stock) {
            LiveStock<Map<String, Offer>> live = LiveStock.of(stock);
            if (freshConverter != null) {
                live = live.freshConvertedBy(freshConverter);
            }
            stocks.put(env, live);
            return this;
        }

        @Override
        protected SupplierSourceEnum supplier() {
            return SupplierSourceEnum.FLIGGY;
        }

        @Override
        protected ResolveProperties resolveProperties() {
            return props;
        }

        @Override
        protected CheckPriceResult precondition(CheckPriceReq request) {
            calls.add("precondition");
            return precondition;
        }

        @Override
        protected List<String> salesEnvironments(CheckPriceReq request) {
            return environments;
        }

        @Override
        protected LiveStock<Map<String, Offer>> fetchLiveStock(CheckPriceReq request, String salesEnvironment) {
            calls.add("fetch:" + salesEnvironment);
            return stocks.get(salesEnvironment);
        }

        @Override
        protected String findByToken(Map<String, Offer> stock, CheckPriceReq request) {
            calls.add("find");
            return stock.containsKey(request.getSProductId()) ? request.getSProductId() : null;
        }

        @Override
        protected List<ResolveCandidate<String>> resolveCandidates(Map<String, Offer> stock, CheckPriceReq request) {
            calls.add("candidates");
            List<ResolveCandidate<String>> out = new ArrayList<>();
            stock.forEach((token, offer) -> {
                if (request.getProductKey().equals(offer.productKey())) {
                    out.add(new ResolveCandidate<>(token, offer.priceCents()));
                }
            });
            return out;
        }

        @Override
        protected String tokenOf(String candidate) {
            return candidate;
        }

        @Override
        protected CheckPriceResult inspect(String candidate, Map<String, Offer> stock, CheckPriceReq request) {
            calls.add("inspect:" + candidate);
            return inspection;
        }

        @Override
        protected CheckPriceResult availabilityOnlyResp(String candidate, Map<String, Offer> stock, CheckPriceReq request) {
            calls.add("availability:" + candidate);
            return CheckPriceResult.builder().outcome(CheckPriceOutcome.AVAILABLE).message(candidate).build();
        }

        @Override
        protected CheckPriceResult validate(String candidate, Map<String, Offer> stock, CheckPriceReq request) {
            calls.add("validate:" + candidate);
            return CheckPriceResult.builder().outcome(CheckPriceOutcome.BOOKABLE).message(candidate)
                    .offerId("offer-" + candidate).offerTtlSeconds(600L).build();
        }
    }

    private static CheckPriceReq req(VerifyLevel level, String token, String productKey, Integer seenPrice) {
        return CheckPriceReq.builder()
                .supplierId(10015).sHotelId("50366597").sProductId(token).productKey(productKey)
                .checkIn("2026-09-30").checkOut("2026-10-01").roomNum(1).adultCount(1).childNum(0)
                .seenPrice(seenPrice).verifyLevel(level)
                .build();
    }

    private static Map<String, Offer> stockOf(Object... tokenKeyPrice) {
        Map<String, Offer> stock = new LinkedHashMap<>();
        for (int i = 0; i < tokenKeyPrice.length; i += 3) {
            stock.put((String) tokenKeyPrice[i], new Offer((String) tokenKeyPrice[i + 1], (Integer) tokenKeyPrice[i + 2]));
        }
        return stock;
    }

    // ---------- 分档 ----------

    @Test
    @DisplayName("AVAILABILITY：找到票即回有货，不打 validate")
    void availabilityTierNeverValidates() {
        StubFlow flow = new StubFlow().stock(null, stockOf("T1", KEY, 10000));

        CheckPriceResult resp = flow.checkPrice(req(VerifyLevel.AVAILABILITY, "T1", KEY, 10000));

        assertEquals(CheckPriceOutcome.AVAILABLE, resp.getOutcome());
        assertTrue(flow.calls.contains("availability:T1"), flow.calls.toString());
        assertTrue(flow.calls.stream().noneMatch(c -> c.startsWith("validate:")), "曝光档不许打 validate：" + flow.calls);
        assertNull(resp.getOfferId(), "曝光档不签句柄");
    }

    @Test
    @DisplayName("BOOKABLE：找到票后必经 validate，由它给可订与句柄")
    void bookableTierAlwaysValidates() {
        StubFlow flow = new StubFlow().stock(null, stockOf("T1", KEY, 10000));

        CheckPriceResult resp = flow.checkPrice(req(VerifyLevel.BOOKABLE, "T1", KEY, 10000));

        assertEquals(CheckPriceOutcome.BOOKABLE, resp.getOutcome());
        assertEquals(List.of("precondition", "fetch:null", "find", "inspect:T1", "validate:T1"), flow.calls);
    }

    @Test
    @DisplayName("verifyLevel 缺省按 BOOKABLE：老调用方不带这个字段，不能因此少验")
    void missingVerifyLevelValidates() {
        StubFlow flow = new StubFlow().stock(null, stockOf("T1", KEY, 10000));

        flow.checkPrice(req(null, "T1", KEY, 10000));

        assertTrue(flow.calls.contains("validate:T1"), flow.calls.toString());
    }

    // ---------- 换票 ----------

    @Test
    @DisplayName("令牌死了：按 productKey 换等价新票再验，不判 RATE_DEAD")
    void deadTokenIsSwappedBeforeDeclaringRateDead() {
        StubFlow flow = new StubFlow().stock(null, stockOf("T2", KEY, 10100, "T3", KEY, 9900, "T4", "other", 100));

        CheckPriceResult resp = flow.checkPrice(req(VerifyLevel.BOOKABLE, "T1", KEY, 10000));

        assertEquals(CheckPriceOutcome.BOOKABLE, resp.getOutcome());
        assertEquals("T3", resp.getMessage(), "多张等价票选最便宜的（ResolveGate）");
        assertTrue(flow.calls.contains("validate:T3"), flow.calls.toString());
    }

    @Test
    @DisplayName("令牌死且现货无同卖法等价票：RATE_DEAD（未命中）")
    void noEquivalentIsRateDead() {
        StubFlow flow = new StubFlow().stock(null, stockOf("T2", "other", 9900));

        CheckPriceResult resp = flow.checkPrice(req(VerifyLevel.AVAILABILITY, "T1", KEY, 10000));

        assertEquals(CheckPriceOutcome.RATE_DEAD, resp.getOutcome());
        assertTrue(flow.calls.contains("candidates"));
        assertTrue(flow.calls.stream().noneMatch(c -> c.startsWith("availability:") || c.startsWith("validate:")));
    }

    @Test
    @DisplayName("等价票存在但超容差（100 元 vs 103 元 > 2%）：RATE_DEAD，不静默涨价成交")
    void overToleranceIsRateDead() {
        StubFlow flow = new StubFlow().stock(null, stockOf("T2", KEY, 10300));

        assertEquals(CheckPriceOutcome.RATE_DEAD,
                flow.checkPrice(req(VerifyLevel.BOOKABLE, "T1", KEY, 10000)).getOutcome());
    }

    @Test
    @DisplayName("上游未携 productKey：没有身份就没有等价的定义，不去算候选，直接 RATE_DEAD")
    void missingProductKeySkipsResolve() {
        StubFlow flow = new StubFlow().stock(null, stockOf("T2", KEY, 9900));

        CheckPriceResult resp = flow.checkPrice(req(VerifyLevel.BOOKABLE, "T1", null, 10000));

        assertEquals(CheckPriceOutcome.RATE_DEAD, resp.getOutcome());
        assertTrue(flow.calls.stream().noneMatch("candidates"::equals), "无键不该去算候选：" + flow.calls);
    }

    @Test
    @DisplayName("闸口 resolve-enabled 关闭：行为与不换票一致，RATE_DEAD")
    void gateClosedKeepsRateDead() {
        StubFlow flow = new StubFlow().stock(null, stockOf("T2", KEY, 9900));
        flow.props.enabled = false;

        assertEquals(CheckPriceOutcome.RATE_DEAD,
                flow.checkPrice(req(VerifyLevel.BOOKABLE, "T1", KEY, 10000)).getOutcome());
        assertTrue(flow.calls.stream().noneMatch("candidates"::equals));
    }

    @Test
    @DisplayName("上游未携 seenPrice：基准从缓存反查，反查到就换票")
    void baselineFallsBackToCache() {
        StubFlow flow = new StubFlow().stock(null, stockOf("T2", KEY, 10100));
        PriceCacheService cache = Mockito.mock(PriceCacheService.class);
        Mockito.when(cache.getPrice(Mockito.any(), Mockito.any(), Mockito.eq(KEY)))
                .thenReturn(List.of(Product.builder().totalPrice(10000).build()));
        ReflectionTestUtils.setField(flow, "priceCacheService", cache);

        CheckPriceResult resp = flow.checkPrice(req(VerifyLevel.BOOKABLE, "T1", KEY, null));

        assertEquals(CheckPriceOutcome.BOOKABLE, resp.getOutcome());
        assertEquals("T2", resp.getMessage());
    }

    @Test
    @DisplayName("上游未携 seenPrice 且缓存反查不到：无基准不换票，RATE_DEAD")
    void noBaselineRefusesToSwap() {
        StubFlow flow = new StubFlow().stock(null, stockOf("T2", KEY, 9900));
        PriceCacheService cache = Mockito.mock(PriceCacheService.class);
        Mockito.when(cache.getPrice(Mockito.any(), Mockito.any(), Mockito.any())).thenReturn(List.of());
        ReflectionTestUtils.setField(flow, "priceCacheService", cache);

        assertEquals(CheckPriceOutcome.RATE_DEAD,
                flow.checkPrice(req(VerifyLevel.BOOKABLE, "T1", KEY, null)).getOutcome());
    }

    // ---------- 终态与自检 ----------

    @Test
    @DisplayName("现取给出终态（如整店无售）：原样返回，不找票")
    void terminalFromFetchIsReturnedAsIs() {
        StubFlow flow = new StubFlow();
        flow.stocks.put(null, LiveStock.terminal(
                CheckPriceResult.builder().outcome(CheckPriceOutcome.SOLD_OUT).message("该住期已无任何可售报价").build()));

        CheckPriceResult resp = flow.checkPrice(req(VerifyLevel.BOOKABLE, "T1", KEY, 10000));

        assertEquals(CheckPriceOutcome.SOLD_OUT, resp.getOutcome());
        assertEquals(List.of("precondition", "fetch:null"), flow.calls);
    }

    @Test
    @DisplayName("前置自检不过（凭证未配置）：不调供应商")
    void preconditionShortCircuits() {
        StubFlow flow = new StubFlow().stock(null, stockOf("T1", KEY, 10000));
        flow.precondition = CheckPriceResult.builder().outcome(CheckPriceOutcome.INDETERMINATE).message("凭证未配置").build();

        CheckPriceResult resp = flow.checkPrice(req(VerifyLevel.BOOKABLE, "T1", KEY, 10000));

        assertEquals(CheckPriceOutcome.INDETERMINATE, resp.getOutcome());
        assertEquals(List.of("precondition"), flow.calls);
    }

    @Test
    @DisplayName("找到票后的供应商自检拒绝（停售/床型不可选）：以自检结果为终态，不分档")
    void inspectionRejectsBeforeTiering() {
        StubFlow flow = new StubFlow().stock(null, stockOf("T1", KEY, 10000));
        flow.inspection = CheckPriceResult.builder().outcome(CheckPriceOutcome.RATE_DEAD).message("该产品已停售").build();

        CheckPriceResult resp = flow.checkPrice(req(VerifyLevel.AVAILABILITY, "T1", KEY, 10000));

        assertEquals(CheckPriceOutcome.RATE_DEAD, resp.getOutcome());
        assertEquals("该产品已停售", resp.getMessage());
        assertTrue(flow.calls.stream().noneMatch(c -> c.startsWith("availability:")));
    }

    // ---------- 多售卖环境 ----------

    @Test
    @DisplayName("多售卖环境：前一个确证 RATE_DEAD 才试下一个")
    void nextSalesEnvironmentOnlyAfterRateDead() {
        StubFlow flow = new StubFlow()
                .stock("hotel_only", stockOf("X", "other", 1))
                .stock("hotel_package", stockOf("T1", KEY, 10000));
        flow.environments = List.of("hotel_only", "hotel_package");

        CheckPriceResult resp = flow.checkPrice(req(VerifyLevel.BOOKABLE, "T1", KEY, 10000));

        assertEquals(CheckPriceOutcome.BOOKABLE, resp.getOutcome());
        assertTrue(flow.calls.contains("fetch:hotel_only") && flow.calls.contains("fetch:hotel_package"), flow.calls.toString());
    }

    @Test
    @DisplayName("多售卖环境：前一个是不确定或已售罄就停，再查既救不回也会掩盖成因")
    void nonRateDeadStopsTheChain() {
        StubFlow flow = new StubFlow().stock("hotel_package", stockOf("T1", KEY, 10000));
        flow.stocks.put("hotel_only", LiveStock.terminal(
                CheckPriceResult.builder().outcome(CheckPriceOutcome.INDETERMINATE).message("查价调用未取得结果").build()));
        flow.environments = List.of("hotel_only", "hotel_package");

        CheckPriceResult resp = flow.checkPrice(req(VerifyLevel.BOOKABLE, "T1", KEY, 10000));

        assertEquals(CheckPriceOutcome.INDETERMINATE, resp.getOutcome());
        assertTrue(flow.calls.stream().noneMatch("fetch:hotel_package"::equals), flow.calls.toString());
    }

    // ---------- 验价即刷（机制归模板，2026-09-07 由两家各一份上提）----------

    private static PriceCacheService cacheSpy() {
        return Mockito.mock(PriceCacheService.class);
    }

    private static StubFlow flowWithCache(PriceCacheService cache) {
        StubFlow flow = new StubFlow();
        ReflectionTestUtils.setField(flow, "priceCacheService", cache);
        return flow;
    }

    @Test
    @DisplayName("验价即刷：转换器给出产品 → 回写缓存，占用键随验价走")
    void freshStockIsWrittenBackUnderTheCheckOccupancy() {
        PriceCacheService cache = cacheSpy();
        StubFlow flow = flowWithCache(cache);
        flow.freshConverter = priceReq -> List.of(Product.builder().productId("T1").build());
        flow.stock(null, stockOf("T1", KEY, 10000));

        flow.freshStockToCache(req(VerifyLevel.AVAILABILITY, "T1", KEY, 10000), flow.freshConverter);

        ArgumentCaptor<PriceReq> pr = ArgumentCaptor.forClass(PriceReq.class);
        ArgumentCaptor<Supplier> sp = ArgumentCaptor.forClass(Supplier.class);
        Mockito.verify(cache).productToCache(Mockito.anyList(), pr.capture(), sp.capture());
        assertEquals("1", pr.getValue().getOccupancies().get(0),
                "占用键必须随验价走——写成别的档即静默错键");
        assertEquals("2026-09-30", pr.getValue().getCheckIn());
        assertEquals("2026-10-01", pr.getValue().getCheckout(), "CheckPriceReq.checkOut → PriceReq.checkout");
        assertEquals(SupplierSourceEnum.FLIGGY.getCode(), sp.getValue().getSupplierId());
        assertEquals("50366597", sp.getValue().getSHotelId());
    }

    @Test
    @DisplayName("验价即刷：转换器给 null → 不动缓存（F-5.1 一次抖动不许清在售价）")
    void freshNullMeansDoNotTouchTheCache() {
        PriceCacheService cache = cacheSpy();
        StubFlow flow = flowWithCache(cache);

        flow.freshStockToCache(req(VerifyLevel.AVAILABILITY, "T1", KEY, 10000), priceReq -> null);

        // 用 any() 而不是 anyList()：后者不匹配 null，会让"把 null 送进缓存"这个 bug 假绿
        Mockito.verify(cache, Mockito.never()).productToCache(Mockito.any(), Mockito.any(), Mockito.any());
    }

    @Test
    @DisplayName("验价即刷：转换器给空列表 → 照样回写（模板据此落无货标记清僵尸价 B7）")
    void freshEmptyListStillWritesSoTheMarkerLands() {
        PriceCacheService cache = cacheSpy();
        StubFlow flow = flowWithCache(cache);

        flow.freshStockToCache(req(VerifyLevel.AVAILABILITY, "T1", KEY, 10000), priceReq -> List.of());

        ArgumentCaptor<List> products = ArgumentCaptor.forClass(List.class);
        Mockito.verify(cache).productToCache(products.capture(), Mockito.any(), Mockito.any());
        assertEquals(0, products.getValue().size());
    }

    @Test
    @DisplayName("验价即刷：回写炸了只落日志，绝不外抛（验价主流程不受影响）")
    void freshWriteFailureIsSwallowed() {
        PriceCacheService cache = cacheSpy();
        Mockito.doThrow(new RuntimeException("redis down"))
                .when(cache).productToCache(Mockito.anyList(), Mockito.any(), Mockito.any());
        StubFlow flow = flowWithCache(cache);

        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> flow.freshStockToCache(
                req(VerifyLevel.AVAILABILITY, "T1", KEY, 10000),
                priceReq -> List.of(Product.builder().productId("T1").build())));
    }

    @Test
    @DisplayName("验价即刷：终态也要写——下架/整店无售正是要落无货标记的时候")
    void terminalStockStillTriggersTheFreshWrite() {
        PriceCacheService cache = cacheSpy();
        StubFlow flow = flowWithCache(cache);
        flow.freshConverter = priceReq -> List.of();
        flow.stocks.put(null, LiveStock.<Map<String, Offer>>terminal(
                        CheckPriceResult.builder().outcome(CheckPriceOutcome.SOLD_OUT).build())
                .freshConvertedBy(flow.freshConverter));

        CheckPriceResult resp = flow.checkPrice(req(VerifyLevel.AVAILABILITY, "T1", KEY, 10000));

        assertEquals(CheckPriceOutcome.SOLD_OUT, resp.getOutcome());
        // 异步池：等回写落地（单线程池，提交即有序）
        Mockito.verify(cache, Mockito.timeout(2000)).productToCache(Mockito.anyList(), Mockito.any(), Mockito.any());
    }

    @Test
    @DisplayName("没挂转换器的家（如 Expedia）：模板一个字节都不碰缓存")
    void suppliersWithoutFreshWriteAreUntouched() {
        PriceCacheService cache = cacheSpy();
        StubFlow flow = flowWithCache(cache);
        flow.stock(null, stockOf("T1", KEY, 10000));   // freshConverter 未设 = 不挂

        flow.checkPrice(req(VerifyLevel.AVAILABILITY, "T1", KEY, 10000));

        Mockito.verify(cache, Mockito.never()).productToCache(Mockito.anyList(), Mockito.any(), Mockito.any());
    }
}
