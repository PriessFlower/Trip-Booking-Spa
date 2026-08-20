package com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.pricing;

import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.ProductRespDTO;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.PriceReq;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.Supplier;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.shared.ExpediaHelper;
import com.trip.booking.spa.gateway.application.pricing.AbstractProductSyncSupportService;
import com.trip.booking.spa.gateway.application.pricing.PricingResult;
import com.trip.booking.spa.gateway.application.misc.RecordLogService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.util.List;

@Service("expediaProductSyncService")
@Slf4j
public class ExpediaProductSyncServiceImpl extends AbstractProductSyncSupportService {

    @Autowired
    private ExpediaPriceService expediaPriceService;

    @Resource(name = "redisRecordLogServiceImpl")
    private RecordLogService redisRecordLogServiceImpl;

    @Override
    public PricingResult querySupplierPrice(PriceReq priceReq, Supplier supplier) {
        redisRecordLogServiceImpl.recordExpediaQps();
        if (StringUtils.isNotBlank(supplier.getSProductId())) {
            // 单产品路径：内部把「验价失败」「所点报价不在响应中」「调用失败」一律折成 null，
            // 无从分辨，故一律落未能确认——不可说成无房（待做：与 checkPrices 一样按响应分态）
            List<ProductRespDTO> products = expediaPriceService.queryProductPrice(priceReq, supplier);
            return CollectionUtils.isEmpty(products)
                    ? PricingResult.indeterminate() : PricingResult.available(products);
        }
        // 闸口：泰国及韩国部分酒店当天入住不报价（名单来自打包进制品的 expediaHotelList.json）。
        // 误开风险=这些酒店当天入住报价后大概率验价失败；误关风险=当天入住直接无价，用户侧表现为查无报价。
        // 执行面：所有节点的在线查价路径。
        // 待办：名单为硬编码，违反 PROJECT.md §3.8.6，需迁至 Nacos。
        if (ExpediaHelper.hotelIdList.contains(priceReq.getSuppliers().get(0).getSHotelId())
                && LocalDate.parse(priceReq.getCheckIn()).equals(LocalDate.now()))
        {
            log.info("[gate] expedia.same-day-blocked-hotels 拦截: hotelId={}, checkIn={}",
                    supplier.getSHotelId(), priceReq.getCheckIn());
            // 闸口拒绝报价归入「无可售」而非「未能确认」：对上游而言同样是这里没有可卖的，
            // 且重试无用，报未能确认只会诱发无谓重试
            return PricingResult.noInventory();
        }
        return expediaPriceService.queryPrices(priceReq, supplier);
    }
}
