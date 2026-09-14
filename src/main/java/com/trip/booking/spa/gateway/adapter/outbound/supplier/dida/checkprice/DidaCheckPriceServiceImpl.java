package com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.checkprice;

import com.trip.booking.spa.gateway.application.checkprice.CheckPriceResult;
import com.trip.booking.spa.gateway.domain.pricing.CheckPriceCommand;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.pricing.DidaPriceServiceImpl;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.DidaProperties;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.model.DidaHotel;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.dida.shared.model.DidaRatePlan;
import com.trip.booking.spa.gateway.application.checkprice.AbstractCheckPriceFlow;
import com.trip.booking.spa.gateway.application.checkprice.LiveStock;
import com.trip.booking.spa.gateway.application.checkprice.ResolveCandidate;
import com.trip.booking.spa.gateway.application.checkprice.ResolveProperties;
import com.trip.booking.spa.gateway.domain.supplier.SupplierSourceEnum;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

/**
 * 道旅验价能力入口。bean 名必须是 {@code didaCheckPriceSyncService}
 * （SupplierSourceEnum.DIDA.desc + Capability.CHECK_PRICE 后缀），否则路由不到。
 * 流程（现取→找票→换票→分档→验价）在模板；供应商侧的读法在 {@link DidaPriceServiceImpl}。
 */
@Service("didaCheckPriceSyncService")
public class DidaCheckPriceServiceImpl extends AbstractCheckPriceFlow<DidaHotel, DidaRatePlan> {

    @Resource
    private DidaPriceServiceImpl didaPriceService;

    @Resource
    private DidaProperties properties;

    @Override
    protected SupplierSourceEnum supplier() {
        return SupplierSourceEnum.DIDA;
    }

    @Override
    protected ResolveProperties resolveProperties() {
        return properties;
    }

    @Override
    protected CheckPriceResult precondition(CheckPriceCommand request) {
        return didaPriceService.precondition(request);
    }

    @Override
    protected LiveStock<DidaHotel> fetchLiveStock(CheckPriceCommand request, String salesEnvironment) {
        return didaPriceService.fetchLiveStock(request);
    }

    @Override
    protected DidaRatePlan findByToken(DidaHotel hotel, CheckPriceCommand request) {
        return didaPriceService.findPlan(hotel, request.supplierProductId());
    }

    @Override
    protected List<ResolveCandidate<DidaRatePlan>> resolveCandidates(DidaHotel hotel, CheckPriceCommand request) {
        return didaPriceService.resolveCandidates(hotel, request);
    }

    @Override
    protected String tokenOf(DidaRatePlan plan) {
        return plan.getRatePlanId();
    }

    @Override
    protected CheckPriceResult inspect(DidaRatePlan plan, DidaHotel hotel, CheckPriceCommand request) {
        return didaPriceService.inspect(plan, request);
    }

    @Override
    protected CheckPriceResult availabilityOnlyResp(DidaRatePlan plan, DidaHotel hotel, CheckPriceCommand request) {
        return didaPriceService.availabilityOnlyResp(request, plan);
    }

    @Override
    protected CheckPriceResult validate(DidaRatePlan plan, DidaHotel hotel, CheckPriceCommand request) {
        return didaPriceService.validate(request, hotel, plan);
    }
}
