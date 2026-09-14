package com.trip.booking.spa.gateway.adapter.outbound.supplier.clwy.pricing;

import com.trip.booking.spa.gateway.adapter.outbound.state.offer.OfferStore;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.clwy.checkprice.ClwyCheckPriceServiceImpl;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.clwy.shared.ClwyProductKeyDeriver;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.clwy.shared.ClwyProperties;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.clwy.shared.ClwyTokenProvider;
import com.trip.booking.spa.gateway.application.checkprice.CheckPriceResult;
import com.trip.booking.spa.gateway.application.pricing.PricingResult;
import com.trip.booking.spa.gateway.domain.booking.CheckPriceOutcome;
import com.trip.booking.spa.gateway.domain.pricing.CheckPriceCommand;
import com.trip.booking.spa.gateway.domain.pricing.PriceQuery;
import com.trip.booking.spa.gateway.domain.product.PriceInfo;
import com.trip.booking.spa.gateway.domain.product.Product;
import com.trip.booking.spa.gateway.domain.booking.PricingOutcome;
import com.trip.booking.spa.gateway.domain.booking.VerifyLevel;
import com.trip.booking.spa.gateway.domain.supplier.SupplierSourceEnum;
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

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 差旅无忧查价／验价的<b>真 e2e</b>：跑本仓真实的 {@link ClwyPriceServiceImpl} 与验价模板，
 * 真实 HTTP 打<b>生产</b>端点（本家无沙箱）。
 *
 * <p><b>只读</b>：全程只调 {@code Authentication/GetToken} 与 {@code HotelProduct/GetPrice}。
 * 后者传 RatePlanId 时是官方所称的「预定页试单」，只换一个 10 分钟有效的 RateKey，
 * <b>不建单、不占房、不扣款</b>——真正下单是另一个端点（{@code /Order/Booking}，本批未接）。
 *
 * <p><b>运行方式</b>（默认跳过，不进 CI）：
 * <pre>
 * CLWY_E2E=1 CLWY_WID=… CLWY_API_KEY=… mvn test -Dtest=ClwyCheckPriceE2ETest
 * </pre>
 * 出网无需白名单（2026-09-14 实测腾讯云与阿里云出口都直接可达），本机若被墙可借白名单机：
 * {@code ssh -D 1080} 后加 {@code -DsocksProxyHost=127.0.0.1 -DsocksProxyPort=1080}。
 *
 * <p><b>为什么第 3 档大概率要走换票</b>：本家报价码分代轮换（cursor 取证：60 天 58 次重放仅
 * 4 次成功），"查价拿到的码到验价时还在"是小概率。这条 e2e 因此顺带验的就是生产真实路径。
 */
