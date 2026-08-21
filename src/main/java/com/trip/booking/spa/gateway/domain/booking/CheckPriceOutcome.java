package com.trip.booking.spa.gateway.domain.booking;

/**
 * 验价结果分态。
 *
 * <p><b>为什么不能只用「可订／不可订」两态</b>：不可订至少有三种截然不同的成因，
 * 而它们的正确处置互相矛盾：
 *
 * <ul>
 *   <li>{@link #SOLD_OUT}——如实告诉旅客满房，可以引导换房换店</li>
 *   <li>{@link #RATE_DEAD}——旅客点的是一份已经消失的报价，正确做法是重新查价再点，
 *       而不是告诉旅客「没房了」（同一酒店同一房型很可能仍有房）</li>
 *   <li>{@link #INDETERMINATE}——我们并不知道有没有房，不该替供应商回答</li>
 * </ul>
 *
 * <p>塌成一态的代价有实测：tg-trip-cursor 的取价函数把「死店」「真无房」「瞬时超时」
 * 三种情形一律返回空，上层因此永远收不到「这家店没了」的信号，最终长成道旅韩国
 * 41,934 个店级映射里 25.6% 是死 id、339 个死 id 残留 66,469 行在售僵尸价的局面。
 *
 * <p><b>最要紧的一条纪律</b>：{@link #RATE_DEAD} 绝不可折叠进 {@link #INDETERMINATE}。
 * 前者是确定性的（重试必再失败，应重新查价），后者是暂时性的（可重试）。
 * cursor 把艺龙的产品级死码打成「无响应」，而「无响应」又落进了硬错误集合被数据库价
 * 兜底成「可订」，于是确定性死产品被反复曝光、用户下单后在建单段暴死——丢的是真单。
 * 反过来也不许：{@link #INDETERMINATE} 一律不得被上游当作可订。
 */
public enum CheckPriceOutcome {

    /** 验价通过，可下单。此时报价句柄与价格字段必然有值 */
    BOOKABLE,

    /**
     * <b>有货，但未向供应商确认可订性</b>——只查了现货（hotel.detail），没打验价。
     * 仅当请求指定 {@code verifyLevel=AVAILABILITY} 时返回。
     *
     * <p><b>与 {@link #BOOKABLE} 差的是一句承诺</b>：BOOKABLE 意味着供应商刚刚确认这份报价
     * 此刻可成单；AVAILABLE 只意味着它出现在供应商的在售清单里。两者之间有实测差距——
     * 2026-08-21 生产实打，现货里报出来却验价失败的约 3%（多为 {@code H001083} 内层
     * {@code 7010 国际产品不可定}），而这类死态<b>在 hotel.detail 里没有任何字段能预判</b>：
     * 把可订与不可订的报价逐字段比对（BookingChannels / sellChannels / CooperationType /
     * SignType / CustomerType / NeedIdNo / Identification / InstantConfirmation）完全一致。
     *
     * <p><b>此态下 offerId 必然为 null，上游禁止据此下单。</b>它存在的理由是渠道验价那一档的
     * SLA：供应商预算 1200ms，而 detail 实测约 1.2s、detail+validate 约 4.6s——塞不进去。
     * 用它换来"先让客人看到有货"，把可订性推迟到下单前那一档确认。
     *
     * <p>不复用 BOOKABLE 表达它，是因为 BOOKABLE 的语义里含着"句柄有值、可以下单"这条承诺
     * （R-1.6 不确定不许说成确定）。上游若把 AVAILABLE 当 BOOKABLE 处理，会拿着 null 句柄下单。
     */
    AVAILABLE,

    /** 供应商明确回答该产品已售罄。确定性结果 */
    SOLD_OUT,

    /**
     * 所点的报价已不存在于供应商当前的报价中——报价标识过期、产品下架、或所选床型已不可选。
     *
     * <p>确定性结果：拿同一个产品标识重试必再失败。上游应重新查价后让旅客重新选择。
     * <b>不要告知旅客「已满房」</b>，同一房型很可能仍然有房，只是那份报价换代了。
     */
    RATE_DEAD,

    /**
     * 未能得出结论：调用超时、被限流、供应商返回 5xx，或响应无法判读。
     *
     * <p>上游<b>禁止</b>据此认定可订，也不宜据此告知旅客满房；稍后重试即可。
     */
    INDETERMINATE
}
