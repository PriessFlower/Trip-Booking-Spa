package com.trip.booking.spa.gateway.adapter.outbound.supplier.clwy.shared;

import com.trip.booking.spa.gateway.adapter.outbound.supplier.clwy.shared.model.ClwyCancellationPenalty;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.clwy.shared.model.ClwyRatePlan;
import com.trip.booking.spa.gateway.domain.product.CancelPolicy;
import com.trip.booking.spa.gateway.domain.product.RefundType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 退改规范化：起始点列表 → 分段。<b>时钟是入参</b>（守护 R63：夹具会随真实时间腐烂）——
 * "免费窗过期后不再对外承诺免费"这条判据，靠 {@code Instant.now()} 就钉不住。
 *
 * <p>真实报文（2026-09-14 生产，酒店 218951）：住期 2026-09-21 一晚，房费 2682.64，
 * 09-13 23:00(+1) 起罚 536.51，09-18 21:59(+1) 起罚 2682.64（全额）。
 * 即三段：此前免费 → 部分罚 → 全额罚。
 */
class ClwyCancelPolicyTest {

    /** 早于第一个起始点，三段都还在 */
    private static final Instant BEFORE_ALL = Instant.parse("2026-09-10T00:00:00Z");

    private final ClwyProductKeyDeriver deriver = deriver();

    private static ClwyProductKeyDeriver deriver() {
        ClwyProperties props = new ClwyProperties();
        props.setWid("W-TEST");
        ClwyProductKeyDeriver d = new ClwyProductKeyDeriver();
        d.setProperties(props);
        return d;
    }

    @Test
    @DisplayName("真实报文：三段——免费窗 + 部分罚 + 全额罚，且末段 before 落到下限 25")
    void realPayloadBecomesThreeSegments() throws IOException {
        ClwyRatePlan plan = ClwyWireNameTest.fixture().firstHotel()
                .getRoomTypeList().get(0).getRatePlanList().get(0);

        List<CancelPolicy> policies = deriver.convertCancelPolicy(
                "2026-09-21", plan.getCancellationPenalties(), plan.getCityTimeZone(), BEFORE_ALL);

        assertEquals(3, policies.size(), "首条 amount>0 要补一段免费窗");
        assertEquals(RefundType.NO_DEDUCTION, policies.get(0).getType(), "09-13 23:00 之前免费");
        assertEquals(RefundType.DEDUCT_BY_AMOUNT, policies.get(1).getType());
        assertEquals(536.51, policies.get(1).getValue(), 0.001);
        assertEquals(RefundType.DEDUCT_BY_AMOUNT, policies.get(2).getType());
        assertEquals(2682.64, policies.get(2).getValue(), 0.001);
        assertEquals(25, policies.get(2).getBefore(), "末段无截止，落 before 下限=此后一直");
        assertEquals("+01:00", policies.get(0).getTimeZone(), "时区标签取解析出的小时偏移");
    }

    @Test
    @DisplayName("时区是小时偏移不是时区名：'1'/'-8'/'12' 都要能解，'Europe/Rome' 反而不该被当偏移")
    void cityTimeZoneIsAnHourOffset() {
        assertEquals(ZoneOffset.ofHours(1), ClwyProductKeyDeriver.parseHourOffset("1"));
        assertEquals(ZoneOffset.ofHours(-8), ClwyProductKeyDeriver.parseHourOffset("-8"));
        assertEquals(ZoneOffset.ofHours(12), ClwyProductKeyDeriver.parseHourOffset("12"));
        assertEquals(ZoneOffset.ofHoursMinutes(5, 30), ClwyProductKeyDeriver.parseHourOffset("5.5"));
        assertNull(ClwyProductKeyDeriver.parseHourOffset("Europe/Rome"));
        assertNull(ClwyProductKeyDeriver.parseHourOffset(""));
        assertNull(ClwyProductKeyDeriver.parseHourOffset(null));
        assertNull(ClwyProductKeyDeriver.parseHourOffset("99"), "超出 ±18 小时的不是合法偏移");
    }

    @Test
    @DisplayName("from 无偏移，必须配 cityTimeZone 才是确定时刻；newFrom 的单位数偏移作兜底")
    void instantComesFromLocalPlusOffsetOrNewFrom() {
        ClwyCancellationPenalty p = new ClwyCancellationPenalty();
        p.setFrom("2026-09-13 23:00:00");
        p.setNewFrom("2026-09-13T23:00:00+1:00");
        p.setAmount(BigDecimal.ONE);

        assertEquals(Instant.parse("2026-09-13T22:00:00Z"),
                ClwyProductKeyDeriver.instantOf(p, ZoneOffset.ofHours(1)));
        // cityTimeZone 不可用时回落 newFrom，结果应当一致
        assertEquals(Instant.parse("2026-09-13T22:00:00Z"),
                ClwyProductKeyDeriver.instantOf(p, null));
        assertEquals(Instant.parse("2026-09-13T22:00:00Z"),
                ClwyProductKeyDeriver.parseNewFrom("2026-09-13T23:00:00+1:00"), "单位数偏移要补零");
        assertNull(ClwyProductKeyDeriver.parseNewFrom("2026-09-13 23:00:00"), "没偏移就解不出确定时刻");
    }

    @Test
    @DisplayName("取不到时刻一律 UNKNOWN：不套默认时区，也不兜成不可退（cursor 正是兜成不可退的）")
    void unresolvableTimeIsUnknownNotNonRefundable() {
        ClwyCancellationPenalty p = new ClwyCancellationPenalty();
        p.setFrom("2026-09-13 23:00:00");
        p.setAmount(BigDecimal.TEN);

        assertTrue(deriver.convertCancelPolicy("2026-09-21", List.of(p), null, BEFORE_ALL).isEmpty(),
                "既无 cityTimeZone 又无 newFrom → 空列表(UNKNOWN)");
        assertTrue(deriver.convertCancelPolicy("2026-09-21", List.of(p), "Europe/Rome", BEFORE_ALL).isEmpty(),
                "时区名不是小时偏移，解不出即 UNKNOWN");
    }

    @Test
    @DisplayName("政策列表缺席 → UNKNOWN（空列表），不是「不可退」")
    void missingPolicyIsUnknown() {
        assertTrue(deriver.convertCancelPolicy("2026-09-21", null, "1", BEFORE_ALL).isEmpty());
        assertTrue(deriver.convertCancelPolicy("2026-09-21", List.of(), "1", BEFORE_ALL).isEmpty());
    }

    @Test
    @DisplayName("首条即免费（amount=0）时不补免费窗，段数等于原始条数")
    void freeFirstSegmentNeedsNoExtraWindow() {
        ClwyCancellationPenalty free = new ClwyCancellationPenalty();
        free.setFrom("2026-09-13 23:00:00");
        free.setNewFrom("2026-09-13T23:00:00+1:00");
        free.setAmount(BigDecimal.ZERO);
        ClwyCancellationPenalty paid = new ClwyCancellationPenalty();
        paid.setFrom("2026-09-18 21:59:00");
        paid.setNewFrom("2026-09-18T21:59:00+1:00");
        paid.setAmount(new BigDecimal("100"));

        List<CancelPolicy> policies = deriver.convertCancelPolicy(
                "2026-09-21", List.of(free, paid), "1", BEFORE_ALL);

        assertEquals(2, policies.size());
        assertEquals(RefundType.NO_DEDUCTION, policies.get(0).getType());
        assertEquals(RefundType.DEDUCT_BY_AMOUNT, policies.get(1).getType());
        assertNotNull(policies.get(1).getTimeZone());
    }
}
