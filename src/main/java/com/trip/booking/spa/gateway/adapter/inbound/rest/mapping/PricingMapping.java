package com.trip.booking.spa.gateway.adapter.inbound.rest.mapping;

import com.trip.booking.spa.gateway.adapter.inbound.rest.request.CheckPriceReq;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.PriceReq;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.Supplier;
import com.trip.booking.spa.gateway.domain.pricing.CheckPriceCommand;
import com.trip.booking.spa.gateway.domain.pricing.PriceQuery;

/**
 * 查价与验价的入参翻译：JSON 契约 → 领域指令，只此一处。
 *
 * <p>出参方向没有翻译：{@code Product} 与 {@code CheckPriceResult} 已是契约层类型
 * （#236），控制器直接回。
 *
 * <p><b>一次请求 × 一家供应商拆成一条指令</b>：上游一次可以问多家，服务按家并行，
 * 每家只该看到自己那份坐标。此前是把整个 PriceReq（含<b>全部</b>供应商列表）连同一个
 * Supplier 一起传下去，「取错家」在编译期完全不可见——#237 修的就是那类错。
 */
public final class PricingMapping {

    private PricingMapping() {
    }

    /** 把请求里的住期人数与<b>一家</b>供应商的坐标合成一条查价指令 */
    public static PriceQuery toQuery(PriceReq req, Supplier supplier) {
        return PriceQuery.builder()
                .supplierId(supplier == null || supplier.getSupplierId() == null ? 0 : supplier.getSupplierId())
                .supplierHotelId(supplier == null ? null : supplier.getSHotelId())
                .supplierProductId(supplier == null ? null : supplier.getSProductId())
                .checkIn(req.getCheckIn())
                .checkOut(req.getCheckout())
                .roomNum(req.getRoomNum())
                .adultNum(req.getAdultNum())
                .childNum(req.getChildNum())
                .childAges(req.getChildAges())
                .currency(req.getCurrency())
                .language(req.getLanguage())
                .bedId(req.getBedId())
                .priceFlag(req.getPriceFlag())
                .occupancies(req.getOccupancies())
                .build();
    }

    public static CheckPriceCommand toCommand(CheckPriceReq req) {
        return CheckPriceCommand.builder()
                .supplierId(req.getSupplierId() == null ? 0 : req.getSupplierId())
                .supplierHotelId(req.getSHotelId())
                .supplierProductId(req.getSProductId())
                .productKey(req.getProductKey())
                .verifyLevel(req.getVerifyLevel())
                .checkIn(req.getCheckIn())
                .checkOut(req.getCheckOut())
                .roomNum(req.getRoomNum())
                .seenPrice(req.getSeenPrice())
                .adultCount(req.getAdultCount())
                .childNum(req.getChildNum())
                .childAges(req.getChildAges())
                .priceFlag(req.getPriceFlag())
                .language(req.getLanguage())
                .bedId(req.getBedId())
                .currency(req.getCurrency())
                .build();
    }
}
