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

    /**
     * 退改规则时刻=<b>北京时间</b>（快照 §9 必测第 5 项，2026-08-28 实证销项）：
     * 响应自带查询时刻毫秒戳（data.time），最早一段 onward=「罚金自当下起算」——
     * 二者相等于 GMT+8（东京酒店，东京时间差 1 小时对不上）。时区错 1 小时,
     * "还能免费取消到几点"就错 1 小时,客人在窗口外取消要挨罚金。
     */
    @Test
    @DisplayName("规则时刻是北京时间：响应 time 戳 == 最早段 onward @ GMT+8")
    void cancelRuleTimesAreBeijing() throws Exception {
        String raw = Files.readString(Path.of("src/test/resources/fliggy/ari-availability-real-20260827.json"));
        com.fasterxml.jackson.databind.JsonNode root =
                new com.fasterxml.jackson.databind.ObjectMapper().readTree(raw);
        long epochMs = root.get("data").get("time").asLong();

        String earliestOnward = "2026-08-27 13:48:44"; // 首条 rate 最早段的 onward（原文）
        java.time.Instant onwardBeijing = java.time.LocalDateTime
                .parse(earliestOnward, java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                .atZone(java.time.ZoneId.of("Asia/Shanghai")).toInstant();

        long diffSeconds = Math.abs(java.time.Duration
                .between(java.time.Instant.ofEpochMilli(epochMs), onwardBeijing).getSeconds());
        assertTrue(diffSeconds < 300, "onward 按北京时间解释应≈查询时刻,实际差 " + diffSeconds + " 秒"
                + "——若本断言红了,说明飞猪改了时区表达,FliggyProductKeyDeriver.RULE_ZONE 必须重证");
    }
}
