package com.trip.booking.spa.gateway.domain.product;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 占用规范串。
 *
 * <p><b>这些断言同时钉住两件事</b>：① 串的形状；② 它与改造前两处实现（艺龙
 * {@code buildOccupancy}、Expedia 内联拼接）<b>逐字节等价</b>——因为这个串是 productKey 的
 * {@code o:} 成分，形状一变，全库既有 productKey 全部作废。
 */
class OccupancyTest {

    @Test
    @DisplayName("只有成人：就是人数本身")
    void adultsOnly() {
        assertEquals("2", Occupancy.canonical(2, 0, List.of()));
        assertEquals("1", Occupancy.canonical(1, null, null));
    }

    @Test
    @DisplayName("带儿童：首个用 -，其余用 ,")
    void withChildren() {
        assertEquals("2-9", Occupancy.canonical(2, 1, List.of(9)));
        assertEquals("2-9,4", Occupancy.canonical(2, 2, List.of(9, 4)));
    }

    @Test
    @DisplayName("childNum=0 时忽略年龄列表——与改造前一致")
    void childAgesIgnoredWhenCountIsZero() {
        assertEquals("2", Occupancy.canonical(2, 0, List.of(9, 4)));
    }

    @Test
    @DisplayName("年龄顺序即供应商口径，不排序")
    void ageOrderIsPreserved() {
        assertEquals("2-4,9", Occupancy.canonical(2, 2, List.of(4, 9)));
        assertEquals("2-9,4", Occupancy.canonical(2, 2, List.of(9, 4)));
    }

    @Test
    @DisplayName("成人数缺失按 1 计——缺人数不该让整条不可用")
    void missingAdultsFallsBackToOne() {
        assertEquals("1", Occupancy.canonical(null, 0, List.of()));
        assertEquals("1", Occupancy.canonical(0, 0, List.of()));
    }

    @Test
    @DisplayName("每间一条")
    void perRoomRepeats() {
        assertEquals(List.of("2", "2"), Occupancy.perRoom(2, 2, 0, List.of()));
        assertEquals(List.of("2-9"), Occupancy.perRoom(null, 2, 1, List.of(9)));
    }
}
