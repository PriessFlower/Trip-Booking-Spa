package com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.checkprice;

import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.CheckPriceRespDTO;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.CheckPriceReq;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.pricing.ExpediaPriceServiceImpl;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.shared.ExpediaRapidProperties;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.shared.model.response.QueryPriceResponse;
import com.trip.booking.spa.gateway.application.checkprice.AbstractCheckPriceFlow;
import com.trip.booking.spa.gateway.application.checkprice.LiveStock;
import com.trip.booking.spa.gateway.application.checkprice.ResolveCandidate;
import com.trip.booking.spa.gateway.application.checkprice.ResolveProperties;
import com.trip.booking.spa.gateway.domain.supplier.SupplierSourceEnum;
import com.trip.booking.spa.platform.observability.RecordLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

/**
 * Expedia 验价能力入口。bean 名必须是 {@code expediaCheckPriceSyncService}。
 * 流程在模板；供应商侧的读法在 {@link ExpediaPriceServiceImpl}。
 */
@Slf4j
@Service("expediaCheckPriceSyncService")
public class ExpediaCheckPriceServiceImpl extends AbstractCheckPriceFlow<QueryPriceResponse, QueryPriceResponse.Rates> {

    @Resource
    private ExpediaPriceServiceImpl expediaPriceService;
    @Resource
    private ExpediaRapidProperties rapidProperties;

    @Resource(name = "redisRecordLogServiceImpl")
    RecordLogService redisRecordLogServiceImpl;

    @Override
    protected SupplierSourceEnum supplier() {
        return SupplierSourceEnum.EXPEDIA;
    }

    @Override
    protected ResolveProperties resolveProperties() {
        return rapidProperties;
    }

    @Override
    protected List<String> salesEnvironments(CheckPriceReq request) {
        return expediaPriceService.salesEnvironments(request);
    }

    @Override
    protected LiveStock<QueryPriceResponse> fetchLiveStock(CheckPriceReq request, String salesEnvironment) {
        return expediaPriceService.fetchLiveStock(request, salesEnvironment);
    }

    @Override
    protected QueryPriceResponse.Rates findByToken(QueryPriceResponse data, CheckPriceReq request) {
        return expediaPriceService.findRate(data, request.getSProductId());
    }

    @Override
    protected List<ResolveCandidate<QueryPriceResponse.Rates>> resolveCandidates(QueryPriceResponse data, CheckPriceReq request) {
        return expediaPriceService.resolveCandidates(data, request);
    }

    @Override
    protected String tokenOf(QueryPriceResponse.Rates rate) {
        return rate.getId();
    }

    @Override
    protected CheckPriceRespDTO inspect(QueryPriceResponse.Rates rate, QueryPriceResponse data, CheckPriceReq request) {
        return expediaPriceService.inspect(rate, request);
    }

    /**
     * 登记在册的欠账（{@code CheckPriceFlowArchRulesTest.KNOWN_GAP}）：Expedia 尚未接入渠道曝光核价
     * （cursor 的 {@code spaGateway.checkprice} 只有 elong,fliggy），此档暂按完整验价处理。
     * 接入曝光层前必须实现真正的仅现货应答并从清单删除，否则会与飞猪 2026-09-02 同款超时。
     */
    @Override
    protected CheckPriceRespDTO availabilityOnlyResp(QueryPriceResponse.Rates rate, QueryPriceResponse data, CheckPriceReq request) {
        log.info("expedia验价：曝光档尚未实现仅现货应答，按完整验价处理,sHotelId={},sProductId={}",
                request.getSHotelId(), request.getSProductId());
        return validate(rate, data, request);
    }

    @Override
    protected CheckPriceRespDTO validate(QueryPriceResponse.Rates rate, QueryPriceResponse data, CheckPriceReq request) {
        return expediaPriceService.validate(request, rate);
    }
}
