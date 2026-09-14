package com.trip.booking.spa.gateway.adapter.outbound.supplier.meituan.shared;

import com.trip.booking.spa.gateway.adapter.outbound.supplier.meituan.shared.model.MeituanCpApply;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.meituan.shared.model.MeituanGoods;
import com.trip.booking.spa.gateway.domain.product.CancelPolicy;
import com.trip.booking.spa.gateway.domain.product.RefundType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 退改规范化。本家的段语义与另几家<b>相反</b>：{@code endDate} 是段的<b>右边界</b>
 * （"在它之前取消收 penalty"），不是"从它起收"。读反了会把免费窗和收费窗整体错位一段。
 *
 * <p>时钟一律由测试注入——判过期是本方法输出的一部分，交给 {@code Instant.now()} 就等于
 * 让断言随真实时间腐烂（守护 R63）。
 */
class MeituanCancelPolicyTest {

    private final MeituanProductKeyDeriver deriver = deriver();

    private static MeituanProductKeyDeriver deriver() {
        MeituanProperties props = new MeituanProperties();
        props.setPartnerId("39155");
        MeituanProductKeyDeriver d = new MeituanProductKeyDeriver();
        d.setProperties(props);
        return d;
    }

    /** 住期与夹具一致；取一个两段退改都还没到期的时刻 */
    private static final String CHECK_IN = "2026-09-21";
    private static final Instant BEFORE_ALL = Instant.parse("2026-09-14T00:00:00Z");

    private static MeituanGoods goods(int index) throws IOException {
        return MeituanWireNameTest.fixture().hotelOf("967183").getGoodsList().get(index);
    }

    @Test
    @DisplayName("限时取消：免费段在前、收费段在后，末尾补一段不可取消")
    void refundableLadderKeepsOrder() throws IOException {
        MeituanGoods g = goods(0);

        List<CancelPolicy> policies = deriver.convertCancelPolicy(
                CHECK_IN, g.getRefundable(), g.getCpApply(), 1, true, BEFORE_ALL);

        assertEquals(3, policies.size(), "两段原文 + 末尾的不可取消收尾");
        assertEquals(RefundType.NO_DEDUCTION, policies.get(0).getType(), "09/20 00:00 之前免费");
        assertEquals(48, policies.get(0).getBefore(), "距入住日 24:00 还有 48 小时");
        assertEquals(RefundType.DEDUCT_BY_AMOUNT, policies.get(1).getType());
        assertEquals(28020, policies.get(1).getAmount(), "定额，单位分，与报文逐字相同");
        assertEquals(0, policies.get(2).getCancelType(), "末段截止后不可取消——不写出来就等于说此后仍按末段罚");
    }

    /**
     * B4 的同款陷阱换了个地方：报价档的罚金是<b>单间</b>口径，不乘间数就会拿一间的罚金
     * 去承诺三间的单子。实测依据见 {@code MeituanProductKeyDeriver#convertCancelPolicy}。
     */
    @Test
    @DisplayName("报价档三间：罚金必须乘间数")
    void quoteLanePenaltyScalesWithRooms() throws IOException {
        MeituanGoods g = goods(0);

        List<CancelPolicy> one = deriver.convertCancelPolicy(
                CHECK_IN, g.getRefundable(), g.getCpApply(), 1, true, BEFORE_ALL);
        List<CancelPolicy> three = deriver.convertCancelPolicy(
                CHECK_IN, g.getRefundable(), g.getCpApply(), 3, true, BEFORE_ALL);

        assertEquals(28020, one.get(1).getAmount());
        assertEquals(28020 * 3, three.get(1).getAmount(), "单间口径 × 间数");
    }

    /** 反过来的错同样要防：验价档给的已是全间数口径，再乘一次就把罚金说成了两倍 */
    @Test
    @DisplayName("验价档三间：罚金已是全间数口径，不许再乘")
    void checkLanePenaltyIsAlreadyWholeBooking() throws IOException {
        MeituanGoods g = goods(0);

        List<CancelPolicy> three = deriver.convertCancelPolicy(
                CHECK_IN, g.getRefundable(), g.getCpApply(), 3, false, BEFORE_ALL);

        assertEquals(28020, three.get(1).getAmount(), "原样透出");
    }

    @Test
    @DisplayName("refundable=1：明确不可退，不看阶梯")
    void nonRefundableIgnoresLadder() throws IOException {
        MeituanGoods g = goods(1);

        List<CancelPolicy> policies = deriver.convertCancelPolicy(
                CHECK_IN, g.getRefundable(), g.getCpApply(), 1, true, BEFORE_ALL);

        assertEquals(1, policies.size());
        assertEquals(0, policies.get(0).getCancelType());
    }

    @Test
    @DisplayName("免费窗已关：过期段不许流出，否则对外会承诺一个订不到的免费取消")
    void expiredFreeWindowIsDropped() throws IOException {
        MeituanGoods g = goods(0);
        // 免费段的截止是 09/20 00:00 北京时间；取它之后的时刻
        Instant afterFreeWindow = Instant.parse("2026-09-20T04:00:00Z");

        List<CancelPolicy> policies = deriver.convertCancelPolicy(
                CHECK_IN, g.getRefundable(), g.getCpApply(), 1, true, afterFreeWindow);

        assertTrue(policies.stream().noneMatch(p -> p.getType() == RefundType.NO_DEDUCTION),
                "此刻已经免不了了");
    }

    @Test
    @DisplayName("可退却没给阶梯 → UNKNOWN（空列表），不许兜成免费也不许兜成不可退")
    void refundableWithoutLadderIsUnknown() {
        List<CancelPolicy> policies = deriver.convertCancelPolicy(
                CHECK_IN, 2, List.of(), 1, true, BEFORE_ALL);

        assertTrue(policies.isEmpty());
    }

    @Test
    @DisplayName("段的时刻解析不出 → 整体 UNKNOWN，不拿其余段凑一个残缺阶梯")
    void unparseableEndDateFallsBackToUnknown() {
        MeituanCpApply bad = new MeituanCpApply();
        bad.setEndDate("2026-09-20 00:00");   // ISO 风格，不是本家的 MM/dd/yyyy HH:mm
        bad.setPenalty(100L);
        List<MeituanCpApply> raw = new ArrayList<>(List.of(bad));

        assertTrue(deriver.convertCancelPolicy(CHECK_IN, 2, raw, 1, true, BEFORE_ALL).isEmpty());
    }

    @Test
    @DisplayName("endDate 按北京时间锚定：同一串在别的时区会算出不同的剩余小时数")
    void endDateIsAnchoredToBeijing() {
        MeituanCpApply item = new MeituanCpApply();
        item.setEndDate("09/20/2026 00:00");
        item.setPenalty(0L);

        Instant parsed = MeituanProductKeyDeriver.endOf(item);

        assertEquals(Instant.parse("2026-09-19T16:00:00Z"), parsed, "北京 09-20 00:00 = UTC 09-19 16:00");
    }
}
