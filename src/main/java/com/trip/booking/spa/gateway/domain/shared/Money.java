package com.trip.booking.spa.gateway.domain.shared;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * 金额：分 + 币种，缺一不可。
 *
 * <p>此前金额在契约里是裸的 Integer 分值，币种散落在三个并存字段
 * （storePayCurrency / currencyType / currency）里靠约定对齐——两家在产供应商都报 CNY
 * 时这套约定不出事，接入 USD 供应商（飞猪/美团/喜玩）后"这个数是什么币种"在契约上
 * 就有了三个可能的答案。金额与币种同乘同除、永不分离，是本类存在的全部理由。
 *
 * <p><b>没有换汇</b>：网关只如实转述供应商的金额事实（B4 的"单一币种"由上游或
 * 汇率层达成）。需要换汇而汇率不可得时，调用方必须落"不确定"，禁止拿原值兜底——
 * cursor 的飞猪取消罚金正是拿 USD 原值当 CNY 用、只打一行 error 不阻断。
 */
public final class Money {

    private final long amountCents;
    private final String currency;

    private Money(long amountCents, String currency) {
        this.amountCents = amountCents;
        this.currency = currency;
    }

    /** @param currency ISO 4217 大写三字码，如 CNY / USD；空值直接拒绝，金额不许无币种流转 */
    public static Money ofCents(long amountCents, String currency) {
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("金额必须带币种，禁止裸数值流转");
        }
        return new Money(amountCents, currency.trim().toUpperCase());
    }

    /**
     * 元 → 分。仓里此前有 3 个私有实现 + 17 处内联做这件事。统一为 HALF_UP：
     * 供应商报价保留到分，正常输入不触发舍入（旧实现全部向零截断，对两位小数输入
     * 与 HALF_UP 无差）；真出现半分时四舍五入比静默截断更不意外。
     */
    public static Money fromYuan(BigDecimal yuan, String currency) {
        Objects.requireNonNull(yuan, "金额为空时应表达为「无该项金额」，不是 0 也不是 null Money");
        return ofCents(toCents(yuan), currency);
    }

    /**
     * 大单位 → 分（×100，HALF_UP，越界抛出而非静默回绕）。
     *
     * <p>本方法只统一倍数与舍入，<b>不承载币种</b>——币种语义由调用方的字段负责
     * （USD 的"分"是 cent、CNY 是分，倍数一致）。契约字段仍是裸 Integer 分值的存量
     * 场景用它；新领域模型一律用带币种的 {@link #fromYuan}/{@link #ofCents}。
     */
    public static int toCents(BigDecimal majorUnits) {
        return majorUnits.multiply(BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.HALF_UP).intValueExact();
    }

    public long amountCents() {
        return amountCents;
    }

    public String currency() {
        return currency;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Money)) {
            return false;
        }
        Money other = (Money) o;
        return amountCents == other.amountCents && currency.equals(other.currency);
    }

    @Override
    public int hashCode() {
        return Objects.hash(amountCents, currency);
    }

    @Override
    public String toString() {
        return amountCents + "分(" + currency + ")";
    }
}
