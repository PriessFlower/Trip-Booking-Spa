package com.trip.booking.spa.gateway.adapter.outbound.supplier.meituan.pricing;

import com.trip.booking.spa.gateway.adapter.outbound.state.offer.OfferStore;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.meituan.checkprice.MeituanCheckPriceServiceImpl;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.meituan.shared.MeituanProductKeyDeriver;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.meituan.shared.MeituanProperties;
import com.trip.booking.spa.gateway.application.checkprice.CheckPriceResult;
import com.trip.booking.spa.gateway.application.pricing.PricingResult;
import com.trip.booking.spa.gateway.domain.booking.CheckPriceOutcome;
import com.trip.booking.spa.gateway.domain.booking.PricingOutcome;
import com.trip.booking.spa.gateway.domain.booking.VerifyLevel;
import com.trip.booking.spa.gateway.domain.pricing.CheckPriceCommand;
import com.trip.booking.spa.gateway.domain.pricing.PriceQuery;
import com.trip.booking.spa.gateway.domain.product.PriceInfo;
import com.trip.booking.spa.gateway.domain.product.Product;
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
 * 美团查价／验价的<b>真 e2e</b>：跑本仓真实的 {@link MeituanPriceServiceImpl} 与验价模板，
 * 真实 HTTP 打<b>生产</b>端点（本家无沙箱）。
 *
 * <p><b>只读</b>：全程只调 {@code hotel.oversea.batch.goods.rp} 与 {@code hotel.oversea.order.check}。
 * 后者是官方的"下单前校验"，只做可订与库存校验并回最新价，<b>不建单、不占房、不扣款</b>——
 * 真正下单是另一个接口（{@code hotel.oversea.order.booking}，本批未接）。
 *
 * <p><b>运行方式</b>（默认跳过，不进 CI）：
 * <pre>
 * MEITUAN_E2E=1 MEITUAN_ACCESS_KEY=… MEITUAN_SECRET_KEY=… MEITUAN_PARTNER_ID=… \
 *   mvn test -Dtest=MeituanCheckPriceE2ETest
 * </pre>
 * 出网无需白名单（2026-09-14 实测腾讯云出口与本机都直接可达）。
 */
