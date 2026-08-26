package com.trip.booking.spa.gateway.domain.shared;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 钉住全仓唯一的元→分实现。此前 3 个私有实现 + 17 处内联全部向零截断，
 * 收敛到本类后统一 HALF_UP——对供应商报到分的正常输入与截断无差，
 * 越界输入（半分、超 int 金额）宁可炸在换算处，不静默吞。
 */
class MoneyTest {

    /** 正常输入（保留到分）：与旧实现逐分一致 */
    @Test
    void exactCentsConvertLosslessly() {
        assertEquals(4579, Money.toCents(new BigDecimal("45.79")));
        assertEquals(29317, Money.toCents(new BigDecimal("293.17")));
        assertEquals(0, Money.toCents(BigDecimal.ZERO));
    }

    /** 半分走四舍五入，不是静默截断 */
    @Test
    void subCentRoundsHalfUp() {
        assertEquals(4580, Money.toCents(new BigDecimal("45.795")));
        assertEquals(4579, Money.toCents(new BigDecimal("45.794")));
    }

    /** 超 int 金额炸在换算处，不回绕成负数流进价格链路 */
    @Test
    void overflowThrowsInsteadOfWrapping() {
        assertThrows(ArithmeticException.class,
                () -> Money.toCents(new BigDecimal("99999999999")));
    }

    /** 金额永不裸奔：币种空值直接拒绝 */
    @Test
    void currencyIsMandatory() {
        assertThrows(IllegalArgumentException.class, () -> Money.ofCents(100, " "));
        assertEquals("CNY", Money.fromYuan(new BigDecimal("1.00"), "cny").currency());
        assertEquals(100, Money.fromYuan(new BigDecimal("1.00"), "CNY").amountCents());
    }
}
