package com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.shared;

import com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.shared.model.FliggyAriResponse;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.shared.model.FliggyValidateResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 双层信封判读——读原始 JSON 而非 new 对象（Spa* DTO 契约漂移的教训）。
 * JSON 形态按 docs/fliggy/distribution-api.md 与 TOP 公共错误约定构造；
 * 首次沙箱实测拿到真实报文后，替换这里的手写样本并补齐逐字段守护。
 */
class FliggyTopEnvelopeTest {

    @Test
    @DisplayName("error_response code=27 → 平台错 + 凭据病(AUTH_CONFIG 判据)")
    void invalidSessionIsCredentialFailure() {
        String raw = "{\"error_response\":{\"code\":27,\"msg\":\"Invalid session\","
                + "\"sub_code\":\"invalid-sessionkey\",\"sub_msg\":\"非法或过期的SessionKey\"}}";
        FliggyAriResponse r = FliggyAriResponse.parse(raw);

        assertTrue(r.isPlatformError());
        assertTrue(r.isCredentialFailure());
        assertFalse(r.isSucc());
        assertEquals("27:invalid-sessionkey", r.metricErrorCode());
    }

    @Test
    @DisplayName("error_response code=7 → 平台频控,不是凭据病")
    void callLimitedIsThrottledNotCredential() {
        String raw = "{\"error_response\":{\"code\":7,\"msg\":\"App Call Limited\"}}";
        FliggyAriResponse r = FliggyAriResponse.parse(raw);

        assertTrue(r.isPlatformThrottled());
        assertFalse(r.isCredentialFailure());
    }

    @Test
    @DisplayName("ari 成功响应:rates 摊平、trace 可取、空 rates=明确无货")
    void ariParsesRatesAndTrace() {
        String raw = "{\"xhotel_distribution_ari_availability_response\":{\"data\":{"
                + "\"request_trace_id\":\"trace-1\",\"properties\":[{\"hotel_id\":\"H1\","
                + "\"rates\":[{\"rate_key\":\"rk-1\",\"room_id\":\"R1\"}]}]}}}";
        FliggyAriResponse r = FliggyAriResponse.parse(raw);

        assertTrue(r.isSucc());
        assertFalse(r.isEmptyResult());
        assertEquals("trace-1", r.requestTraceId());
        assertEquals(1, r.rates().size());
        assertEquals("rk-1", r.rates().get(0).get("rate_key").asText());

        FliggyAriResponse empty = FliggyAriResponse.parse(
                "{\"xhotel_distribution_ari_availability_response\":{\"data\":{\"properties\":[]}}}");
        assertTrue(empty.isSucc());
        assertTrue(empty.isEmptyResult());
    }

    @Test
    @DisplayName("validate 成功:create_key/总价(分)/币种可取;is_success=false 即业务未通过")
    void validateParsesKeysAndPrice() {
        String ok = "{\"xhotel_order_international_distribution_validate_response\":{"
                + "\"is_success\":true,\"result\":{\"create_key\":\"ck-1\","
                + "\"rate_plan_info\":{\"total_room_price\":12345,\"currency_code\":\"USD\"}}}}";
        FliggyValidateResponse r = FliggyValidateResponse.parse(ok);

        assertTrue(r.isSucc());
        assertEquals("ck-1", r.createKey());
        assertEquals(12345, r.totalRoomPriceCents());
        assertEquals("USD", r.currencyCode());

        FliggyValidateResponse fail = FliggyValidateResponse.parse(
                "{\"xhotel_order_international_distribution_validate_response\":{"
                        + "\"is_success\":false,\"error_resp_code\":101}}");
        assertFalse(fail.isSucc());
        assertEquals("101", fail.bizErrorCode());
        assertEquals("biz:101", fail.metricErrorCode());
        assertNull(fail.createKey());
    }
}
