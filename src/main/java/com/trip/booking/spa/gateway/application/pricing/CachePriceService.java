package com.trip.booking.spa.gateway.application.pricing;

import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.ProductRespDTO;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.PriceReq;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.Supplier;

import java.util.List;

/**
 * @description:缓存处理
 * @author: dick_w
 * @date: 2025/3/12 10:20
 * @param:
 * @return:
 **/
public interface CachePriceService {

    /** 取该店该住期缓存里的全部产品 */
    List<ProductRespDTO> getPrice(PriceReq priceReq, Supplier supplier);

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

    void productToCache(List<ProductRespDTO> respDTOS, PriceReq request);

}
