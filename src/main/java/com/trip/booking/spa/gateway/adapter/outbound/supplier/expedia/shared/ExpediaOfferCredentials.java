package com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.shared;

/**
 * Expedia 报价句柄里存放的凭据键名。
 *
 * <p>键名集中在此，供验价（写入方）与下单（读取方）共同引用，
 * 而不是两边各写一个字面量——那样一旦有人改动其中一处，另一处会在运行时才发现取不到值。
 *
 * <p>Expedia 目前只需一项凭据（下单链接自带令牌与本次报价的全部上下文）。
 * 其余供应商所需的凭据项更多，各自定义自己的键名即可，网关不解释这些键。
 */
public final class ExpediaOfferCredentials {

    /** 验价响应给出的 {@code links.book.href}，下单即向该地址提交 */
    public static final String BOOK_HREF = "bookHref";

    private ExpediaOfferCredentials() {
    }
}
