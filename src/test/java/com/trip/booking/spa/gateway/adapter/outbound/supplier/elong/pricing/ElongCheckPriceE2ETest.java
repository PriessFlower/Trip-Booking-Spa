package com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.pricing;

import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.CheckPriceRespDTO;
import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.PriceInfo;
import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.ProductRespDTO;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.CheckPriceReq;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.PriceReq;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.Supplier;
import com.trip.booking.spa.gateway.adapter.outbound.state.offer.OfferStore;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared.ElongOfferCredentials;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared.ElongProductKeyDeriver;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared.ElongProperties;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared.model.request.ElongDataValidateRequest;
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
import org.apache.commons.lang3.StringUtils;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationContext;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 艺龙查价／验价的<b>真 e2e</b>：跑本仓真实的 {@link ElongPriceServiceImpl}，
 * 真实 HTTP 打<b>生产</b>艺龙网关，验的是我方代码本身而不是手搓报文。
 *
 * <p><b>为什么必须是真 e2e</b>：2026-08-21 的排查里，"申报口径改成 Rate 能不能通"一直是用
 * python 复刻报文验的——那只证明了协议可行，没证明我方代码发出去的报文一样。两者之间隔着
 * 一层（当时就发现脚本的 {@code EarliestArrivalTime} 与代码不同）。单测钉住取值、协议测试
 * 钉住报文，中间那段只有真跑代码才盖得住。
 *
 * <p><b>挡掉的只有落库与落缓存的副作用</b>：{@code elongQueryPriceTaskMapper}（反馈环升档，
 * 服务内已 try/catch 兜住）与 {@code priceCacheService}（resolve 的容差基准反查，内部
 * try/catch 返 null）留空；{@link OfferStore} 用真实实现 + 假 {@link RedisUtils}，以便断言
 * 句柄里真正写进去了什么。供应商调用、响应解析、productKey 派生、分态映射、凭据组装
 * <b>全部是真的</b>。
 *
 * <p><b>运行方式</b>（默认跳过，不进 CI）：
 * <pre>
 * ELONG_E2E=1 ELONG_USER=… ELONG_APP_KEY=… ELONG_SECRET=… \
 *   mvn test -Dtest=ElongCheckPriceE2ETest
 * </pre>
 * 出网 IP 须在艺龙白名单内（否则报 {@code A101010012 访问IP错误}，错误文案里会带上它看到的
 * 那个 IP——以那个为准，不要用 ifconfig.me，本机可能有多条出网路径）。
 *
 * <p>只读接口：全程只调 {@code hotel.detail} 与 {@code hotel.data.validate}，
 * <b>不会调 hotel.order.create</b>，不产生真单与费用。
 */
