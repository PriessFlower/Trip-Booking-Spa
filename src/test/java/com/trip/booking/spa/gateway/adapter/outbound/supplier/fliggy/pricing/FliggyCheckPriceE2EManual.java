package com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.pricing;

import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.CheckPriceRespDTO;
import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.ProductRespDTO;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.CheckPriceReq;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.PriceReq;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.Supplier;
import com.trip.booking.spa.gateway.adapter.outbound.state.offer.OfferStore;
import com.trip.booking.spa.gateway.adapter.outbound.state.pricecache.PriceCacheService;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.checkprice.FliggyCheckPriceServiceImpl;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.shared.FliggyProductKeyDeriver;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.shared.FliggyProperties;
import com.trip.booking.spa.gateway.application.pricing.PricingResult;
import com.trip.booking.spa.gateway.domain.booking.CheckPriceOutcome;
import com.trip.booking.spa.gateway.domain.booking.PricingOutcome;
import com.trip.booking.spa.gateway.domain.booking.VerifyLevel;
import com.trip.booking.spa.platform.ratelimit.CallPurpose;
import com.trip.booking.spa.platform.ratelimit.RateLimitHolder;
import com.trip.booking.spa.platform.ratelimit.RateLimitManager;
import com.trip.booking.spa.platform.redis.RedisUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.ApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 飞猪验价<b>真链路</b> e2e：真凭据打真飞猪，走真实的验价入口
 * {@link FliggyCheckPriceServiceImpl}（模板流程：现取→找票→换票→分档→验价）。<b>手动跑</b>，CI 不含：
 * <pre>
 * set -a; . .env; set +a   # FLIGGY_* 六个
 * FLIGGY_E2E=1 mvn test -Dtest=FliggyCheckPriceE2EManual
 * </pre>
 *
 * <p>为什么必须有它：生产 2026-09-02~05 飞猪验价 8/18 RATE_DEAD，全因 rate_key 换代后精确匹配
 * 落空而飞猪没接 resolve；单元测试全绿。本测试拿<b>刚查到的真 productKey + 一把必死的 rate_key</b>
 * 去验价，换票必须把它救回来；再把闸口关掉复现 RATE_DEAD——反证换票是真的在起作用，
 * 不是恰好命中。
 *
 * <p>只读接口：全程只调 ari.availability 与 distribution.validate，<b>不会调 create</b>，
 * 不产生真单与费用。落缓存的副作用用 mock 挡掉。
 */
