package com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.pricing;

import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.ProductRespDTO;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.PriceReq;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.Supplier;
import com.trip.booking.spa.gateway.application.pricing.AbstractProductSyncSupportService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

/**
 * 艺龙查价能力入口。bean 名必须是 {@code elongProductSyncService}
 * （SupplierSourceEnum.ELONG.desc + Capability.PRICING 后缀），否则路由不到。
 */
@Service("elongProductSyncService")
public class ElongProductSyncServiceImpl extends AbstractProductSyncSupportService<List<ProductRespDTO>> {

    @Resource
    private ElongPriceService elongPriceService;

    @Override
    public List<ProductRespDTO> querySupplierPrice(PriceReq priceReq, Supplier supplier) {
        return elongPriceService.queryPrices(priceReq, supplier);
    }

    @Override
    public List<ProductRespDTO> productRespConvert(List<ProductRespDTO> products) {
        return products;
    }
}
