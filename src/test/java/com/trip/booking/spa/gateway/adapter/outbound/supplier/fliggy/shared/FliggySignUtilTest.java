package com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.shared;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * TOP MD5 签名：{@code md5(secret + 字典序k1v1k2v2 + secret)} 大写。
 * 期望值用另一套实现（spring 的 DigestUtils）独立计算——双实现对拍，
 * 不是被测代码自证。TOP 平台只回「签名错误」不回差异，算法漂移只有这里拦得住。
 */
class FliggySignUtilTest {

    @Test
    @DisplayName("字典序拼接+首尾夹 secret+大写，与独立实现对拍")
    void matchesIndependentImplementation() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("method", "taobao.xhotel.distribution.ari.availability");
        params.put("app_key", "12345678");
        params.put("timestamp", "2026-08-26 12:00:00");

        String expected = DigestUtils.md5DigestAsHex(
                ("secret" + "app_key" + "12345678"
                        + "method" + "taobao.xhotel.distribution.ari.availability"
                        + "timestamp" + "2026-08-26 12:00:00" + "secret")
                        .getBytes(StandardCharsets.UTF_8)).toUpperCase(Locale.ROOT);

        assertEquals(expected, FliggySignUtil.sign(params, "secret"));
    }

    @Test
    @DisplayName("入参插入序不影响签名（按键字典序），空值跳过")
    void insertionOrderIrrelevantAndBlankSkipped() {
        Map<String, String> a = new LinkedHashMap<>();
        a.put("b", "2");
        a.put("a", "1");
        a.put("empty", "");
        Map<String, String> b = new LinkedHashMap<>();
        b.put("a", "1");
        b.put("b", "2");

        assertEquals(FliggySignUtil.sign(b, "s"), FliggySignUtil.sign(a, "s"));
        assertEquals(32, FliggySignUtil.sign(a, "s").length());
    }
}
