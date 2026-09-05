package com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.checkprice;

import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.CheckPriceRespDTO;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.CheckPriceReq;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.pricing.ElongPriceServiceImpl;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.pricing.ElongPriceServiceImpl.PlanWithRoom;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared.ElongProperties;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared.model.response.ElongHotelDetailResponse.ElongHotel;
import com.trip.booking.spa.gateway.application.checkprice.AbstractCheckPriceFlow;
import com.trip.booking.spa.gateway.application.checkprice.LiveStock;
import com.trip.booking.spa.gateway.application.checkprice.ResolveCandidate;
import com.trip.booking.spa.gateway.application.checkprice.ResolveProperties;
import com.trip.booking.spa.gateway.domain.supplier.SupplierSourceEnum;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

/**
 * 艺龙验价能力入口。bean 名必须是 {@code elongCheckPriceSyncService}
 * （SupplierSourceEnum.ELONG.desc + Capability.CHECK_PRICE 后缀），否则路由不到。
 * 流程（现取→找票→换票→分档→验价）在模板；供应商侧的读法在 {@link ElongPriceServiceImpl}。
 */
@Service("elongCheckPriceSyncService")
public class ElongCheckPriceServiceImpl extends AbstractCheckPriceFlow<ElongHotel, PlanWithRoom> {

    @Resource
    private ElongPriceServiceImpl elongPriceService;
    @Resource
    private ElongProperties properties;

    @Override
    protected SupplierSourceEnum supplier() {
        return SupplierSourceEnum.ELONG;
    }

    @Override
    protected ResolveProperties resolveProperties() {
        return properties;
    }

    @Override
    protected CheckPriceRespDTO precondition(CheckPriceReq request) {
        return elongPriceService.precondition(request);
    }

    @Override
    protected LiveStock<ElongHotel> fetchLiveStock(CheckPriceReq request, String salesEnvironment) {
        return elongPriceService.fetchLiveStock(request);
    }

    @Override
    protected PlanWithRoom findByToken(ElongHotel hotel, CheckPriceReq request) {
        return elongPriceService.findPlan(hotel, request.getSProductId());
    }

    @Override
    protected List<ResolveCandidate<PlanWithRoom>> resolveCandidates(ElongHotel hotel, CheckPriceReq request) {
        return elongPriceService.resolveCandidates(hotel, request);
    }

    @Override
    protected String tokenOf(PlanWithRoom found) {
        return found.plan().getGoodsUniqId();
    }

    @Override
    protected CheckPriceRespDTO inspect(PlanWithRoom found, ElongHotel hotel, CheckPriceReq request) {
        return elongPriceService.inspect(found, request);
    }

    @Override
    protected CheckPriceRespDTO availabilityOnlyResp(PlanWithRoom found, ElongHotel hotel, CheckPriceReq request) {
        return elongPriceService.availabilityOnlyResp(request, found.plan());
    }

    @Override
    protected CheckPriceRespDTO validate(PlanWithRoom found, ElongHotel hotel, CheckPriceReq request) {
        return elongPriceService.validate(request, hotel, found);
    }
}
