package com.trip.booking.spa.platform.http;

import org.apache.http.impl.client.CloseableHttpClient;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * 钉住连接池按 host 分池：不同供应商的 host 各占一池，物理隔离。
 *
 * <p>此前是全局单例——按第一个到达的 url 的 host 建池，此后所有供应商共用
 * maxTotal=250：任何一家慢下来（socket 挂满超时）就吃光全局连接，其余家一起被拖死。
 * HttpUtils 类注释自己写着"八家供应商全部对外请求的底层实现，改错即八家同挂"，
 * 而单例恰恰把"同挂"做成了结构性事实。
 */
class HttpClientPerHostTest {

    /** 不同 host 必须拿到不同的池 */
    @Test
    void differentHostsGetDifferentPools() {
        CloseableHttpClient a = HttpUtils.getHttpClient("https://api.ean.com/v3/properties");
        CloseableHttpClient b = HttpUtils.getHttpClient("https://api.elong.com/rest");

        assertNotSame(a, b, "两家供应商共用一个连接池 = 一家慢全家挂");
    }

    /** 同 host 复用同一个池——不能退化成每次请求建一个 client */
    @Test
    void sameHostReusesThePool() {
        CloseableHttpClient a = HttpUtils.getHttpClient("https://api.ean.com/v3/properties");
        CloseableHttpClient b = HttpUtils.getHttpClient("https://api.ean.com/v3/chains");

        assertSame(a, b);
    }

    /** 同 host 异端口是两个服务，各占一池 */
    @Test
    void sameHostDifferentPortIsADifferentPool() {
        CloseableHttpClient a = HttpUtils.getHttpClient("http://mock.local:8081/x");
        CloseableHttpClient b = HttpUtils.getHttpClient("http://mock.local:8082/x");

        assertNotSame(a, b);
    }
}
