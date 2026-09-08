package com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.pricing;

import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.CheckPriceRespDTO;
import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.PriceInfo;
import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.ProductRespDTO;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.CheckPriceReq;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.PriceReq;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.Supplier;
import com.trip.booking.spa.gateway.adapter.outbound.state.offer.OfferStore;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.checkprice.DidaCheckPriceServiceImpl;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.DidaProductKeyDeriver;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.DidaProperties;
import com.trip.booking.spa.gateway.application.pricing.PricingResult;
import com.trip.booking.spa.gateway.domain.booking.CheckPriceOutcome;
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
 * 道旅查价／验价的<b>真 e2e</b>：跑本仓真实的 {@link DidaPriceServiceImpl} 与验价模板，
 * 真实 HTTP 打<b>生产</b>道旅端点（道旅无沙箱，官方 pricesearch 注 15：测试账号已废除）。
 *
 * <p>只读接口：全程只调 {@code pricesearch} 与 {@code PriceConfirm}，
 * <b>不会调 HotelBookingConfirm</b>，不产生真单与费用。PriceConfirm 的 PreBook 只是取
 * 一个 2 小时有效的 ReferenceNo，不占房不扣款。
 *
 * <p><b>运行方式</b>（默认跳过，不进 CI）：
 * <pre>
 * DIDA_E2E=1 DIDA_CLIENT_ID=… DIDA_LICENSE_KEY=… mvn test -Dtest=DidaCheckPriceE2ETest
 * </pre>
 * 出网 IP 必须在道旅白名单内，否则报 {@code 2017 Invalid ip/signature}（2026-09-08 实测：
 * 本机美国出口被拒，腾讯云 trip-offline 与阿里云 tg_server1 通）。本机跑法是先开
 * {@code ssh -D 1080} 到白名单机，再加 {@code -DsocksProxyHost=127.0.0.1 -DsocksProxyPort=1080}。
 *
 * <p><b>为什么第 2、3 档几乎必然要走换票</b>：道旅 RatePlanID 腐得极快（2026-09-08 实测
 * 同参数间隔 3 秒重查即换代），所以"查价拿到的码到验价时还在"是小概率。这条 e2e 因此
 * 顺带验的就是生产真实路径：现取现验 → 令牌已死 → 按 productKey 换等价票。
 */
