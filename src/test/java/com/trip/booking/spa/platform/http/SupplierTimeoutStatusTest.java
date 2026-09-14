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
import org.apache.http.conn.ConnectTimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.SocketTimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 超时必须与其余异常分成两个终态。
 *
 * <p>为什么要分：超时说明对家慢、或我方超时设得紧，处置是调超时与并发；连接失败与解析失败说明
 * 报文或链路本身有问题，处置是查报文。混成一个 {@code error}，「刷不出价是对家慢还是我们读错了」
 * 就答不出来。{@code CallStatus.TIMEOUT} 此前声明了却从未被使用，O-3.1 的六个终态因此并不穷尽。
 */
class SupplierTimeoutStatusTest {

    private SimpleMeterRegistry registry;

    private record Unused() implements BaseResponse {
        @Override
        public boolean isSucc() {
            return true;
        }

        @Override
        public boolean isEmptyResult() {
            return false;
        }
    }

    /** 最小 Access：request 按预置异常抛，不出网 */
    private static class ThrowingAccess extends BaseHttpAccess<String, Unused> {
        private final Exception toThrow;

        ThrowingAccess(Exception toThrow) {
            super(SupplierSourceEnum.ELONG, SupplierDataTypeEnum.CHECK_PRICE,
                    MonitorNameEnum.SPA_SUPPLIER_API_ORDER_PRICE, 0);
            this.toThrow = toThrow;
        }

        @Override
        protected ResponseResult<Unused> request(String url, String req, IParser<Unused> parser) throws Exception {
            throw toThrow;
        }

        @Override
        protected String errorCode(Unused response) {
            return null;
        }

        @Override
        protected void beforeAccess(String req) {
        }

        @Override
        protected String buildRequestUrl() {
            return "http://fake";
        }

        @Override
        protected Unused parseResponse(String data) {
            return new Unused();
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

    private double count(String status) {
        return registry.counter("supplier_io_access_count", "supplier", "ELONG",
                "interface", "SPA_SUPPLIER_API_ORDER_PRICE", "status", status).count();
    }

    @Test
    @DisplayName("读超时与连接超时都记 timeout")
    void readAndConnectTimeoutsAreCountedAsTimeout() {
        new ThrowingAccess(new SocketTimeoutException("read timed out")).access("req", CallPurpose.CHECK_PRICE);
        new ThrowingAccess(new ConnectTimeoutException("connect timed out")).access("req", CallPurpose.CHECK_PRICE);

        assertEquals(2.0, count("timeout"));
        assertEquals(0.0, count("error"), "超时不该再落进 error");
    }

    @Test
    @DisplayName("包了一层的超时也认得出来——底层常把原始异常包起来再抛")
    void wrappedTimeoutIsStillTimeout() {
        new ThrowingAccess(new UncheckedIOException(new SocketTimeoutException("read timed out")))
                .access("req", CallPurpose.CHECK_PRICE);

        assertEquals(1.0, count("timeout"));
    }

    @Test
    @DisplayName("非超时异常仍记 error，两者互斥")
    void otherFailuresStayError() {
        new ThrowingAccess(new IOException("connection reset")).access("req", CallPurpose.CHECK_PRICE);

        assertEquals(1.0, count("error"));
        assertEquals(0.0, count("timeout"));
    }
}
