package com.trip.booking.spa.core.api.common.asynchttp;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 钉住 HTTP 成功判据为 <b>2xx</b>，而非仅 200。
 *
 * <p>此前写死 {@code == 200}，凡返回 201/202/204 的接口一律被判失败。该缺陷已实际发生：
 * Expedia 取消房间成功返回 204，被判失败后触发重试，第二次 DELETE 得到
 * 400「Room is already cancelled」，最终把一次成功的取消上报成 UNKNOWN。
 *
 * <p>本类同时钉住另一侧：放宽 HTTP 判据<b>不得</b>放过业务错误——供应商在 2xx 响应里
 * 塞业务失败时，仍须判失败。
 */
class ResponseResultTest {

    /** 204 No Content 是取消成功的常态，必须判成功 */
    @Test
    void acceptsNoContent() {
        assertTrue(result(204, new StubResponse(true)).isSucc());
    }

    /** 201 Created 同样是成功 */
    @Test
    void acceptsCreated() {
        assertTrue(result(201, new StubResponse(true)).isSucc());
    }

    /** 202 Accepted 同样是成功 */
    @Test
    void acceptsAccepted() {
        assertTrue(result(202, new StubResponse(true)).isSucc());
    }

    /** 200 仍然成功——放宽不得改变原有行为 */
    @Test
    void stillAcceptsOk() {
        assertTrue(result(200, new StubResponse(true)).isSucc());
    }

    /** 299 是 2xx 上界内，仍算成功 */
    @Test
    void acceptsUpperBoundOfSuccessRange() {
        assertTrue(result(299, new StubResponse(true)).isSucc());
    }

    /** 300 已越出 2xx，不算成功——重定向不是成功 */
    @Test
    void rejectsRedirect() {
        assertFalse(result(300, new StubResponse(true)).isSucc());
    }

    @Test
    void rejectsClientError() {
        assertFalse(result(400, new StubResponse(true)).isSucc());
    }

    @Test
    void rejectsServerError() {
        assertFalse(result(500, new StubResponse(true)).isSucc());
    }

    /**
     * 放宽 HTTP 判据不得放过业务错误：状态码 2xx 但业务层判失败时，整体仍是失败。
     * 这是本次改动最需要守住的一条。
     */
    @Test
    void stillRejectsBusinessFailureInsideSuccessfulHttp() {
        assertFalse(result(200, new StubResponse(false)).isSucc());
        assertFalse(result(204, new StubResponse(false)).isSucc());
    }

    /** 无解析结果时不得判成功 */
    @Test
    void rejectsMissingData() {
        assertFalse(new ResponseResult<StubResponse>(200, "", null).isSucc());
    }

    private ResponseResult<StubResponse> result(int status, StubResponse data) {
        return new ResponseResult<>(status, "", data);
    }

    private static class StubResponse implements BaseResponse {
        private final boolean succ;

        StubResponse(boolean succ) {
            this.succ = succ;
        }

        @Override
        public boolean isSucc() {
            return succ;
        }

        @Override
        public boolean isEmptyResult() {
            return false;
        }
    }
}
