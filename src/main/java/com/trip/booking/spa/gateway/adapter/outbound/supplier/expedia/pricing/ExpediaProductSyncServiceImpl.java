package com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.pricing;

import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.ProductRespDTO;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.PriceReq;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.Supplier;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.shared.ExpediaHelper;
import com.trip.booking.spa.gateway.application.pricing.AbstractProductSyncSupportService;
import com.trip.booking.spa.gateway.application.pricing.PricingResult;
import com.trip.booking.spa.platform.observability.RecordLogService;
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
@org.springframework.cloud.context.config.annotation.RefreshScope
public class ExpediaProductSyncServiceImpl extends AbstractProductSyncSupportService {

    /**
     * 当天入住拦截闸口的开关（§3.8.5 三项声明见下方使用点）。
     *
     * <p>默认 {@code true} 保持现行为。它存在的理由是 §3.8.6：闸口必须能<b>不发版关掉</b>。
     * 此前只能靠改制品里的名单再发版，而名单是 11,681 条的 json——出了误拦，
     * 唯一的止血手段是走一遍完整发布。
     */
    @org.springframework.beans.factory.annotation.Value("${supplier.expedia.same-day-block-enabled:true}")
    private boolean sameDayBlockEnabled;

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
        // 执行面：所有节点的在线查价路径。开关：supplier.expedia.same-day-block-enabled（默认开）。
        //
        // 名单为什么仍留在制品里（2026-08-20 复核，取代原先「待迁 Nacos」那条待办）：
        // 11,681 条 126KB，超过 Nacos 单配置默认上限 100KB，塞进去会把整份配置撑爆。
        // 且它本质是【酒店层事实】（这家店当天入住不可靠），按 R-2.9 的同一判据该归
        // supplier_hotel_base 的一列——Nacos 放的是运维开关，不是业务数据。
        // 该迁移待 Expedia 链路恢复流量后再做（2026-08-20 实测：24h 零调用、闸口零触发）。
        //
        // §3.8.6 真正要的是「不发版也能关掉」，那与名单放哪无关，故补的是上面那个开关。
        if (sameDayBlockEnabled
                && ExpediaHelper.hotelIdList.contains(priceReq.getSuppliers().get(0).getSHotelId())
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
