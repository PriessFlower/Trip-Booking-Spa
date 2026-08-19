package com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.pricing;

import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.PriceInfo;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.shared.model.response.QueryPriceResponse;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 佣金摊派的不变量钉死（issue #99）。
 *
 * <p>核心命题：<b>Σ 各晚报价 == 实时路径的总价</b>。走缓存的读路径逐晚累加重算总价
 * （{@code CachePriceServiceImpl.getPrice}），实时路径扣的是全额佣金；佣金按晚整除、
 * 余数丢弃时，同一产品两条路径会报出相差 {@code sumCommission mod n} 分的两个总价。
 * 金额极小，真正的代价是口径不唯一——对账时会冒出一批无法解释的分位差。
 */
class ExpediaCommissionSplitTest {

    private static final String CHECK_IN = "2026-09-01";

    private final ExpediaPriceServiceImpl service = new ExpediaPriceServiceImpl();

    /** 每晚一条 base_rate，金额单位为元（与 Expedia 下发形态一致，方法内部 ×100 转分） */
    private static List<List<QueryPriceResponse.Nightly>> nights(String... amounts) {
        List<List<QueryPriceResponse.Nightly>> out = new ArrayList<>();
        for (String amount : amounts) {
            QueryPriceResponse.Nightly n = new QueryPriceResponse.Nightly();
            n.setType("base_rate");
            n.setValue(amount);
            out.add(List.of(n));
        }
        return out;
    }

    private static int sumPrice(List<PriceInfo> infos) {
        return infos.stream().mapToInt(PriceInfo::getPrice).sum();
    }

    /** 元 → 分，即实时路径 totalPrice 的算法：各晚原价之和 ×100 再减全额佣金 */
    private static int realtimeTotal(int sumCommission, String... amounts) {
        int cents = 0;
        for (String a : amounts) {
            cents += (int) (Double.parseDouble(a) * 100);
        }
        return cents - sumCommission;
    }

    /**
     * 本类存在的理由：佣金不能整除时，两条路径的总价必须仍然相等。
     * 缺陷版本这里差 {@code 100 % 3 = 1} 分。
     */
    @Test
    void sumOfNightlyPricesEqualsTotalWhenCommissionIsNotDivisible() {
        List<PriceInfo> infos = service.buildQueryPriceInfos(nights("100.00", "100.00", "100.00"), CHECK_IN, 100);
        assertEquals(3, infos.size());
        assertEquals(realtimeTotal(100, "100.00", "100.00", "100.00"), sumPrice(infos));
    }

    /** 整除时行为与改动前一致 */
    @Test
    void sumOfNightlyPricesEqualsTotalWhenCommissionIsDivisible() {
        List<PriceInfo> infos = service.buildQueryPriceInfos(nights("100.00", "100.00"), CHECK_IN, 200);
        assertEquals(realtimeTotal(200, "100.00", "100.00"), sumPrice(infos));
        assertEquals(infos.get(0).getPrice(), infos.get(1).getPrice(), "整除时各晚扣减应相同");
    }

    /** 余数摊在最前面的几晚，且任意两晚相差不超过 1 分 */
    @Test
    void remainderIsSpreadOverTheEarliestNights() {
        List<PriceInfo> infos = service.buildQueryPriceInfos(nights("100.00", "100.00", "100.00"), CHECK_IN, 100);
        // 100 分摊 3 晚 → 34/33/33，故首晚报价最低
        assertEquals(10000 - 34, infos.get(0).getPrice());
        assertEquals(10000 - 33, infos.get(1).getPrice());
        assertEquals(10000 - 33, infos.get(2).getPrice());
        int max = infos.stream().mapToInt(PriceInfo::getPrice).max().orElseThrow();
        int min = infos.stream().mapToInt(PriceInfo::getPrice).min().orElseThrow();
        assertTrue(max - min <= 1, "各晚报价差不得超过 1 分");
    }

    /** 30 天窗口最坏情形（余数 29 分）也必须守住不变量 */
    @Test
    void invariantHoldsAcrossAThirtyNightWindow() {
        String[] amounts = new String[30];
        java.util.Arrays.fill(amounts, "88.88");
        List<PriceInfo> infos = service.buildQueryPriceInfos(nights(amounts), CHECK_IN, 1229);
        assertEquals(realtimeTotal(1229, amounts), sumPrice(infos));
    }

    /** 房费口径同样摊派：Σ 各晚 roomPrice 必须等于总房费减全额佣金 */
    @Test
    void roomPriceIsSplitWithTheSameRemainderRule() {
        List<PriceInfo> infos = service.buildQueryPriceInfos(nights("50.00", "50.00", "50.00"), CHECK_IN, 100);
        int sumRoom = infos.stream().mapToInt(PriceInfo::getRoomPrice).sum();
        assertEquals(15000 - 100, sumRoom);
    }

    /** nightly 为空：改动前 {@code / 0} 抛 ArithmeticException */
    @Test
    void emptyNightlyListsReturnsEmptyInsteadOfDividingByZero() {
        List<PriceInfo> infos = assertDoesNotThrow(
                () -> service.buildQueryPriceInfos(new ArrayList<>(), CHECK_IN, 100));
        assertTrue(infos.isEmpty());
    }

    /** nightly 为 null：改动前 {@code size()} 抛 NPE */
    @Test
    void nullNightlyListsReturnsEmptyInsteadOfThrowing() {
        List<PriceInfo> infos = assertDoesNotThrow(
                () -> service.buildQueryPriceInfos(null, CHECK_IN, 100));
        assertTrue(infos.isEmpty());
    }

    /** 零佣金是常态（非分销渠道），不得因摊派逻辑而改变行为 */
    @Test
    void zeroCommissionLeavesNightlyPricesUntouched() {
        List<PriceInfo> infos = service.buildQueryPriceInfos(nights("120.50", "120.50"), CHECK_IN, 0);
        assertEquals(12050, infos.get(0).getPrice());
        assertEquals(12050, infos.get(1).getPrice());
    }
}
