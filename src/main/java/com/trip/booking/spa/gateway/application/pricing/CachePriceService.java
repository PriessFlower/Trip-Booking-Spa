package com.trip.booking.spa.gateway.application.pricing;

import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.ProductRespDTO;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.PriceReq;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.Supplier;

import com.trip.booking.spa.gateway.domain.booking.PricingOutcome;

import java.util.List;

/**
 * @description:缓存处理
 * @author: dick_w
 * @date: 2025/3/12 10:20
 * @param:
 * @return:
 **/
public interface CachePriceService {

    /** 取该店该住期缓存里的全部产品。不区分「没刷到」与「刷到了但无货」——需要区分时用
     * {@link #getPriceResult}。 */
    List<ProductRespDTO> getPrice(PriceReq priceReq, Supplier supplier);

    /**
     * 取缓存并<b>如实分态</b>（F-5.1 / F-5.2）。
     *
     * <p>此前出价读缓存只有「有产品」和「没有」两种结果，空一律回报
     * {@link PricingOutcome#INDETERMINATE}。于是三件不同的事塌成了一态：
     * <ul>
     *   <li>这一片压根没刷过（如 2 人的占用片，而刷价只刷 1 人）；</li>
     *   <li>刷过、供应商明确答无在售；</li>
     *   <li>刷过、但已过 TTL。</li>
     * </ul>
     * 第二种是<b>确定事实</b>，说成「未能确认」会诱发上游无谓重试；而反过来把前两种
     * 说成「无货」更糟——那是拿"我们没问"冒充"供应商说没有"。
     *
     * <p>做法依 F-5.2：刷价拿到 NO_INVENTORY 时照常落缓存（写无货标记），读到标记即
     * {@link PricingOutcome#NO_INVENTORY}，键整个不存在才是 INDETERMINATE。
     */
    PricingResult getPriceResult(PriceReq priceReq, Supplier supplier);

    /**
     * 只取缓存字段等于 {@code cacheField} 的那一条。
     *
     * <p><b>字段名必须显式传</b>，不再从 {@code Supplier.sProductId} 顺手取：
     * 缓存字段自 0853d11 起是 productKey，而 {@code sProductId} 这个名字说的是报价码。
     * 让调用方各自拼这个键，正是「两端靠约定对齐」的病灶——那次改名只改了写入侧，
     * 读取侧还按旧字段找，恒 miss 且只有一条 warn。
     *
     * @param cacheField 缓存字段（productKey）；为空则等同于 {@link #getPrice}
     */
    List<ProductRespDTO> getPrice(PriceReq priceReq, Supplier supplier, String cacheField);

    /**
     * 把一轮查价的产物落缓存。
     *
     * <p><b>{@code supplier} 必须显式传</b>，不可从 {@code request.getSuppliers()} 取：
     * 刷价路径构造的 {@link PriceReq} <b>不带 suppliers</b>（酒店是另一个参数），
     * 从里面取到的是 null。2026-08-20 无货标记就栽在这里——单测自己构造了带 suppliers 的
     * 请求，真实调用方永远不会那样构造，于是标记一条都没写成、日志里只有一行
     * 「请求里没有酒店 id，跳过」。
     *
     * <p>空列表不是"什么都不做"：它是「刷到了、供应商明确无在售」这个确定事实，
     * 依 F-5.2 照常落缓存（写无货标记）。
     */
    void productToCache(List<ProductRespDTO> respDTOS, PriceReq request, Supplier supplier);

}
