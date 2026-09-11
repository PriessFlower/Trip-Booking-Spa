package com.trip.booking.spa.gateway.application.checkprice;

import com.trip.booking.spa.gateway.application.checkprice.CheckPriceResult;
import com.trip.booking.spa.gateway.domain.product.Product;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.PriceReq;

import java.util.List;
import java.util.function.Function;

/**
 * 现取一趟的结果：要么是可以找票的现货，要么是适配层已经判定的终态
 * （调用未取得结果→INDETERMINATE、酒店下架→SOLD_OUT、整店无在售→各家口径）。
 * 二者互斥；哪些响应算终态只有适配层读得懂，模板不猜。
 */
public final class LiveStock<S> {

    private final S stock;
    private final CheckPriceResult terminal;
    private Function<PriceReq, List<Product>> freshConverter;

    private LiveStock(S stock, CheckPriceResult terminal) {
        this.stock = stock;
        this.terminal = terminal;
    }

    /**
     * 挂上验价即刷的转换器：拿这份<b>原始</b>现货响应转成可入缓存的报价。
     *
     * <p>为什么用闭包而不是模板的抽象方法：各家的原始响应与"票所在的对象"往往不是同一个
     * 类型（艺龙 {@code ElongHotelDetailResponse} vs 票所在的 {@code ElongHotel}），
     * 由适配层在此处捕获原始响应，模板只需要一个「PriceReq → 报价」的函数。
     *
     * <p>返回 {@code null} = 没问出结果，不动缓存（F-5.1）；空列表 = 明确无货（打无货标记
     * 清僵尸价，B7）；非空 = 现货报价。<b>终态也可以挂</b>——下架/整店无售正是要落无货标记的时候。
     *
     * <p>不挂 = 该家没有验价即刷（如 Expedia），模板什么都不做。
     */
    public LiveStock<S> freshConvertedBy(Function<PriceReq, List<Product>> converter) {
        this.freshConverter = converter;
        return this;
    }

    public Function<PriceReq, List<Product>> freshConverter() {
        return freshConverter;
    }

    public static <S> LiveStock<S> of(S stock) {
        if (stock == null) {
            throw new IllegalArgumentException("现货为空应表达为终态，不是 of(null)");
        }
        return new LiveStock<>(stock, null);
    }

    public static <S> LiveStock<S> terminal(CheckPriceResult outcome) {
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

    public CheckPriceResult terminal() {
        return terminal;
    }
}
