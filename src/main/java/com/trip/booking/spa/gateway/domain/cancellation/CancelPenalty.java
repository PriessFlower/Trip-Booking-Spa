package com.trip.booking.spa.gateway.domain.cancellation;

import com.trip.booking.spa.gateway.domain.shared.Money;

import java.util.Objects;

/**
 * 取消违约金的事实，带来源申报。
 *
 * <p>各家给不给罚金、怎么给，是取消契约里离散度最大的一处（cursor 九家实测）：
 * 结构化字段给出（dida 带币种、艺龙、汇智、路客、飞猪 USD 分）／完全不给、只能拿验价时
 * 抓到的退改政策自己算（clwy、途灵、喜玩、美团、绿云）。来源必须显式申报——
 * "字段给的 0" 和 "没给所以不知道" 是两回事，塌在一起上游就会把「不知道」当「免费取消」。
 *
 * <p>此前艺龙把罚金拼进中文文案（"取消已受理，违约金 X 元"），上游要拿只能正则中文串，
 * 且单位是元而契约其余金额一律是分。本类是那条老路的替代。
 */
public final class CancelPenalty {

    /** 罚金从哪来 */
    public enum PenaltySource {
        /** 供应商取消响应的结构化字段直接给出 */
        FIELD,
        /** 供应商不给，由验价时点的退改政策推算（推算方自负口径） */
        POLICY_DERIVED,
        /** 无从得知：既无字段也未推算。上游不得把它当 0 或当免费取消 */
        NONE
    }

    private static final CancelPenalty UNKNOWN = new CancelPenalty(PenaltySource.NONE, null);

    private final PenaltySource source;
    private final Money amount;

    private CancelPenalty(PenaltySource source, Money amount) {
        this.source = source;
        this.amount = amount;
    }

    public static CancelPenalty fromField(Money amount) {
        return new CancelPenalty(PenaltySource.FIELD,
                Objects.requireNonNull(amount, "申报为字段来源就必须给出金额"));
    }

    public static CancelPenalty derivedFromPolicy(Money amount) {
        return new CancelPenalty(PenaltySource.POLICY_DERIVED,
                Objects.requireNonNull(amount, "申报为政策推算就必须给出金额"));
    }

    /** 无从得知。刻意是单例语义：这一态不携带任何金额 */
    public static CancelPenalty unknown() {
        return UNKNOWN;
    }

    public PenaltySource source() {
        return source;
    }

    /** source 为 NONE 时为 null */
    public Money amount() {
        return amount;
    }
}