@EnabledIfEnvironmentVariable(named = "CLWY_E2E", matches = "1")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ClwyCheckPriceE2ETest {

    /** 通道层取过的限流键，按取用顺序。由本类装的假限流中枢填 */
    private static final List<String> TAKEN = new ArrayList<>();

    private static final String TOKEN_BUCKET = "GLOBAL_LIMIT:CLWY:SPA_SUPPLIER_API_AUTH_TOKEN";
    private static final String QUOTE_BUCKET = "GLOBAL_LIMIT:CLWY:SPA_SUPPLIER_API_PRODUCT_PRICES";
    private static final String TRIAL_BUCKET = "GLOBAL_LIMIT:CLWY:SPA_SUPPLIER_API_ORDER_PRICE";

    /** 2026-09-14 实测该店 T+7 有货（10 房型 230 条报价）；可用 CLWY_E2E_HOTEL 换一家 */
    private static final String HOTEL = System.getenv().getOrDefault("CLWY_E2E_HOTEL", "1537780");

    private static ClwyTokenProvider tokenProvider;
    private static ClwyPriceServiceImpl service;
    private static ClwyCheckPriceServiceImpl flow;
    private static String checkIn;
    private static String checkOut;

    /** 查价拿到的参照产品，供后续验价复用——同数据 A/B 是本测试的核心 */
    private static Product reference;

    @BeforeAll
    static void wireRealService() throws Exception {
        ClwyProperties props = new ClwyProperties();
        set(props, "wid", System.getenv("CLWY_WID"));
        set(props, "apiKey", System.getenv("CLWY_API_KEY"));
        String host = System.getenv("CLWY_API_HOST");
        set(props, "urlHost", host == null || host.isBlank() ? "https://availability.xiangdo.cn" : host);
        set(props, "currency", "CNY");
        set(props, "countryCode", "CN");
        // 打开 resolve：报价码分代轮换，查价那次拿到的码到验价时大概率已换代，
        // 按 productKey 换等价新票正是生产的真实路径，要一起验
        set(props, "resolveEnabled", Boolean.TRUE);
        set(props, "resolvePriceTolerance", 0.02D);
        set(props, "resolvePriceCapCents", 2000);
        assumeTrue(props.isConfigured(), "缺 CLWY_WID/CLWY_API_KEY，跳过");

        TAKEN.clear();
        RateLimitHolder holder = new RateLimitHolder();
        ApplicationContext ctx = mock(ApplicationContext.class);
        when(ctx.getBean(RateLimitManager.class)).thenReturn(new RateLimitManager() {
            @Override
            public void acquire(String key) {
                TAKEN.add(key);
            }

            @Override
            public boolean tryAcquire(String key) {
                TAKEN.add(key);
                return true;
            }

            @Override
            public boolean isRegistered(String key) {
                return true;
            }
        });
        holder.setApplicationContext(ctx);

        RedisUtils redis = mock(RedisUtils.class);
        when(redis.setex(anyString(), anyString(), anyLong())).thenReturn(true);
        OfferStore store = new OfferStore();
        set(store, "redisUtils", redis);
        set(store, "ttlSeconds", 600L);

        ClwyProductKeyDeriver deriver = new ClwyProductKeyDeriver();
        deriver.setProperties(props);

        tokenProvider = new ClwyTokenProvider();
        tokenProvider.setProperties(props);

        service = new ClwyPriceServiceImpl();
        set(service, "properties", props);
        set(service, "tokenProvider", tokenProvider);
        set(service, "productKeyDeriver", deriver);
        set(service, "offerStore", store);

        flow = new ClwyCheckPriceServiceImpl();
        set(flow, "clwyPriceService", service);
        set(flow, "properties", props);

        LocalDate in = LocalDate.now().plusDays(7);
        checkIn = in.toString();
        checkOut = in.plusDays(1).toString();
    }

    @Test
    @Order(1)
    @DisplayName("查价：真实调 GetPrice，逐晚之和等于总价，身份与报价码分列两字段")
    void queryPricesReturnsSellableProducts() {
        PriceQuery req = PriceQuery.builder()
                .supplierId(SupplierSourceEnum.CLWY.getCode()).supplierHotelId(HOTEL)
                .checkIn(checkIn).checkOut(checkOut)
                .roomNum(1).adultNum(2).childNum(0).childAges(new ArrayList<>()).build();

        PricingResult result = service.queryPrices(req, CallPurpose.LIVE);

        // 两级桶的键位只有真链路验得到。注意<b>顺序是查价桶在前、令牌桶在后</b>：
        // 通道层先扣本次调用的格再发请求，而续期是寄生在请求内部的，故令牌那两格后到。
        // 令牌桶带的用途要与它所服务的那次调用一致（这里是 LIVE），不能写死——
        // 后台刷价触发的续期若按前台口径快速失败，那一行刷价就白白失败一次。
        assertThat(TAKEN).as("查价的用途桶与接口桶各扣一格")
                .containsSubsequence(QUOTE_BUCKET + ":LIVE", QUOTE_BUCKET);
        assertThat(TAKEN).as("续期也走通道层的闸门，且用途跟随调用方")
                .containsSubsequence(TOKEN_BUCKET + ":LIVE", TOKEN_BUCKET);

        assumeTrue(result.outcome() != PricingOutcome.INDETERMINATE, "供应商未给出结果，本轮跳过");
        assumeTrue(result.outcome() == PricingOutcome.AVAILABLE, "该店该住期无在售产品，本轮跳过");

        List<Product> products = result.products();
        assertThat(products).isNotEmpty();
        for (Product p : products) {
            assertThat(p.getProductKey()).as("productKey 必须派生出来").isNotBlank();
            assertThat(p.getProductId()).as("报价码不得与身份键同字段").isNotEqualTo(p.getProductKey());
            assertThat(p.getTotalPrice()).as("住期总价").isPositive();
            assertThat(p.getCurrencyType()).as("币种必须来自报文").isNotBlank();
            assertThat(p.getRoom().getRoomId()).as("房型 id 必须透出").isNotBlank();
            int sum = p.getPriceInfos().stream().mapToInt(PriceInfo::getPrice).sum();
            assertThat(sum).as("一间一晚：总价=逐晚之和").isEqualTo(p.getTotalPrice());
        }
        reference = products.get(0);
        System.out.println("[clwy-e2e] 出报 " + products.size() + " 条，参照 productId=" + reference.getProductId()
                + " 房型=" + reference.getRoom().getRoomId() + " 总价=" + reference.getTotalPrice()
                + reference.getCurrencyType());
    }

    @Test
    @Order(2)
    @DisplayName("曝光档：只打查价，回 AVAILABLE 且不签句柄（不占 RateKey 的 10 分钟）")
    void availabilityLevelIssuesNoHandle() {
        assumeTrue(reference != null, "查价未取到参照产品，跳过");
        TAKEN.clear();

        CheckPriceResult resp = flow.checkPrice(command(VerifyLevel.AVAILABILITY));

        assertThat(TAKEN).as("曝光档不该碰试单桶").doesNotContain(TRIAL_BUCKET);
        assumeTrue(resp.getOutcome() != CheckPriceOutcome.INDETERMINATE, "未取得结果，跳过：" + resp.getMessage());
        assertThat(resp.getOutcome()).isIn(CheckPriceOutcome.AVAILABLE, CheckPriceOutcome.RATE_DEAD,
                CheckPriceOutcome.SOLD_OUT);
        if (resp.getOutcome() == CheckPriceOutcome.AVAILABLE) {
            assertThat(resp.getOfferId()).as("曝光档不签句柄").isNull();
            assertThat(resp.getSalePrice()).isPositive();
        }
        System.out.println("[clwy-e2e] 曝光档 -> " + resp.getOutcome() + " 价=" + resp.getSalePrice());
    }

    @Test
    @Order(3)
    @DisplayName("下单前档：试单拿 RateKey 并签句柄；报价码已换代时按 productKey 换等价票")
    void bookableLevelIssuesHandle() {
        assumeTrue(reference != null, "查价未取到参照产品，跳过");
        TAKEN.clear();

        CheckPriceResult resp = flow.checkPrice(command(VerifyLevel.BOOKABLE));

        assumeTrue(resp.getOutcome() != CheckPriceOutcome.INDETERMINATE, "未取得结果，跳过：" + resp.getMessage());
        System.out.println("[clwy-e2e] 下单前档 -> " + resp.getOutcome()
                + " 价=" + resp.getSalePrice() + resp.getCurrencyType()
                + " offerId=" + resp.getOfferId() + " 退改条数="
                + (resp.getCancelPolicy() == null ? 0 : resp.getCancelPolicy().size()));
        if (resp.getOutcome() == CheckPriceOutcome.BOOKABLE) {
            assertThat(TAKEN).as("这一档必须打试单").contains(TRIAL_BUCKET);
            assertThat(resp.getOfferId()).as("可订必须签出句柄，否则下单无从进行").isNotBlank();
            assertThat(resp.getOfferTtlSeconds()).as("句柄时效必须给出").isPositive();
            assertThat(resp.getSalePrice()).isPositive();
            assertThat(resp.getCurrencyType()).isNotBlank();
        } else {
            // 分代轮换是常态，不是失败——但它必须落在有判据的那两个终态上
            assertThat(resp.getOutcome()).isIn(CheckPriceOutcome.RATE_DEAD, CheckPriceOutcome.SOLD_OUT);
        }
    }

    @Test
    @Order(4)
    @DisplayName("刷价档：令牌续期跟着调用方的用途走，不写死成前台")
    void refreshPurposeReachesTheTokenBucket() {
        TAKEN.clear();
        // 先把缓存令牌丢掉，逼出一次真实续期——否则这一次调用不会碰令牌桶，测了个寂寞
        tokenProvider.invalidate();

        PriceQuery req = PriceQuery.builder()
                .supplierId(SupplierSourceEnum.CLWY.getCode()).supplierHotelId(HOTEL)
                .checkIn(checkIn).checkOut(checkOut)
                .roomNum(1).adultNum(2).childNum(0).childAges(new ArrayList<>()).build();

        service.queryPrices(req, CallPurpose.REFRESH);

        assertThat(TAKEN).as("刷价走 REFRESH 的用途桶").contains(QUOTE_BUCKET + ":REFRESH");
        // 这一条才是重点：续期寄生在刷价这次调用里，用途必须跟着它。写死成 LIVE 的话，
        // 后台刷价撞上前台桶满就会快速失败，那一行刷价白白失败一次（前台该等的反而在等）
        assertThat(TAKEN).as("续期的用途必须跟随刷价，而不是前台").contains(TOKEN_BUCKET + ":REFRESH");
        assertThat(TAKEN).as("续期不该借前台的名义扣格").doesNotContain(TOKEN_BUCKET + ":LIVE");
    }

    private static CheckPriceCommand command(VerifyLevel level) {
        return CheckPriceCommand.builder()
                .supplierId(SupplierSourceEnum.CLWY.getCode())
                .supplierHotelId(HOTEL)
                .supplierProductId(reference.getProductId())
                .productKey(reference.getProductKey())
                .verifyLevel(level)
                .checkIn(checkIn).checkOut(checkOut)
                .roomNum(1).adultCount(2).childNum(0).childAges(new ArrayList<>())
                .seenPrice(reference.getTotalPrice())
                .build();
    }

    private static void set(Object target, String field, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(field);
        f.setAccessible(true);
        f.set(target, value);
    }
}
