package com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.shared;

import com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.shared.model.FliggyAriResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 用<b>真实报文</b>钉住 ari 解析（2026-08-27 首笔成功响应，新宿华盛顿酒店，样本截取
 * 2 条 rate）。手写样本钉的是"我们以为的形状"，这里钉的是"飞猪实际的形状"——
 * 两者曾经不同：simplify=true 连 <method>_response 包裹层一起去掉，顶层直接是业务体，
 * 22 条真实报价因此被误判为空（NO_INVENTORY）。此测试红了=飞猪改了报文形状或
 * 我们改坏了解析，两者都必须停下来看。
 */
class FliggyRealPayloadTest {

    @Test
    @DisplayName("真实 ari 报文（simplify 无包裹层）：rates 可取、trace 可取、分价与币种原样")
    void realAriPayloadParses() throws Exception {
        String raw = Files.readString(Path.of("src/test/resources/fliggy/ari-availability-real-20260827.json"));
        FliggyAriResponse r = FliggyAriResponse.parse(raw);

        assertFalse(r.isPlatformError());
        assertTrue(r.isSucc());
        assertFalse(r.isEmptyResult());
        assertEquals(2, r.rates().size());
        assertEquals("213e04ee17878097227985526e0c0a", r.requestTraceId());
        var first = r.rates().get(0);
        assertTrue(first.get("rate_key").asText().length() > 10);
        assertEquals("USD", first.get("total_rate").get("currency").asText());
        assertEquals("10524", first.get("total_rate").get("inclusive").asText());
    }
}
