package com.trip.booking.spa.gateway.adapter.outbound.supplier.meituan.checkprice;

import com.trip.booking.spa.gateway.adapter.outbound.supplier.meituan.pricing.MeituanPriceServiceImpl;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.meituan.shared.MeituanProperties;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.meituan.shared.model.MeituanGoods;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.meituan.shared.model.MeituanHotelGoods;
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
 * 美团验价能力入口。bean 名必须是 {@code meituanCheckPriceSyncService}
 * （SupplierSourceEnum.MEITUAN.desc + Capability.CHECK_PRICE 后缀），否则路由不到。
 * 流程（现取→找票→换票→分档→校验）在模板；供应商侧的读法在 {@link MeituanPriceServiceImpl}。
 *
 * <p><b>短周期内对 resolve 的依赖比另几家轻</b>：goodsId 在一个会话内会跨住期复用
 * （见 {@code SupplierIdentityProfile.MEITUAN} 的取证），"旧列表点击"命中原票是常态。
 * 但它按易腐申报——长周期的命中率没有数据，别把这个"轻"当成长期结论。
 */
@Service("meituanCheckPriceSyncService")
public class MeituanCheckPriceServiceImpl extends AbstractCheckPriceFlow<MeituanHotelGoods, MeituanGoods> {

    @Resource
    private MeituanPriceServiceImpl meituanPriceService;

    @Resource
    private MeituanProperties properties;

    @Override
    protected SupplierSourceEnum supplier() {
        return SupplierSourceEnum.MEITUAN;
    }

    @Override
    protected ResolveProperties resolveProperties() {
        return properties;
    }

    @Override
    protected CheckPriceResult precondition(CheckPriceCommand request) {
        return meituanPriceService.precondition(request);
    }

    @Override
    protected LiveStock<MeituanHotelGoods> fetchLiveStock(CheckPriceCommand request, String salesEnvironment) {
        return meituanPriceService.fetchLiveStock(request);
    }

    @Override
    protected MeituanGoods findByToken(MeituanHotelGoods hotel, CheckPriceCommand request) {
        return meituanPriceService.findGoods(hotel, request.supplierProductId());
    }

    @Override
    protected List<ResolveCandidate<MeituanGoods>> resolveCandidates(MeituanHotelGoods hotel, CheckPriceCommand request) {
        return meituanPriceService.resolveCandidates(hotel, request);
    }

    @Override
    protected String tokenOf(MeituanGoods candidate) {
        return String.valueOf(candidate.getGoodsId());
    }

    @Override
    protected CheckPriceResult availabilityOnlyResp(MeituanGoods candidate, MeituanHotelGoods hotel, CheckPriceCommand request) {
        return meituanPriceService.availabilityOnlyResp(request, candidate);
    }

    @Override
    protected CheckPriceResult validate(MeituanGoods candidate, MeituanHotelGoods hotel, CheckPriceCommand request) {
        return meituanPriceService.validate(request, candidate);
    }
}
