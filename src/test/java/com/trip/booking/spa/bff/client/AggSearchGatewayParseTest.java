package com.trip.booking.spa.bff.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 钉住 spa ↔ agg 的检索契约。
 *
 * <p>固件是 <b>agg 生产实例的真实响应原文</b>（2026-09-05 抓取），不是手写的对象——
 * 这条纪律是踩出来的：spa 与 cursor 之间的 DTO 带 {@code ignoreUnknown}，
 * 字段名漂了不会报错，只是值恒为空。这里同样：agg 哪天把 {@code supplierRefs}
 * 改个名，{@link AggSearchGateway} 不会抛异常，只会永远返回空列表，
 * 而搜索框会安静地退化成「什么都搜不到」。只有读原始 JSON 的测试能拦住它。
 */
class AggSearchGatewayParseTest {

    private static String realResponse() throws Exception {
        try (var in = AggSearchGatewayParseTest.class.getResourceAsStream("/bff/agg-suggest-response.json")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    @DisplayName("从真实响应里取出 Expedia property_id 与展示字段")
    void extractsExpediaPropertyIdFromRealResponse() throws Exception {
        List<AggSearchGateway.Hotel> hotels = AggSearchGateway.parse(realResponse(), "expedia");

        assertThat(hotels).hasSize(2);
        AggSearchGateway.Hotel first = hotels.get(0);
        assertThat(first.propertyId())
                .as("下游定价链认的是 Expedia property_id，不是 canonical hotelId")
                .isEqualTo("18699824");
        assertThat(first.name()).isEqualTo("素坤逸坤酒店");
        assertThat(first.city()).isEqualTo("Samut Prakan");
        assertThat(first.countryCode()).isEqualTo("TH");
        assertThat(first.starRating()).isEqualTo(3.5);
    }

    @Test
    @DisplayName("同一家店挂着多个别家 id 时不会挑错")
    void picksTheRequestedSupplierAmongSeveral() throws Exception {
        // 固件第一条同时挂着 expedia 1 个、elong 2 个、huizhi 1 个。
        // elong 有两个 id 正是「不能把 refs 压成 Map」的现场证据。
        List<AggSearchGateway.Hotel> elong = AggSearchGateway.parse(realResponse(), "elong");

        assertThat(elong.get(0).propertyId()).isEqualTo("32915286");
    }

    @Test
    @DisplayName("拿不到目标供应商 id 的命中必须丢掉")
    void dropsHitsWithoutTheRequestedSupplier() throws Exception {
        List<AggSearchGateway.Hotel> none = AggSearchGateway.parse(realResponse(), "dida");

        assertThat(none)
                .as("报不出价的店不该出现在搜索框里——点得进去却报不出价，比搜不到更糟")
                .isEmpty();
    }
}
