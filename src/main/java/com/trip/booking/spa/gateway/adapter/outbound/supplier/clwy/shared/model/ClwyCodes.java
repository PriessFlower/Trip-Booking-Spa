package com.trip.booking.spa.gateway.adapter.outbound.supplier.clwy.shared.model;

/**
 * 差旅无忧的返回码。<b>官方只定义了两个值</b>（各接口文档的 Code 字段说明一致，2026-09-14 查阅）：
 * {@code 200} 成功、{@code 500} 错误。没有细分码表，失败原因只在 {@code message} 里。
 *
 * <p>这带来的直接后果：三态分类<b>只能靠文案</b>。据 cursor 生产一周（2026-09-07~14）
 * 8,585 次成功 + 427 次失败的全量样本，{@code code=500} 的 message <b>只出现过一种取值</b>：
 * {@code "No Availability"}。故仓内只为它立判据，其余文案一律不确定——码表之外不猜。
 */
public final class ClwyCodes {

    public static final int OK = 200;

    public static final int ERROR = 500;

    /**
     * 无库存。生产一周里 {@code code=500} 的唯一文案（427/427）。
     *
     * <p>它在查价与验价两档语义不同，故不在此处判态：查价拿到它＝这家这住期没货；验价拿到它
     * 既可能是「所点报价码已换代」也可能是「整店真没货」，两者要靠"现取整店还有没有货"来分——
     * 那正是 {@code AbstractCheckPriceFlow} 现取→找票→换票这条路会自然回答的问题。
     */
    public static final String NO_AVAILABILITY = "No Availability";

    private ClwyCodes() {
    }
}
