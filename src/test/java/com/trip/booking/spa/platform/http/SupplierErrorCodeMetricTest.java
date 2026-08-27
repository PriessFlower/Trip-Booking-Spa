package com.trip.booking.spa.platform.http;

import com.trip.booking.spa.gateway.domain.supplier.SupplierDataTypeEnum;
import com.trip.booking.spa.gateway.domain.supplier.SupplierSourceEnum;
import com.trip.booking.spa.platform.http.asynchttp.BaseResponse;
import com.trip.booking.spa.platform.http.asynchttp.IParser;
import com.trip.booking.spa.platform.http.asynchttp.ResponseResult;
import com.trip.booking.spa.platform.observability.Monitor;
import com.trip.booking.spa.platform.observability.MonitorNameEnum;
import com.trip.booking.spa.platform.observability.MonitorService;
import com.trip.booking.spa.platform.ratelimit.CallPurpose;
import com.trip.booking.spa.platform.ratelimit.RateLimitHolder;
import com.trip.booking.spa.platform.ratelimit.RateLimitManager;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 业务失败必须留下原生错误码分布（supplier_io_error_code{supplier,interface,code}）。
 * status 只答「败了」，code 答「败成什么样」——具体码此前只在日志里，40 分钟即冲掉，
 * 「上周 rejected 涨的那坨是什么码」在指标通道上无解。响应类给不出码时归 http_<status>。
 */
class SupplierErrorCodeMetricTest {

    private SimpleMeterRegistry registry;

    /** 最小失败响应：isSucc=false，码由构造给定 */
    private record FailedResponse(String code) implements BaseResponse {
        @Override
        public boolean isSucc() {
            return false;
        }

        @Override
        public boolean isEmptyResult() {
            return false;
        }
    }

    /** 最小 Access：request 直接返回预置响应，不出网 */
    private static class FakeAccess extends BaseHttpAccess<String, FailedResponse> {
        private final FailedResponse canned;

        FakeAccess(FailedResponse canned) {
            super(SupplierSourceEnum.ELONG, SupplierDataTypeEnum.CHECK_PRICE,
                    MonitorNameEnum.SPA_SUPPLIER_API_ORDER_PRICE, 0);
            this.canned = canned;
        }

        @Override
        protected ResponseResult<FailedResponse> request(String url, String req, IParser<FailedResponse> parser) {
            return new ResponseResult<>(200, "{}", canned);
        }

        @Override
        protected String errorCode(FailedResponse response) {
            return response.code();
        }

        @Override
        protected void beforeAccess(String req) {
        }

        @Override
        protected String buildRequestUrl() {
            return "http://fake";
        }

        @Override
        protected FailedResponse parseResponse(String data) {
            return canned;
        }
    }

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        MonitorService monitorService = new MonitorService();
        monitorService.bindTo(registry);
        ReflectionTestUtils.setField(Monitor.class, "monitorService", monitorService);
        RateLimitManager manager = Mockito.mock(RateLimitManager.class);
        Mockito.when(manager.isRegistered(Mockito.anyString())).thenReturn(false);
        Mockito.when(manager.tryAcquire(Mockito.anyString())).thenReturn(true);
        ReflectionTestUtils.setField(RateLimitHolder.class, "manager", manager);
    }

    @AfterEach
    void tearDown() {
        ReflectionTestUtils.setField(Monitor.class, "monitorService", null);
        ReflectionTestUtils.setField(RateLimitHolder.class, "manager", null);
    }

    @Test
    @DisplayName("失败响应带码 → 原生码进分布;不带码 → 归 http_<status>")
    void errorCodeIsRecordedWithNativeCodeOrHttpFallback() {
        new FakeAccess(new FailedResponse("H001083")).access("req", CallPurpose.CHECK_PRICE);
        assertEquals(1.0, registry.counter("supplier_io_error_code_count",
                "supplier", "ELONG", "interface", "SPA_SUPPLIER_API_ORDER_PRICE",
                "code", "H001083").count());

        new FakeAccess(new FailedResponse(null)).access("req", CallPurpose.CHECK_PRICE);
        assertEquals(1.0, registry.counter("supplier_io_error_code_count",
                "supplier", "ELONG", "interface", "SPA_SUPPLIER_API_ORDER_PRICE",
                "code", "http_200").count());
    }
}
