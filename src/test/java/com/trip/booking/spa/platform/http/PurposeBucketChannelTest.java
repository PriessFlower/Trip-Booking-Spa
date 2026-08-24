package com.trip.booking.spa.platform.http;

import com.trip.booking.spa.gateway.domain.supplier.SupplierDataTypeEnum;
import com.trip.booking.spa.gateway.domain.supplier.SupplierSourceEnum;
import com.trip.booking.spa.platform.exception.RedisLimitException;
import com.trip.booking.spa.platform.http.asynchttp.BaseResponse;
import com.trip.booking.spa.platform.http.asynchttp.IParser;
import com.trip.booking.spa.platform.http.asynchttp.ResponseResult;
import com.trip.booking.spa.platform.observability.MonitorNameEnum;
import com.trip.booking.spa.platform.ratelimit.CallPurpose;
import com.trip.booking.spa.platform.ratelimit.RateLimitHolder;
import com.trip.booking.spa.platform.ratelimit.RateLimitManager;
import com.trip.booking.spa.platform.ratelimit.RateLimitManagerImpl;
import com.trip.booking.spa.platform.ratelimit.RateLimitProperties;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 通道层两级桶的<b>行为</b>验证：用真的 {@link RateLimitManagerImpl}（真 Guava 令牌桶）+
 * 生产 Nacos 的真实取值，观察扣了哪些键、拿不到许可时是等还是走。
 *
 * <p>唯一被替掉的是网络那一段（{@link #request} 直接返回一个成功响应）——被测的是"许可怎么取"，
 * 不是"HTTP 怎么发"。限流器、配置解析、键拼接、用途策略全部是真代码在跑。打真供应商的那份
 * 验证在 {@code ElongCheckPriceE2ETest}（需 {@code ELONG_E2E=1} 与白名单出口）。
 */
class PurposeBucketChannelTest {

    private static final String IFACE = "GLOBAL_LIMIT:ELONG:SPA_SUPPLIER_API_PRODUCT_PRICES";

    /** 每次调用实际打到限流器上的键，按顺序 */
    private final List<String> seen = new ArrayList<>();

    @BeforeEach
    void wireRealLimiter() {
        seen.clear();
    }

    @Test
    @DisplayName("刷价这一路：扣「:REFRESH + 接口桶」各一格，且键完全按用途拼")
    void refreshTakesPurposeThenInterface() {
        install("{\"" + IFACE + "\":6,\"" + IFACE + ":REFRESH\":5}", 5000);

        new FakeAccess(seen).access("x", CallPurpose.REFRESH);

        assertEquals(List.of(IFACE + ":REFRESH", IFACE), seen,
                "顺序也有意义：先扣较紧的用途桶，再扣接口桶");
    }

    @Test
    @DisplayName("未登记的用途桶整格跳过，只扣接口桶——代码可先于配置发布")
    void unregisteredPurposeBucketIsSkipped() {
        // 生产此刻的真实取值：只登记了 :REFRESH，CHECK_PRICE 还没有
        install("{\"" + IFACE + "\":6,\"" + IFACE + ":REFRESH\":5}", 5000);

        new FakeAccess(seen).access("x", CallPurpose.CHECK_PRICE);

        assertEquals(List.of(IFACE), seen,
                "未登记就不该扣——若按 qpsOf 回落 default-qps，等于给忘配的子桶发一个很大的额度");
    }

    @Test
    @DisplayName("客流用途：额度耗尽后如实失败，不把客人挂在限流上")
    void frontOfHouseFailsFast() {
        install("{\"" + IFACE + "\":1,\"" + IFACE + ":CHECK_PRICE\":1}", 50);
        FakeAccess access = new FakeAccess(seen);

        access.access("x", CallPurpose.CHECK_PRICE);

        long start = System.currentTimeMillis();
        assertThrows(RedisLimitException.class, () -> access.access("x", CallPurpose.CHECK_PRICE));
        long waited = System.currentTimeMillis() - start;
        assertTrue(waited < 1000,
                "客流路必须快速失败，实测等了 " + waited + "ms —— 把客人挂住比告诉他稍后重试更糟");
    }

    @Test
    @DisplayName("后台用途：额度耗尽后排队等，不制造假失败")
    void backOfHouseQueues() {
        install("{\"" + IFACE + "\":4,\"" + IFACE + ":REFRESH\":4}", 50);
        FakeAccess access = new FakeAccess(seen);

        access.access("x", CallPurpose.REFRESH);

        long start = System.currentTimeMillis();
        // 不抛异常：刷价被限流挡掉会计入失败态而不动缓存（F-5.1），等于凭空造一次假失败
        access.access("x", CallPurpose.REFRESH);
        long waited = System.currentTimeMillis() - start;

        assertTrue(waited >= 150,
                "4 QPS 下第二次应当等约 250ms 才拿到许可，实测 " + waited + "ms —— "
                        + "没等说明退回了非阻塞语义");
    }

    /** 装一套真限流器：真 RateLimitProperties 解析 + 真 Guava 桶 */
    private void install(String qpsJson, int acquireTimeoutMs) {
        RateLimitProperties props = new RateLimitProperties();
        ReflectionTestUtils.setField(props, "qpsJson", qpsJson);
        ReflectionTestUtils.setField(props, "defaultQps", 20d);
        ReflectionTestUtils.setField(props, "acquireTimeoutMs", acquireTimeoutMs);
        ReflectionTestUtils.setField(props, "mode", "local");
        props.init();

        RateLimitManagerImpl impl = new RateLimitManagerImpl();
        ReflectionTestUtils.setField(impl, "properties", props);

        RateLimitManager recording = new RateLimitManager() {
            @Override
            public void acquire(String key) {
                seen.add(key);
                impl.acquire(key);
            }

            @Override
            public boolean tryAcquire(String key) {
                seen.add(key);
                return impl.tryAcquire(key);
            }

            @Override
            public boolean isRegistered(String key) {
                return impl.isRegistered(key);
            }
        };
        ApplicationContext ctx = mock(ApplicationContext.class);
        when(ctx.getBean(RateLimitManager.class)).thenReturn(recording);
        new RateLimitHolder().setApplicationContext(ctx);
    }

    /** 只替网络那一段的通道实现；许可怎么取全走 BaseHttpAccess 真代码 */
    private static final class FakeAccess extends BaseHttpAccess<String, FakeResponse> {

        private final List<String> seen;

        private FakeAccess(List<String> seen) {
            super(SupplierSourceEnum.ELONG, SupplierDataTypeEnum.PRODUCT_PRICE,
                    MonitorNameEnum.SPA_SUPPLIER_API_PRODUCT_PRICES);
            this.seen = seen;
        }

        @Override
        protected ResponseResult<FakeResponse> request(String url, String request, IParser<FakeResponse> parser) {
            return new ResponseResult<>(HttpStatus.SC_OK, "{}", new FakeResponse());
        }

        @Override
        protected void beforeAccess(String request) {
        }

        @Override
        protected String buildRequestUrl() {
            return "http://localhost/never-dialed";
        }

        @Override
        protected FakeResponse parseResponse(String data) {
            return new FakeResponse();
        }
    }

    private static final class FakeResponse implements BaseResponse {
        @Override
        public boolean isSucc() {
            return true;
        }

        @Override
        public boolean isEmptyResult() {
            return false;
        }
    }
}
