package com.trip.booking.spa.gateway.domain.booking;

/**
 * 验价深度——上游想要多确定的答案，决定本次是否打供应商的验价接口。
 *
 * <p><b>为什么要让上游选</b>：上游 tg-trip-cursor 有两处验价，走的是同一个端点，
 * 但超时预算差一个量级（2026-08-21 核对 cursor 侧 {@code TbCheckPriceTimeouts}）：
 *
 * <ul>
 *   <li>渠道验价（客人点房型、进填单页）——供应商预算 <b>1200ms</b></li>
 *   <li>下单前验价（客人已提交、真下单之前）——供应商预算 <b>10s</b></li>
 * </ul>
 *
 * <p>而艺龙链路的实测耗时是：{@code hotel.detail} 约 1.2s、
 * {@code hotel.detail + hotel.data.validate} 约 4.6s。也就是说完整验价<b>塞不进第一档</b>，
 * 而第一档要的其实只是"有没有货"。此前两档都走完整验价，第一档必然超时。
 *
 * <p><b>为什么不由 SPA 自己猜</b>：SPA 收到的两次请求形状完全一样，分不出是哪一档
 * （cursor 侧区分两档的 {@code orderVerify} 标记不会传给 SPA）。猜不出来就只能一律走最重的
 * 那条，于是轻的那一档被拖死——所以这个意图必须由调用方显式声明。
 */
public enum VerifyLevel {

    /**
     * 只问"有没有货"：单打 {@code hotel.detail}，<b>不打验价</b>。
     *
     * <p>结果为 {@link CheckPriceOutcome#AVAILABLE} 时<b>不签发报价句柄</b>（offerId 为 null），
     * 上游不得据此下单。不签的理由不是省事：现货里拿到的马甲与报价码是会话级易腐凭证，
     * 到真正下单那一刻必然已经过期，签出去只会诱导上游拿一个必死的凭据去建单。
     *
     * <p>此档下退改条款取 {@code hotel.detail} 的 {@code PrepayResult}——这也是艺龙
     * 【国际酒店】国际对接指南的明文要求（「取消政策取 PrepayResult」）。
     */
    AVAILABILITY,

    /**
     * 要"此刻能不能下单"的确证：{@code hotel.detail} 现取现验（R-3.1）后再打
     * {@code hotel.data.validate}，通过才签发报价句柄。
     *
     * <p><b>缺省档</b>：请求未指定时按此处理——旧调用方不带该字段，行为与改动前一致，
     * 且默认落在"更确定"的一侧（宁可慢，不可把不确定说成确定）。
     */
    BOOKABLE
}