@EnabledIfEnvironmentVariable(named = "ELONG_E2E", matches = "1")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ElongCheckPriceE2ETest {

    /** 通道层取过的限流键，按取用顺序。由本类装的假限流中枢填 */
    private static final java.util.List<String> TAKEN = new java.util.ArrayList<>();

    private static final String DETAIL_BUCKET = "GLOBAL_LIMIT:ELONG:SPA_SUPPLIER_API_PRODUCT_PRICES";

    /** 越南大叻·沙非大叻酒店：2026-08 期间持续有在售产品，且 Member 与 Rate 明显不等 */
    private static final String HOTEL = "61497910";

    private static ElongPriceServiceImpl service;
    private static RedisUtils redis;
    private static String checkIn;
    private static String checkOut;

    /** 查价拿到的参照产品，供后续两档验价复用——同数据 A/B 是本测试的核心 */
    private static ProductRespDTO reference;

    @BeforeAll
    static void wireRealService() throws Exception {
        ElongProperties props = new ElongProperties();
        set(props, "user", System.getenv("ELONG_USER"));
        set(props, "appKey", System.getenv("ELONG_APP_KEY"));
        set(props, "secret", System.getenv("ELONG_SECRET"));
        String host = System.getenv("ELONG_API_HOST");
        set(props, "urlHost", host == null || host.isBlank() ? "https://api.elong.com/rest" : host);
        set(props, "version", "1.62");
        // 打开 resolve：报价码是会话级易腐的，查价那一次拿到的 GoodsUniqId 到验价时大概率
        // 已经换代，正门就是按 productKey 换等价新票——这正是生产的真实路径，要一起验
        set(props, "resolveEnabled", Boolean.TRUE);
        set(props, "resolvePriceTolerance", 0.02D);
        set(props, "resolvePriceCapCents", 2000);
        assumeTrue(props.isConfigured(), "缺 ELONG_USER/ELONG_APP_KEY/ELONG_SECRET，跳过");

        // 限流中枢平时由 Spring 启动时抄进静态桥（RateLimitHolder）；这里没起容器，
        // 得手动装一个全放行的实现，否则 BaseHttpAccess 拿到 null 直接 NPE。
        // 放行而不是真限流：e2e 只跑个位数次调用，且真限流会把测试变成不可复现的等待。
        // 但它<b>记录取过哪些键</b>——真实调用时通道层到底按什么键扣格，是这套两级桶
        // 唯一能在真链路上验到的事实（配置与注释都可能与代码分叉）。
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
                // 当作"用途桶已登记"，使两级桶都被取到——否则验不到用途段拼得对不对
                return true;
            }
        });
        holder.setApplicationContext(ctx);

        redis = mock(RedisUtils.class);
        when(redis.setex(anyString(), anyString(), anyLong())).thenReturn(true);
        OfferStore store = new OfferStore();
        set(store, "redisUtils", redis);
        set(store, "ttlSeconds", 600L);

        // 派生器自己也持有 properties——productKey 的 account 成分取自 supplier.elong.user，
        // 漏注的话真跑到派生那一步才 NPE（本测试第一次跑就是这么发现的）
        ElongProductKeyDeriver deriver = new ElongProductKeyDeriver();
        set(deriver, "properties", props);

        service = new ElongPriceServiceImpl();
        set(service, "properties", props);
        set(service, "productKeyDeriver", deriver);
        set(service, "offerStore", store);

        LocalDate in = LocalDate.now().plusDays(3);
        checkIn = in.toString();
        checkOut = in.plusDays(1).toString();
    }

    private static void set(Object target, String field, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(field);
        f.setAccessible(true);
        f.set(target, value);
    }

    private static Supplier supplier() {
        return Supplier.builder().supplierId(10010).sHotelId(HOTEL).build();
    }

    @Test
    @Order(1)
    @DisplayName("查价：真实调 hotel.detail，价格取结算口径且税费自洽")
    void queryPricesUsesSettlementBasis() {
        PriceReq req = PriceReq.builder().checkIn(checkIn).checkout(checkOut)
                .roomNum(1).adultNum(1).childNum(0).childAges(new ArrayList<>()).guestType(0).build();

        PricingResult result = service.queryPrices(req, supplier(), CallPurpose.LIVE);

        // 两级桶：真链路上通道层必须先扣用途桶、再扣接口桶。这条断言放在 assumeTrue 之前——
        // 扣格发生在调用之前，与艺龙给不给货无关；放在后面会被"无在售就跳过"吞掉
        assertThat(TAKEN).as("实时查价这条路必须扣 :LIVE 用途桶与接口桶各一格")
                .containsSubsequence(DETAIL_BUCKET + ":LIVE", DETAIL_BUCKET);

        assumeTrue(result.outcome() != PricingOutcome.INDETERMINATE,
                "艺龙未给出结果（超时/限流），本轮跳过");
        assumeTrue(result.outcome() == PricingOutcome.AVAILABLE,
                "该店该住期无在售产品，本轮跳过");

        List<ProductRespDTO> products = result.products();
        assertThat(products).isNotEmpty();
        for (ProductRespDTO p : products) {
            assertThat(p.getProductKey()).as("productKey 必须派生出来").isNotBlank();
            assertThat(p.getProductId()).as("报价码不得与身份键同字段").isNotEqualTo(p.getProductKey());
            assertThat(p.getTotalPrice()).as("含税总额").isPositive();

            int sumPrice = p.getPriceInfos().stream().mapToInt(PriceInfo::getPrice).sum();
            assertThat(sumPrice).as("总额须等于逐日之和").isEqualTo(p.getTotalPrice());

            for (PriceInfo info : p.getPriceInfos()) {
                assertThat(info.getRoomPrice() + info.getTaxes())
                        .as("price = roomPrice + taxes（国际对接指南 ①②）").isEqualTo(info.getPrice());
                assertThat(info.getTaxes()).as("Rate 是含税价，税费必须为正——恒 0 即口径回退")
                        .isPositive();
            }
            assertThat(p.getRoomTotalPrice()).as("税前房费应低于含税总额").isLessThan(p.getTotalPrice());
        }
        reference = products.get(0);
    }

    @Test
    @Order(2)
    @DisplayName("渠道验价档：真实只打 detail，回 AVAILABLE 且不签句柄")
    void availabilityLevelIssuesNoHandle() {
        assumeTrue(reference != null, "查价未取到参照产品，跳过");

        TAKEN.clear();
        CheckPriceRespDTO resp = service.checkPrices(req(VerifyLevel.AVAILABILITY));

        // 点订前的现取现验走 :CHECK_PRICE 而不是 :REFRESH——同一个 hotel.detail 接口，
        // 两路各占一个用途桶。这一路是客人在等，与后台刷价必须分开计额
        assertThat(TAKEN).as("现取现验必须扣 :CHECK_PRICE 用途桶与接口桶各一格")
                .containsSubsequence(DETAIL_BUCKET + ":CHECK_PRICE", DETAIL_BUCKET);
        assertThat(TAKEN).as("客流这一路不得扣到刷价的用途桶")
                .doesNotContain(DETAIL_BUCKET + ":REFRESH");

        assumeTrue(resp.getOutcome() != CheckPriceOutcome.INDETERMINATE, "艺龙未给出结果，跳过");
        assertThat(resp.getOutcome()).isEqualTo(CheckPriceOutcome.AVAILABLE);
        assertThat(resp.getOfferId()).as("这一档没验价，不得签发句柄").isNull();
        assertThat(resp.getSalePrice()).isPositive();
        assertThat(resp.getPriceInfos()).isNotEmpty();
        for (PriceInfo info : resp.getPriceInfos()) {
            assertThat(info.getRoomPrice() + info.getTaxes()).isEqualTo(info.getPrice());
            assertThat(info.getTaxes()).isPositive();
        }
    }

    @Test
    @Order(3)
    @DisplayName("下单前验价档：真实打 detail+validate，通过则句柄里的申报价等于展示价")
    void bookableLevelDeclaresTheSameBasisItShows() {
        assumeTrue(reference != null, "查价未取到参照产品，跳过");

        CheckPriceRespDTO resp = service.checkPrices(req(VerifyLevel.BOOKABLE));

        assertThat(resp.getOutcome()).as("四态之内，不得出现别的值")
                .isIn(CheckPriceOutcome.BOOKABLE, CheckPriceOutcome.AVAILABLE,
                        CheckPriceOutcome.SOLD_OUT, CheckPriceOutcome.RATE_DEAD,
                        CheckPriceOutcome.INDETERMINATE);
        assertThat(resp.getOutcome()).as("这一档打了验价，不该回 AVAILABLE")
                .isNotEqualTo(CheckPriceOutcome.AVAILABLE);
        assumeTrue(resp.getOutcome() == CheckPriceOutcome.BOOKABLE,
                "本轮验价未通过（" + resp.getOutcome() + "），可订性断言跳过");

        assertThat(resp.getOfferId()).as("BOOKABLE 必然带句柄").isNotBlank();
        assertThat(resp.getSalePrice()).isPositive();

        // 句柄里真正写进 Redis 的那份凭据——申报给艺龙的价就是它
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(redis).setex(anyString(), body.capture(), anyLong());
        String json = body.getValue();
        assertThat(json).contains("\"" + ElongOfferCredentials.DECLARED_TOTAL + "\"");

        BigDecimal declared = declaredTotalOf(json);
        assertThat(declared).as("申报价须为正").isPositive();
        // 申报价（元）与对客展示价（分）必须同口径：都是 Σ Rate。此前展示走 Member、
        // 申报走 Member，验价却把 salePrice 换成了 validate 的 Rate，三者不同源
        assertThat(declared.multiply(BigDecimal.valueOf(100)).intValue())
                .as("申报价与展示价必须同口径（差异仅允许来自数秒内的价格刷新）")
                .isCloseTo(resp.getSalePrice(), org.assertj.core.data.Percentage.withPercentage(2));
    }

    @Test
    @Order(4)
    @DisplayName("H001189 自纠正：故意抬高 MinRate 触发拒绝，看重试能否真的救回来")
    void selfCorrectsPerDayPriceMismatchAgainstProduction() throws Exception {
        // 前三个用例只在偏差恰好落在容差内时跑过，自纠正那条分支<b>一次都没走到</b>。
        // 而 MinRate 是我方传入的，抬高它必然撞 H001189——这是唯一能对着真实供应商
        // 验证自纠正的办法（此前该逻辑的唯一证据是白天那批 python 脚本，不是我方代码）
        CheckPriceReq request = req(VerifyLevel.BOOKABLE);

        Method fetch = ElongPriceServiceImpl.class.getDeclaredMethod("queryHotelDetail",
                String.class, String.class, String.class, Integer.class, Integer.class, Integer.class, List.class);
        fetch.setAccessible(true);
        @SuppressWarnings("unchecked")
        var detail = (com.trip.booking.spa.platform.http.asynchttp.ResponseResult
                <com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared.model.response.ElongHotelDetailResponse>)
                fetch.invoke(service, HOTEL, checkIn, checkOut, 1, 1, 0, new ArrayList<Integer>());
        assumeTrue(detail != null && detail.getData() != null && detail.getData().isSucc(), "detail 未取到，跳过");
        assumeTrue(!detail.getData().isEmptyResult(), "该店该住期无在售，跳过");

        var hotel = detail.getData().getResult().getHotels().get(0);
        var plan = hotel.getRooms().stream()
                .flatMap(r -> r.getRatePlans() == null ? java.util.stream.Stream.empty() : r.getRatePlans().stream())
                .filter(p -> StringUtils.isNotBlank(p.getLittleMajiaId()) && p.getNightlyRates() != null
                        && p.getNightlyRates().stream().allMatch(n -> n.getRate() != null && n.getMinRate() != null))
                .findFirst().orElse(null);
        assumeTrue(plan != null, "没有可用报价，跳过");

        // 抬高 MinRate 0.5 元 → 申报税费变小 → 必然低于艺龙算的税费 → H001189。
        // Price 保持 Σ Rate 不动，所以申报总价与正常路径完全一致，不多付一分钱
        BigDecimal inflated = plan.getNightlyRates().get(0).getMinRate().add(new BigDecimal("0.50"));
        List<ElongDataValidateRequest.DayPrice> tampered = new ArrayList<>();
        for (var night : plan.getNightlyRates()) {
            tampered.add(ElongDataValidateRequest.DayPrice.builder()
                    .date(night.getDate().substring(0, 10))
                    .price(night.getRate())
                    .minRate(night.getMinRate().add(new BigDecimal("0.50")))
                    .build());
        }

        Method validate = ElongPriceServiceImpl.class.getDeclaredMethod("validate",
                CheckPriceReq.class, String.class,
                com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared.model.response.ElongRatePlan.class,
                List.class);
        validate.setAccessible(true);
        CheckPriceRespDTO resp = (CheckPriceRespDTO) validate.invoke(service, request, HOTEL, plan, tampered);

        assumeTrue(resp.getOutcome() != CheckPriceOutcome.INDETERMINATE
                        || !StringUtils.contains(resp.getMessage(), "未取得结果"),
                "艺龙未给出结果（超时/限流），跳过");
        // 断言自纠正真的生效：抬高过的 MinRate 必然先被拒，能走到 BOOKABLE 只可能是
        // 重试用艺龙回传的 MinRate 救回来的
        assertThat(resp.getOutcome())
                .as("抬高 MinRate 后仍应通过——靠的是 H001189 自纠正重试。实际=%s，消息=%s",
                        resp.getOutcome(), resp.getMessage())
                .isEqualTo(CheckPriceOutcome.BOOKABLE);
        assertThat(resp.getOfferId()).isNotBlank();

        // 句柄里的 MinRate 必须是艺龙回传的那份，不能是被我抬高过的
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(redis, org.mockito.Mockito.atLeastOnce()).setex(anyString(), body.capture(), anyLong());
        String json = body.getAllValues().get(body.getAllValues().size() - 1);
        assertThat(json).as("句柄不得留下被抬高的 MinRate")
                .doesNotContain(inflated.toPlainString());
    }

    @Test
    @Order(5)
    @DisplayName("多间下单前验价：roomNum=2 真打 validate，不得再撞 H001188")
    void multiRoomBookableDeclaresRoomMultipliedTotal() {
        assumeTrue(reference != null, "查价未取到参照产品，跳过");

        CheckPriceRespDTO resp = service.checkPrices(req(VerifyLevel.BOOKABLE, 2));

        // 针对 2026-08-23 高德 2 间真单（26082320295835a66d8b13dd）的拒因：TotalPrice
        // 漏乘间数 → H001188|每日价传参异常。修复后该码不允许复现；SOLD_OUT/RATE_DEAD
        // 是市场态，属可接受结果，故先断言拒因、再对可订态做条件断言
        assertThat(StringUtils.defaultString(resp.getMessage()))
                .as("H001188=申报总价与间数不匹配（本次修复对象）。实际态=%s", resp.getOutcome())
                .doesNotContain("H001188");
        assumeTrue(resp.getOutcome() == CheckPriceOutcome.BOOKABLE,
                "本轮 2 间验价未通过（" + resp.getOutcome() + "：" + resp.getMessage() + "），后续断言跳过");

        assertThat(resp.getOfferId()).isNotBlank();
        // 句柄里间数与整单申报价绑定；申报价与展示价同为整单口径
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(redis, org.mockito.Mockito.atLeastOnce()).setex(anyString(), body.capture(), anyLong());
        String json = body.getAllValues().get(body.getAllValues().size() - 1);
        assertThat(json).contains("\"" + ElongOfferCredentials.ROOM_NUM + "\":\"2\"");
        assertThat(declaredTotalOf(json).multiply(BigDecimal.valueOf(100)).intValue())
                .as("申报价（元→分）与展示价必须同为整单口径")
                .isCloseTo(resp.getSalePrice(), org.assertj.core.data.Percentage.withPercentage(2));
    }

    private static CheckPriceReq req(VerifyLevel level) {
        return req(level, 1);
    }

    private static CheckPriceReq req(VerifyLevel level, int roomNum) {
        return CheckPriceReq.builder()
                .supplierId(10010).sHotelId(HOTEL)
                .sProductId(reference.getProductId())
                .productKey(reference.getProductKey())
                .seenPrice(reference.getTotalPrice())
                .checkIn(checkIn).checkOut(checkOut)
                .roomNum(roomNum).adultCount(1).childNum(0).childAges(new ArrayList<>())
                .verifyLevel(level)
                .build();
    }

    private static BigDecimal declaredTotalOf(String offerJson) {
        String key = "\"" + ElongOfferCredentials.DECLARED_TOTAL + "\":\"";
        int i = offerJson.indexOf(key);
        assertThat(i).as("句柄里必须存有申报价").isGreaterThan(-1);
        int from = i + key.length();
        return new BigDecimal(offerJson.substring(from, offerJson.indexOf('"', from)));
    }
}