@EnabledIfEnvironmentVariable(named = "FLIGGY_E2E", matches = "1")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FliggyCheckPriceE2EManual {

    /** 新宿华盛顿：2026-08-27 实证在售（22 条报价） */
    private static final String HOTEL = "50363404";

    /** 形状合法、必不在现货里的 rate_key——模拟高德回传的上一代令牌 */
    private static final String DEAD_RATE_KEY = "V3|" + "0".repeat(64);

    private static FliggyProperties props;
    private static FliggyPriceServiceImpl service;
    private static FliggyCheckPriceServiceImpl flow;
    private static RedisUtils redis;
    private static String checkIn;
    private static String checkOut;

    /** 查价拿到的参照产品：真 rate_key、真 productKey、真价 */
    private static ProductRespDTO reference;

    @BeforeAll
    static void wireRealService() {
        props = new FliggyProperties();
        props.setAppKey(System.getenv("FLIGGY_APP_KEY"));
        props.setSecret(System.getenv("FLIGGY_SECRET"));
        props.setSession(System.getenv("FLIGGY_SESSION"));
        props.setDistributor(System.getenv("FLIGGY_DISTRIBUTOR"));
        String host = System.getenv("FLIGGY_API_HOST");
        props.setUrlHost(host == null || host.isBlank() ? "https://eco.taobao.com/router/rest" : host);
        props.setResolveEnabled(true);
        assumeTrue(props.isConfigured(), "缺 FLIGGY_APP_KEY/FLIGGY_SECRET/FLIGGY_SESSION（先 source .env）");

        // 限流中枢平时由 Spring 抄进静态桥；没起容器得手动装全放行实现，否则通道层 NPE
        RateLimitHolder holder = new RateLimitHolder();
        ApplicationContext ctx = mock(ApplicationContext.class);
        when(ctx.getBean(RateLimitManager.class)).thenReturn(new RateLimitManager() {
            @Override
            public void acquire(String key) {
            }

            @Override
            public boolean tryAcquire(String key) {
                return true;
            }

            @Override
            public boolean isRegistered(String key) {
                return true;
            }
        });
        holder.setApplicationContext(ctx);

        redis = mock(RedisUtils.class);
        when(redis.setex(anyString(), anyString(), anyLong())).thenReturn(true);
        OfferStore store = new OfferStore();
        ReflectionTestUtils.setField(store, "redisUtils", redis);
        ReflectionTestUtils.setField(store, "ttlSeconds", 600L);

        service = new FliggyPriceServiceImpl();
        ReflectionTestUtils.setField(service, "properties", props);
        ReflectionTestUtils.setField(service, "productKeyDeriver", new FliggyProductKeyDeriver(props));
        ReflectionTestUtils.setField(service, "offerStore", store);
        ReflectionTestUtils.setField(service, "priceCacheService", mock(PriceCacheService.class));

        flow = new FliggyCheckPriceServiceImpl();
        ReflectionTestUtils.setField(flow, "fliggyPriceService", service);
        ReflectionTestUtils.setField(flow, "properties", props);

        LocalDate in = LocalDate.now(ZoneId.of("Asia/Shanghai")).plusDays(13);
        checkIn = in.toString();
        checkOut = in.plusDays(1).toString();
    }

    @Test
    @Order(1)
    @DisplayName("查价：真实调 ari.availability，取参照产品（rate_key / productKey / 价）")
    void queryPricesGivesReference() {
        PriceReq req = PriceReq.builder().checkIn(checkIn).checkout(checkOut)
                .roomNum(1).adultNum(2).childNum(0).childAges(List.of()).build();

        PricingResult result = service.queryPrices(req,
                Supplier.builder().supplierId(10015).sHotelId(HOTEL).build(), CallPurpose.LIVE);

        assumeTrue(result.outcome() != PricingOutcome.INDETERMINATE, "飞猪未给出结果（网络/凭据），本轮跳过");
        assumeTrue(result.outcome() == PricingOutcome.AVAILABLE, "该店该住期无在售，本轮跳过");
        List<ProductRespDTO> products = result.products();
        assertThat(products).isNotEmpty();
        reference = products.get(0);
        assertThat(reference.getProductKey()).isNotBlank();
        assertThat(reference.getProductId()).as("rate_key 与 productKey 永不同字段").isNotEqualTo(reference.getProductKey());
        assertThat(reference.getTotalPrice()).isPositive();
    }

    @Test
    @Order(2)
    @DisplayName("曝光档·令牌在：AVAILABLE、不签句柄")
    void availabilityWithLiveTokenIsAvailable() {
        assumeTrue(reference != null, "查价未取到参照产品，跳过");

        CheckPriceRespDTO resp = flow.checkPrice(req(VerifyLevel.AVAILABILITY, reference.getProductId()));

        assumeTrue(resp.getOutcome() != CheckPriceOutcome.INDETERMINATE, "飞猪未给出结果，跳过");
        assertThat(resp.getOutcome()).isEqualTo(CheckPriceOutcome.AVAILABLE);
        assertThat(resp.getOfferId()).as("曝光档不签句柄").isNull();
        assertThat(resp.getSalePrice()).isPositive();
        assertThat(resp.getCurrencyType()).isNotBlank();
    }

    @Test
    @Order(3)
    @DisplayName("曝光档·令牌死：按 productKey 换票救回，AVAILABLE——这就是生产 8/18 RATE_DEAD 的场景")
    void availabilityWithDeadTokenIsResolved() {
        assumeTrue(reference != null, "查价未取到参照产品，跳过");

        CheckPriceRespDTO resp = flow.checkPrice(req(VerifyLevel.AVAILABILITY, DEAD_RATE_KEY));

        assumeTrue(resp.getOutcome() != CheckPriceOutcome.INDETERMINATE, "飞猪未给出结果，跳过");
        assertThat(resp.getOutcome())
                .as("令牌死了但同卖法在售，换票必须救回；RATE_DEAD 即 resolve 没起作用（%s）", resp.getMessage())
                .isEqualTo(CheckPriceOutcome.AVAILABLE);
        assertThat(resp.getOfferId()).isNull();
    }

    @Test
    @Order(4)
    @DisplayName("反证：闸口关闭，同一把死令牌必须 RATE_DEAD——证明上一条是换票救的，不是碰巧")
    void gateClosedReproducesRateDead() {
        assumeTrue(reference != null, "查价未取到参照产品，跳过");
        props.setResolveEnabled(false);
        try {
            CheckPriceRespDTO resp = flow.checkPrice(req(VerifyLevel.AVAILABILITY, DEAD_RATE_KEY));

            assumeTrue(resp.getOutcome() != CheckPriceOutcome.INDETERMINATE, "飞猪未给出结果，跳过");
            assertThat(resp.getOutcome()).isEqualTo(CheckPriceOutcome.RATE_DEAD);
        } finally {
            props.setResolveEnabled(true);
        }
    }

    @Test
    @Order(5)
    @DisplayName("下单前档·令牌死：换票后必经 validate——不得回 AVAILABLE，也不得 RATE_DEAD")
    void bookableWithDeadTokenValidatesTheSwappedRate() {
        assumeTrue(reference != null, "查价未取到参照产品，跳过");

        CheckPriceRespDTO resp = flow.checkPrice(req(VerifyLevel.BOOKABLE, DEAD_RATE_KEY));

        assertThat(resp.getOutcome()).as("这一档打了验价，不该回 AVAILABLE").isNotEqualTo(CheckPriceOutcome.AVAILABLE);
        assertThat(resp.getOutcome()).as("换票应已救回；RATE_DEAD 即 resolve 未生效（%s）", resp.getMessage())
                .isNotEqualTo(CheckPriceOutcome.RATE_DEAD);
        assumeTrue(resp.getOutcome() == CheckPriceOutcome.BOOKABLE,
                "本轮 validate 未通过（" + resp.getOutcome() + "：" + resp.getMessage() + "），句柄断言跳过");
        assertThat(resp.getOfferId()).as("BOOKABLE 必然带句柄").isNotBlank();
        assertThat(resp.getOfferTtlSeconds()).isPositive();
        assertThat(resp.getSalePrice()).isPositive();
    }

    private static CheckPriceReq req(VerifyLevel level, String rateKey) {
        return CheckPriceReq.builder()
                .supplierId(10015).sHotelId(HOTEL)
                .sProductId(rateKey)
                .productKey(reference.getProductKey())
                .seenPrice(reference.getTotalPrice())
                .checkIn(checkIn).checkOut(checkOut)
                .roomNum(1).adultCount(2).childNum(0).childAges(List.of())
                .verifyLevel(level)
                .build();
    }
}
