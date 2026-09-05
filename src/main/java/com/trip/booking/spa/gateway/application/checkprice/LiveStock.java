package com.trip.booking.spa.gateway.application.checkprice;

import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.CheckPriceRespDTO;

/**
 * 现取一趟的结果：要么是可以找票的现货，要么是适配层已经判定的终态
 * （调用未取得结果→INDETERMINATE、酒店下架→SOLD_OUT、整店无在售→各家口径）。
 * 二者互斥；哪些响应算终态只有适配层读得懂，模板不猜。
 */
public final class LiveStock<S> {

    private final S stock;
    private final CheckPriceRespDTO terminal;

    private LiveStock(S stock, CheckPriceRespDTO terminal) {
        this.stock = stock;
        this.terminal = terminal;
    }

    public static <S> LiveStock<S> of(S stock) {
        if (stock == null) {
            throw new IllegalArgumentException("现货为空应表达为终态，不是 of(null)");
        }
        return new LiveStock<>(stock, null);
    }

    public static <S> LiveStock<S> terminal(CheckPriceRespDTO outcome) {
        if (outcome == null || outcome.getOutcome() == null) {
            throw new IllegalArgumentException("终态必须带 outcome");
        }
        return new LiveStock<>(null, outcome);
    }

    public boolean isTerminal() {
        return terminal != null;
    }

    public S stock() {
        return stock;
    }

    public CheckPriceRespDTO terminal() {
        return terminal;
    }
}
