package com.trip.booking.spa.gateway.adapter.outbound.supplier.clwy.checkprice;

import com.trip.booking.spa.gateway.adapter.outbound.supplier.clwy.pricing.ClwyPlan;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.clwy.pricing.ClwyPriceServiceImpl;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.clwy.shared.ClwyProperties;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.clwy.shared.model.ClwyHotel;
import com.trip.booking.spa.gateway.application.checkprice.AbstractCheckPriceFlow;
import com.trip.booking.spa.gateway.application.checkprice.CheckPriceResult;
import com.trip.booking.spa.gateway.application.checkprice.LiveStock;
import com.trip.booking.spa.gateway.application.checkprice.ResolveCandidate;
import com.trip.booking.spa.gateway.application.checkprice.ResolveProperties;
import com.trip.booking.spa.gateway.domain.pricing.CheckPriceCommand;
import com.trip.booking.spa.gateway.domain.supplier.SupplierSourceEnum;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

/**
 * 差旅无忧验价能力入口。bean 名必须是 {@code clwyCheckPriceSyncService}
 * （SupplierSourceEnum.CLWY.desc + Capability.CHECK_PRICE 后缀），否则路由不到。
 * 流程（现取→找票→换票→分档→试单）在模板；供应商侧的读法在 {@link ClwyPriceServiceImpl}。
 *
 * <p><b>本家比另三家更依赖 resolve</b>：报价码是分代轮换的（cursor 取证：60 天 58 次重放
 * 仅 4 次成功、93% 撞 {@code No Availability}），即"查价拿到的码到验价时还在"是小概率。
 * 关掉 resolve 不会资损，但可订率会塌——见 {@code ClwyProperties#resolveEnabled} 的闸口声明。
 */
@Service("clwyCheckPriceSyncService")
public class ClwyCheckPriceServiceImpl extends AbstractCheckPriceFlow<ClwyHotel, ClwyPlan> {

    @Resource
    private ClwyPriceServiceImpl clwyPriceService;

    @Resource
    private ClwyProperties properties;

    @Override
    protected SupplierSourceEnum supplier() {
        return SupplierSourceEnum.CLWY;
    }

    @Override
    protected ResolveProperties resolveProperties() {
        return properties;
    }

    @Override
    protected CheckPriceResult precondition(CheckPriceCommand request) {
        return clwyPriceService.precondition(request);
    }

    @Override
    protected LiveStock<ClwyHotel> fetchLiveStock(CheckPriceCommand request, String salesEnvironment) {
        return clwyPriceService.fetchLiveStock(request);
    }

    @Override
    protected ClwyPlan findByToken(ClwyHotel hotel, CheckPriceCommand request) {
        return clwyPriceService.findPlan(hotel, request.supplierProductId());
    }

    @Override
    protected List<ResolveCandidate<ClwyPlan>> resolveCandidates(ClwyHotel hotel, CheckPriceCommand request) {
        return clwyPriceService.resolveCandidates(hotel, request);
    }

    @Override
    protected String tokenOf(ClwyPlan candidate) {
        return candidate.ratePlanId();
    }

    @Override
    protected CheckPriceResult availabilityOnlyResp(ClwyPlan candidate, ClwyHotel hotel, CheckPriceCommand request) {
        return clwyPriceService.availabilityOnlyResp(request, candidate);
    }

    @Override
    protected CheckPriceResult validate(ClwyPlan candidate, ClwyHotel hotel, CheckPriceCommand request) {
        return clwyPriceService.validate(request, hotel, candidate);
    }
}