@EnabledIfEnvironmentVariable(named = "DIDA_E2E", matches = "1")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DidaCheckPriceE2ETest {

    /** 通道层取过的限流键，按取用顺序。由本类装的假限流中枢填 */
    private static final List<String> TAKEN = new ArrayList<>();

    private static final String SEARCH_BUCKET = "GLOBAL_LIMIT:DIDA:SPA_SUPPLIER_API_PRODUCT_PRICES";

    private static final String CONFIRM_BUCKET = "GLOBAL_LIMIT:DIDA:SPA_SUPPLIER_API_ORDER_PRICE";

    /** 东京·亚细亚会馆：2026-09-08 实测该店报价条数适中（17 条），住期覆盖稳定 */
    private static final String HOTEL = "563";

    private static DidaPriceServiceImpl service;

    private static DidaCheckPriceServiceImpl flow;

    private static String checkIn;

    private static String checkOut;

    /** 查价拿到的参照产品，供后续两档验价复用——同数据 A/B 是本测试的核心 */
    private static ProductRespDTO reference;

    @BeforeAll
    static void wireRealService() throws Exception {
        DidaProperties props = new DidaProperties();
        set(props, "clientId", System.getenv("DIDA_CLIENT_ID"));
        set(props, "licenseKey", System.getenv("DIDA_LICENSE_KEY"));
        String host = System.getenv("DIDA_API_HOST");
        set(props, "urlHost", host == null || host.isBlank() ? "https://api.didatravel.com" : host);
        set(props, "currency", "CNY");
        set(props, "nationality", "CN");
        // 打开 resolve：报价码腐得极快，查价那次拿到的 RatePlanID 到验价时大概率已换代，
        // 正门就是按 productKey 换等价新票——这正是生产的真实路径，要一起验
        set(props, "resolveEnabled", Boolean.TRUE);
        set(props, "resolvePriceTolerance", 0.02D);
        set(props, "resolvePriceCapCents", 2000);
        assumeTrue(props.isConfigured(), "缺 DIDA_CLIENT_ID/DIDA_LICENSE_KEY，跳过");

        // 限流中枢平时由 Spring 启动时抄进静态桥；这里没起容器，装一个全放行的实现，
        // 但它记录取过哪些键——通道层到底按什么键扣格，只有真链路验得到
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

        DidaProductKeyDeriver deriver = new DidaProductKeyDeriver();
        set(deriver, "properties", props);

        service = new DidaPriceServiceImpl();
        set(service, "properties", props);
        set(service, "productKeyDeriver", deriver);
        set(service, "offerStore", store);

        flow = new DidaCheckPriceServiceImpl();
        set(flow, "didaPriceService", service);
        set(flow, "properties", props);

        LocalDate in = LocalDate.now().plusDays(21);
        checkIn = in.toString();
        checkOut = in.plusDays(1).toString();
    }

    private static void set(Object target, String field, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(field);
        f.setAccessible(true);
        f.set(target, value);
    }

    private static Supplier supplier() {
        return Supplier.builder().supplierId(SupplierSourceEnum.DIDA.getCode()).sHotelId(HOTEL).build();
    }

    @Test
    @Order(1)
    @DisplayName("查价：真实调 pricesearch，逐日之和等于总价，身份与报价码分列两字段")
    void queryPricesReturnsSellableProducts() {
        PriceReq req = PriceReq.builder().checkIn(checkIn).checkout(checkOut)
                .roomNum(1).adultNum(2).childNum(0).childAges(new ArrayList<>()).build();

        PricingResult result = service.queryPrices(req, supplier(), CallPurpose.LIVE);

        // 两级桶：扣格发生在调用之前，与道旅给不给货无关，故断言放在 assume 之前
        assertThat(TAKEN).as("实时查价这条路必须扣 :LIVE 用途桶与接口桶各一格")
                .containsSubsequence(SEARCH_BUCKET + ":LIVE", SEARCH_BUCKET);

        assumeTrue(result.outcome() != PricingOutcome.INDETERMINATE, "道旅未给出结果，本轮跳过");
        assumeTrue(result.outcome() == PricingOutcome.AVAILABLE, "该店该住期无在售产品，本轮跳过");

        List<ProductRespDTO> products = result.products();
        assertThat(products).isNotEmpty();
        for (ProductRespDTO p : products) {
            assertThat(p.getProductKey()).as("productKey 必须派生出来").isNotBlank();
            assertThat(p.getProductId()).as("报价码不得与身份键同字段").isNotEqualTo(p.getProductKey());
            assertThat(p.getTotalPrice()).as("住期总价").isPositive();
            assertThat(p.getCurrencyType()).isEqualTo("CNY");
            assertThat(p.getRoom().getRoomId()).as("房型 id 必须透出（B8）").isNotBlank();

            int sumPrice = p.getPriceInfos().stream().mapToInt(PriceInfo::getPrice).sum();
            assertThat(sumPrice).as("总价须等于逐晚之和").isEqualTo(p.getTotalPrice());
            assertThat(p.getPriceInfos()).hasSize(1);
        }
        reference = products.get(0);
    }

    @Test
    @Order(2)
    @DisplayName("曝光档：只打 pricesearch，回 AVAILABLE 且不签句柄")
    void availabilityLevelIssuesNoHandle() {
        assumeTrue(reference != null, "查价未取到参照产品，跳过");

        TAKEN.clear();
        CheckPriceRespDTO resp = flow.checkPrice(req(VerifyLevel.AVAILABILITY));

        assertThat(TAKEN).as("现取现验必须扣 :CHECK_PRICE 用途桶与接口桶各一格")
                .containsSubsequence(SEARCH_BUCKET + ":CHECK_PRICE", SEARCH_BUCKET);
        assertThat(TAKEN).as("客流这一路不得扣到刷价的用途桶")
                .doesNotContain(SEARCH_BUCKET + ":REFRESH");
        assertThat(TAKEN).as("曝光档不许打验价接口").doesNotContain(CONFIRM_BUCKET);

        assumeTrue(resp.getOutcome() != CheckPriceOutcome.INDETERMINATE, "道旅未给出结果，跳过");
        assertThat(resp.getOutcome()).isEqualTo(CheckPriceOutcome.AVAILABLE);
        assertThat(resp.getOfferId()).as("这一档没验价，不得签发句柄").isNull();
        assertThat(resp.getSalePrice()).isPositive();
        assertThat(resp.getPriceInfos()).isNotEmpty();
    }

    @Test
    @Order(3)
    @DisplayName("下单前档：真实打 pricesearch+PriceConfirm，通过则签出带 ReferenceNo 的句柄")
    void bookableLevelIssuesHandleWithReferenceNo() {
        assumeTrue(reference != null, "查价未取到参照产品，跳过");

        TAKEN.clear();
        CheckPriceRespDTO resp = flow.checkPrice(req(VerifyLevel.BOOKABLE));

        assertThat(resp.getOutcome()).as("这一档打了验价，不该回 AVAILABLE")
                .isNotEqualTo(CheckPriceOutcome.AVAILABLE);
        assumeTrue(resp.getOutcome() == CheckPriceOutcome.BOOKABLE,
                "本轮验价未通过（" + resp.getOutcome() + "），可订性断言跳过");

        assertThat(TAKEN).as("BOOKABLE 必然打过验价接口").contains(CONFIRM_BUCKET);
        assertThat(resp.getOfferId()).as("BOOKABLE 必然带句柄").isNotBlank();
        assertThat(resp.getOfferTtlSeconds()).as("句柄必须带时效").isPositive();
        assertThat(resp.getSalePrice()).isPositive();
        assertThat(resp.getCurrencyType()).isEqualTo("CNY");
        assertThat(resp.getPriceInfos()).isNotEmpty();
    }

    private static CheckPriceReq req(VerifyLevel level) {
        return CheckPriceReq.builder()
                .supplierId(SupplierSourceEnum.DIDA.getCode())
                .sHotelId(HOTEL)
                .sProductId(reference.getProductId())
                .productKey(reference.getProductKey())
                // 换票容差的尺子。本测试不装 PriceCacheService（回写与反查都会静默跳过），
                // 故基准必须由入参给，否则 resolve 会以"无基准"拒绝换票
                .seenPrice(reference.getTotalPrice())
                .verifyLevel(level)
                .checkIn(checkIn).checkOut(checkOut)
                .roomNum(1).adultCount(2).childNum(0).childAges(new ArrayList<>())
                .build();
    }
}
