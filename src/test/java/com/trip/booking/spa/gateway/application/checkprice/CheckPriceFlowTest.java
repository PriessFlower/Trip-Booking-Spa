package com.trip.booking.spa.gateway.application.checkprice;

import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.CheckPriceRespDTO;
import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.ProductRespDTO;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.CheckPriceReq;
import com.trip.booking.spa.gateway.adapter.outbound.state.pricecache.PriceCacheService;
import com.trip.booking.spa.gateway.domain.booking.CheckPriceOutcome;
import com.trip.booking.spa.gateway.domain.booking.VerifyLevel;
import com.trip.booking.spa.gateway.domain.supplier.SupplierSourceEnum;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
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
        CheckPriceRespDTO precondition;
        CheckPriceRespDTO inspection;
        final List<String> calls = new ArrayList<>();

        StubFlow stock(String env, Map<String, Offer> stock) {
            stocks.put(env, LiveStock.of(stock));
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
        protected CheckPriceRespDTO precondition(CheckPriceReq request) {
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
        protected CheckPriceRespDTO inspect(String candidate, Map<String, Offer> stock, CheckPriceReq request) {
            calls.add("inspect:" + candidate);
            return inspection;
        }

        @Override
        protected CheckPriceRespDTO availabilityOnlyResp(String candidate, Map<String, Offer> stock, CheckPriceReq request) {
            calls.add("availability:" + candidate);
            return CheckPriceRespDTO.builder().outcome(CheckPriceOutcome.AVAILABLE).message(candidate).build();
        }

        @Override
        protected CheckPriceRespDTO validate(String candidate, Map<String, Offer> stock, CheckPriceReq request) {
            calls.add("validate:" + candidate);
            return CheckPriceRespDTO.builder().outcome(CheckPriceOutcome.BOOKABLE).message(candidate)
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

        CheckPriceRespDTO resp = flow.checkPrice(req(VerifyLevel.AVAILABILITY, "T1", KEY, 10000));

        assertEquals(CheckPriceOutcome.AVAILABLE, resp.getOutcome());
        assertTrue(flow.calls.contains("availability:T1"), flow.calls.toString());
        assertTrue(flow.calls.stream().noneMatch(c -> c.startsWith("validate:")), "曝光档不许打 validate：" + flow.calls);
        assertNull(resp.getOfferId(), "曝光档不签句柄");
    }

    @Test
    @DisplayName("BOOKABLE：找到票后必经 validate，由它给可订与句柄")
    void bookableTierAlwaysValidates() {
        StubFlow flow = new StubFlow().stock(null, stockOf("T1", KEY, 10000));

        CheckPriceRespDTO resp = flow.checkPrice(req(VerifyLevel.BOOKABLE, "T1", KEY, 10000));

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

        CheckPriceRespDTO resp = flow.checkPrice(req(VerifyLevel.BOOKABLE, "T1", KEY, 10000));

        assertEquals(CheckPriceOutcome.BOOKABLE, resp.getOutcome());
        assertEquals("T3", resp.getMessage(), "多张等价票选最便宜的（ResolveGate）");
        assertTrue(flow.calls.contains("validate:T3"), flow.calls.toString());
    }

    @Test
    @DisplayName("令牌死且现货无同卖法等价票：RATE_DEAD（未命中）")
    void noEquivalentIsRateDead() {
        StubFlow flow = new StubFlow().stock(null, stockOf("T2", "other", 9900));

        CheckPriceRespDTO resp = flow.checkPrice(req(VerifyLevel.AVAILABILITY, "T1", KEY, 10000));

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

        CheckPriceRespDTO resp = flow.checkPrice(req(VerifyLevel.BOOKABLE, "T1", null, 10000));

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
                .thenReturn(List.of(ProductRespDTO.builder().totalPrice(10000).build()));
        ReflectionTestUtils.setField(flow, "priceCacheService", cache);

        CheckPriceRespDTO resp = flow.checkPrice(req(VerifyLevel.BOOKABLE, "T1", KEY, null));

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
                CheckPriceRespDTO.builder().outcome(CheckPriceOutcome.SOLD_OUT).message("该住期已无任何可售报价").build()));

        CheckPriceRespDTO resp = flow.checkPrice(req(VerifyLevel.BOOKABLE, "T1", KEY, 10000));

        assertEquals(CheckPriceOutcome.SOLD_OUT, resp.getOutcome());
        assertEquals(List.of("precondition", "fetch:null"), flow.calls);
    }

    @Test
    @DisplayName("前置自检不过（凭证未配置）：不调供应商")
    void preconditionShortCircuits() {
        StubFlow flow = new StubFlow().stock(null, stockOf("T1", KEY, 10000));
        flow.precondition = CheckPriceRespDTO.builder().outcome(CheckPriceOutcome.INDETERMINATE).message("凭证未配置").build();

        CheckPriceRespDTO resp = flow.checkPrice(req(VerifyLevel.BOOKABLE, "T1", KEY, 10000));

        assertEquals(CheckPriceOutcome.INDETERMINATE, resp.getOutcome());
        assertEquals(List.of("precondition"), flow.calls);
    }

    @Test
    @DisplayName("找到票后的供应商自检拒绝（停售/床型不可选）：以自检结果为终态，不分档")
    void inspectionRejectsBeforeTiering() {
        StubFlow flow = new StubFlow().stock(null, stockOf("T1", KEY, 10000));
        flow.inspection = CheckPriceRespDTO.builder().outcome(CheckPriceOutcome.RATE_DEAD).message("该产品已停售").build();

        CheckPriceRespDTO resp = flow.checkPrice(req(VerifyLevel.AVAILABILITY, "T1", KEY, 10000));

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

        CheckPriceRespDTO resp = flow.checkPrice(req(VerifyLevel.BOOKABLE, "T1", KEY, 10000));

        assertEquals(CheckPriceOutcome.BOOKABLE, resp.getOutcome());
        assertTrue(flow.calls.contains("fetch:hotel_only") && flow.calls.contains("fetch:hotel_package"), flow.calls.toString());
    }

    @Test
    @DisplayName("多售卖环境：前一个是不确定或已售罄就停，再查既救不回也会掩盖成因")
    void nonRateDeadStopsTheChain() {
        StubFlow flow = new StubFlow().stock("hotel_package", stockOf("T1", KEY, 10000));
        flow.stocks.put("hotel_only", LiveStock.terminal(
                CheckPriceRespDTO.builder().outcome(CheckPriceOutcome.INDETERMINATE).message("查价调用未取得结果").build()));
        flow.environments = List.of("hotel_only", "hotel_package");

        CheckPriceRespDTO resp = flow.checkPrice(req(VerifyLevel.BOOKABLE, "T1", KEY, 10000));

        assertEquals(CheckPriceOutcome.INDETERMINATE, resp.getOutcome());
        assertTrue(flow.calls.stream().noneMatch("fetch:hotel_package"::equals), flow.calls.toString());
    }
}
