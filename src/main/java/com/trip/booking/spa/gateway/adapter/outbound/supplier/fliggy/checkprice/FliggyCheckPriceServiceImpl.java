package com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.checkprice;

import com.fasterxml.jackson.databind.JsonNode;
import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.CheckPriceRespDTO;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.CheckPriceReq;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.pricing.FliggyPriceServiceImpl;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.shared.FliggyProperties;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.fliggy.shared.model.FliggyAriResponse;
import com.trip.booking.spa.gateway.application.checkprice.AbstractCheckPriceFlow;
import com.trip.booking.spa.gateway.application.checkprice.LiveStock;
import com.trip.booking.spa.gateway.application.checkprice.ResolveCandidate;
import com.trip.booking.spa.gateway.application.checkprice.ResolveProperties;
import com.trip.booking.spa.gateway.domain.supplier.SupplierSourceEnum;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

/**
 * 飞猪验价能力入口。bean 名必须是 {@code fliggyCheckPriceSyncService}
 * （SupplierSourceEnum.FLIGGY.desc + Capability.CHECK_PRICE 后缀），否则路由不到。
 * 流程（现取→找票→换票→分档→验价）在模板；供应商侧的读法在 {@link FliggyPriceServiceImpl}，
 * 查价与验价对分态的口径必须同源。
 */
@Service("fliggyCheckPriceSyncService")
public class FliggyCheckPriceServiceImpl extends AbstractCheckPriceFlow<FliggyAriResponse, JsonNode> {

    @Resource
    private FliggyPriceServiceImpl fliggyPriceService;
    @Resource
    private FliggyProperties properties;

    @Override
    protected SupplierSourceEnum supplier() {
        return SupplierSourceEnum.FLIGGY;
    }

    @Override
    protected ResolveProperties resolveProperties() {
        return properties;
    }

    @Override
    protected CheckPriceRespDTO precondition(CheckPriceReq request) {
        return fliggyPriceService.precondition();
    }

    @Override
    protected LiveStock<FliggyAriResponse> fetchLiveStock(CheckPriceReq request, String salesEnvironment) {
        return fliggyPriceService.fetchLiveStock(request);
    }

    @Override
    protected JsonNode findByToken(FliggyAriResponse ari, CheckPriceReq request) {
        return FliggyPriceServiceImpl.findByRateKey(ari.rates(), request.getSProductId());
    }

    @Override
    protected List<ResolveCandidate<JsonNode>> resolveCandidates(FliggyAriResponse ari, CheckPriceReq request) {
        return fliggyPriceService.resolveCandidates(ari, request);
    }

    @Override
    protected String tokenOf(JsonNode rate) {
        JsonNode key = rate.get("rate_key");
        return key == null ? null : key.asText();
    }

    @Override
    protected CheckPriceRespDTO availabilityOnlyResp(JsonNode rate, FliggyAriResponse ari, CheckPriceReq request) {
        return fliggyPriceService.availabilityOnlyResp(request, rate);
    }

    @Override
    protected CheckPriceRespDTO validate(JsonNode rate, FliggyAriResponse ari, CheckPriceReq request) {
        return fliggyPriceService.validate(request, rate, ari);
    }
}
