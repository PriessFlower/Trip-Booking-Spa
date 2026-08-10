package com.trip.booking.spa.core.api.service.impl;

import com.trip.booking.spa.core.api.dto.ProductRespDTO;
import com.trip.booking.spa.core.api.request.PriceReq;
import com.trip.booking.spa.core.api.request.Supplier;
import com.trip.booking.spa.core.api.expedia.service.ExpediaPriceService;
import com.trip.booking.spa.core.api.expedia.utils.ExpediaHelper;
import com.trip.booking.spa.core.api.service.AbstractProductSyncSupportService;
import com.trip.booking.spa.core.api.service.RecordLogService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.utils.Lists;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.util.List;

@Service("expediaProductSyncService")
@Slf4j
public class ExpediaProductSyncServiceImpl extends AbstractProductSyncSupportService<List<ProductRespDTO>> {

    @Autowired
    private ExpediaPriceService expediaPriceService;

    @Resource(name = "redisRecordLogServiceImpl")
    private RecordLogService redisRecordLogServiceImpl;

    @Override
    public List<ProductRespDTO> querySupplierPrice(PriceReq priceReq, Supplier supplier) {
        redisRecordLogServiceImpl.recordExpediaQps();
        if (StringUtils.isNotBlank(supplier.getSProductId())) {
            return expediaPriceService.queryProductPrice(priceReq, supplier);
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
            return Lists.newArrayList();
        }
        return expediaPriceService.queryPrices(priceReq, supplier);
    }

    @Override
    public List<ProductRespDTO> productRespConvert(List<ProductRespDTO> queryPriceResponse) {
        return queryPriceResponse;
    }
}
