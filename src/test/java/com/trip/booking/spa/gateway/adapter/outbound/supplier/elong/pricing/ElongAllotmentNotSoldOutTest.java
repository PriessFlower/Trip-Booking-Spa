package com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.pricing;

import com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared.model.response.ElongRatePlan;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 钉死 {@code CurrentAlloment} 不再否决单间预订。
 *
 * <p><b>为什么必须有</b>：该字段是艺龙的"房量限额"（最多允许订几间），<b>0/999/9999 表示不限</b>，
 * 不是"剩余房量"。此前代码按「{@code <=0} 不可订」处理，把"不限"当成"无房"，
 * 于是在查价出报（{@code isOnSale} 是刷价出报的筛子）、resolve 候选筛选、验价 SOLD_OUT 三处
 * 否决了本可售卖的产品——方向是<b>少卖</b>。
 *
 * <p>官方对国内与国际两套口径都写明「最少有 1 间可以预定」，而我方一次只订 1 间
 * （{@code NumberOfRooms=1}），故该字段不该参与"有没有房"的判定。
 *
 * <p>现网未踩到（2026-08-21 实测 4,239 个报价中 0 与 null 各 0 条，国际用真实数字或 9999），
 * 但渠道验价改为 detail-only 后，"有没有房"完全依赖 {@code Status}，该判定成为承重逻辑，
 * 故用测试固定住语义。多间预订（{@code NumberOfRooms > 1}）需要该字段时，须按境内外分口径解读。
 */
class ElongAllotmentNotSoldOutTest {

    private static boolean isOnSale(Integer allotment, Boolean status) throws Exception {
        ElongRatePlan plan = new ElongRatePlan();
        plan.setStatus(status);
        plan.setCurrentAlloment(allotment);
        plan.setRoomTypeId("0007");
        Method m = ElongPriceServiceImpl.class.getDeclaredMethod("isOnSale", ElongRatePlan.class);
        m.setAccessible(true);
        return (boolean) m.invoke(null, plan);
    }

    @ParameterizedTest(name = "CurrentAlloment={0} 仍视为在售")
    @ValueSource(ints = {0, 1, 4, 5, 999, 9999})
    @DisplayName("0 / 999 / 9999 是「不限」的哨兵值，不得当作无房")
    void allotmentNeverVetoesOnSale(int allotment) throws Exception {
        assertThat(isOnSale(allotment, Boolean.TRUE)).isTrue();
    }

    @Test
    @DisplayName("CurrentAlloment 缺失也不否决——缺一个限额不等于没有房")
    void nullAllotmentDoesNotVeto() throws Exception {
        assertThat(isOnSale(null, Boolean.TRUE)).isTrue();
    }

    @Test
    @DisplayName("真正的下架仍由 Status=false 拦住")
    void statusFalseIsStillNotOnSale() throws Exception {
        assertThat(isOnSale(9999, Boolean.FALSE)).isFalse();
    }

    @Test
    @DisplayName("Status 缺失按在售——艺龙不下发该字段时不得擅自下架")
    void nullStatusIsTreatedAsOnSale() throws Exception {
        assertThat(isOnSale(0, null)).isTrue();
    }
}