@EnabledIfEnvironmentVariable(named = "MEITUAN_E2E", matches = "1")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MeituanCheckPriceE2ETest {

    /** 通道层取过的限流键，按取用顺序。由本类装的假限流中枢填 */
    private static final List<String> TAKEN = new ArrayList<>();

    private static final String QUOTE_BUCKET = "GLOBAL_LIMIT:MEITUAN:SPA_SUPPLIER_API_PRODUCT_PRICES";
    private static final String CHECK_BUCKET = "GLOBAL_LIMIT:MEITUAN:SPA_SUPPLIER_API_ORDER_PRICE";

    /** 2026-09-14 实测该店 T+7 有货且支持多间；可用 MEITUAN_E2E_HOTEL 换一家 */
    private static final String HOTEL = System.getenv().getOrDefault("MEITUAN_E2E_HOTEL", "2388500");

    private static MeituanPriceServiceImpl service;
    private static MeituanCheckPriceServiceImpl flow;
    private static String checkIn;
    private static String checkOut;

    /** 查价拿到的参照产品，供后续验价复用——同数据 A/B 是本测试的核心 */
    private static Product reference;

    @BeforeAll
    static void wireRealService() throws Exception {
        MeituanProperties props = new MeituanProperties();
        set(props, "accessKey", System.getenv("MEITUAN_ACCESS_KEY"));
        set(props, "secretKey", System.getenv("MEITUAN_SECRET_KEY"));
        set(props, "partnerId", System.getenv("MEITUAN_PARTNER_ID"));
        String url = System.getenv("MEITUAN_API_URL");
        set(props, "url", url == null || url.isBlank() ? "https://fenxiao.meituan.com/opdtor/api/v2" : url);
        set(props, "currency", "USD");
        set(props, "clientNationality", "CN");
        set(props, "resolveEnabled", Boolean.TRUE);
        set(props, "resolvePriceTolerance", 0.02D);
        set(props, "resolvePriceCapCents", 2000);
        assumeTrue(props.isConfigured(), "缺 MEITUAN_ACCESS_KEY/MEITUAN_SECRET_KEY/MEITUAN_PARTNER_ID，跳过");

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

        MeituanProductKeyDeriver deriver = new MeituanProductKeyDeriver();
        deriver.setProperties(props);

        service = new MeituanPriceServiceImpl();
        service.setProperties(props);
        service.setProductKeyDeriver(deriver);
        service.setOfferStore(store);

        flow = new MeituanCheckPriceServiceImpl();
        set(flow, "meituanPriceService", service);
        set(flow, "properties", props);

        LocalDate in = LocalDate.now().plusDays(7);
        checkIn = in.toString();
        checkOut = in.plusDays(1).toString();
    }

    @Test
    @Order(1)
    @DisplayName("查价：真实调 batch.goods.rp，逐晚之和等于总价，身份与报价码分列两字段")
    void queryPricesReturnsSellableProducts() {
        PriceQuery req = PriceQuery.builder()
                .supplierId(SupplierSourceEnum.MEITUAN.getCode()).supplierHotelId(HOTEL)
                .checkIn(checkIn).checkOut(checkOut)
                .roomNum(1).adultNum(2).childNum(0).childAges(new ArrayList<>()).build();

        PricingResult result = service.queryPrices(req, CallPurpose.LIVE);

        // 两级桶的键位只有真链路验得到。本家<b>没有令牌</b>（每请求现签），故这条路上
        // 只该出现查价的两格，不该有第三个桶
        assertThat(TAKEN).as("查价的用途桶与接口桶各扣一格")
                .containsSubsequence(QUOTE_BUCKET + ":LIVE", QUOTE_BUCKET);
        assertThat(TAKEN).as("查价不该碰验价桶").doesNotContain(CHECK_BUCKET);

        assumeTrue(result.outcome() != PricingOutcome.INDETERMINATE, "供应商未给出结果，本轮跳过");
        assumeTrue(result.outcome() == PricingOutcome.AVAILABLE, "该店该住期无在售产品，本轮跳过");

        List<Product> products = result.products();
        assertThat(products).isNotEmpty();
        for (Product p : products) {
            assertThat(p.getProductKey()).as("productKey 必须派生出来").isNotBlank();
            assertThat(p.getProductId()).as("报价码不得与身份键同字段").isNotEqualTo(p.getProductKey());
            assertThat(p.getTotalPrice()).as("住期总价").isPositive();
            assertThat(p.getCurrencyType()).as("币种来自配置——本家报文不带币种").isEqualTo("USD");
            assertThat(p.getRoom().getRoomId()).as("物理房型 id 必须透出").isNotBlank();
            int sum = p.getPriceInfos().stream().mapToInt(PriceInfo::getPrice).sum();
            assertThat(sum).as("一间一晚：总价=逐晚之和").isEqualTo(p.getTotalPrice());
        }
        reference = products.get(0);
        System.out.println("[meituan-e2e] 出报 " + products.size() + " 条，参照 goodsId=" + reference.getProductId()
                + " 房型=" + reference.getRoom().getRoomId() + " 总价=" + reference.getTotalPrice()
                + reference.getCurrencyType());
    }

    @Test
    @Order(2)
    @DisplayName("曝光档：只打查价，回 AVAILABLE 且不签句柄（不占校验配额）")
    void availabilityLevelIssuesNoHandle() {
        assumeTrue(reference != null, "查价未取到参照产品，跳过");
        TAKEN.clear();

        CheckPriceResult resp = flow.checkPrice(command(VerifyLevel.AVAILABILITY, 1));

        assertThat(TAKEN).as("曝光档不该碰下单前校验").doesNotContain(CHECK_BUCKET);
        assumeTrue(resp.getOutcome() != CheckPriceOutcome.INDETERMINATE, "未取得结果，跳过：" + resp.getMessage());
        assertThat(resp.getOutcome()).isIn(CheckPriceOutcome.AVAILABLE, CheckPriceOutcome.RATE_DEAD,
                CheckPriceOutcome.SOLD_OUT);
        if (resp.getOutcome() == CheckPriceOutcome.AVAILABLE) {
            assertThat(resp.getOfferId()).as("曝光档不签句柄").isNull();
            assertThat(resp.getSalePrice()).isPositive();
        }
        System.out.println("[meituan-e2e] 曝光档 -> " + resp.getOutcome() + " 价=" + resp.getSalePrice());
    }

    @Test
    @Order(3)
    @DisplayName("下单前档：打 order.check 并签句柄；订不到时落在有判据的终态上")
    void bookableLevelIssuesHandle() {
        assumeTrue(reference != null, "查价未取到参照产品，跳过");
        TAKEN.clear();

        CheckPriceResult resp = flow.checkPrice(command(VerifyLevel.BOOKABLE, 1));

        assumeTrue(resp.getOutcome() != CheckPriceOutcome.INDETERMINATE, "未取得结果，跳过：" + resp.getMessage());
        System.out.println("[meituan-e2e] 下单前档 -> " + resp.getOutcome()
                + " 价=" + resp.getSalePrice() + resp.getCurrencyType()
                + " offerId=" + resp.getOfferId() + " 退改条数="
                + (resp.getCancelPolicy() == null ? 0 : resp.getCancelPolicy().size()));
        if (resp.getOutcome() == CheckPriceOutcome.BOOKABLE) {
            assertThat(TAKEN).as("这一档必须打下单前校验").contains(CHECK_BUCKET);
            assertThat(resp.getOfferId()).as("可订必须签出句柄，否则下单无从进行").isNotBlank();
            assertThat(resp.getOfferTtlSeconds()).as("句柄时效必须给出").isPositive();
            assertThat(resp.getSalePrice()).isPositive();
            assertThat(resp.getCurrencyType()).isEqualTo("USD");
        } else {
            assertThat(resp.getOutcome()).isIn(CheckPriceOutcome.RATE_DEAD, CheckPriceOutcome.SOLD_OUT);
        }
    }

    /**
     * 本家最容易搞错的那条口径，只有真链路验得到：<b>间数不进单价，但进罚金</b>。
     * 2026-09-14 首测即是这样定的案（两晚 1 间 15098/25164，2 间 30146/50244）。
     */
    @Test
    @Order(4)
    @DisplayName("多间：单价不随间数变，总价按间数乘；订不到多间时是确定终态不是不确定")
    void multiRoomScalesTotalNotUnitPrice() {
        assumeTrue(reference != null, "查价未取到参照产品，跳过");

        CheckPriceResult one = flow.checkPrice(command(VerifyLevel.BOOKABLE, 1));
        assumeTrue(one.getOutcome() == CheckPriceOutcome.BOOKABLE, "一间都订不到，无从比较");
        CheckPriceResult two = flow.checkPrice(command(VerifyLevel.BOOKABLE, 2));

        System.out.println("[meituan-e2e] 1 间 -> " + one.getSalePrice()
                + " / 2 间 -> " + two.getOutcome() + " " + two.getSalePrice());
        if (two.getOutcome() != CheckPriceOutcome.BOOKABLE) {
            // 只有一间库存是常态，但它必须落在"确定订不到"上——判成不确定会让上游一直重试
            assertThat(two.getOutcome()).as("订不到多间 = 确定终态")
                    .isIn(CheckPriceOutcome.SOLD_OUT, CheckPriceOutcome.RATE_DEAD);
            return;
        }
        int unitOne = one.getPriceInfos().stream().mapToInt(PriceInfo::getPrice).sum();
        int unitTwo = two.getPriceInfos().stream().mapToInt(PriceInfo::getPrice).sum();
        assertThat(unitTwo).as("逐晚明细是单间口径，不随间数变").isEqualTo(unitOne);
        assertThat(two.getSalePrice()).as("总价按间数乘（B4）").isEqualTo(unitTwo * 2);
    }

    @Test
    @Order(5)
    @DisplayName("刷价档：走 REFRESH 的用途桶，不借前台的名义")
    void refreshUsesItsOwnBucket() {
        TAKEN.clear();
        PriceQuery req = PriceQuery.builder()
                .supplierId(SupplierSourceEnum.MEITUAN.getCode()).supplierHotelId(HOTEL)
                .checkIn(checkIn).checkOut(checkOut)
                .roomNum(1).adultNum(2).childNum(0).childAges(new ArrayList<>()).build();

        service.queryPrices(req, CallPurpose.REFRESH);

        assertThat(TAKEN).as("刷价走 REFRESH 的用途桶").contains(QUOTE_BUCKET + ":REFRESH");
        assertThat(TAKEN).as("刷价不该借前台的名义扣格").doesNotContain(QUOTE_BUCKET + ":LIVE");
    }

    private static CheckPriceCommand command(VerifyLevel level, int rooms) {
        return CheckPriceCommand.builder()
                .supplierId(SupplierSourceEnum.MEITUAN.getCode())
                .supplierHotelId(HOTEL)
                .supplierProductId(reference.getProductId())
                .productKey(reference.getProductKey())
                .verifyLevel(level)
                .checkIn(checkIn).checkOut(checkOut)
                .roomNum(rooms).adultCount(2).childNum(0).childAges(new ArrayList<>())
                .seenPrice(reference.getTotalPrice() * rooms)
                .build();
    }

    private static void set(Object target, String field, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(field);
        f.setAccessible(true);
        f.set(target, value);
    }
}
