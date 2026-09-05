package com.trip.booking.spa.gateway.application.checkprice;

import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.CheckPriceRespDTO;
import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.ProductRespDTO;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.CheckPriceReq;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.PriceReq;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.Supplier;
import com.trip.booking.spa.gateway.adapter.outbound.state.pricecache.PriceCacheService;
import com.trip.booking.spa.gateway.domain.booking.CheckPriceOutcome;
import com.trip.booking.spa.gateway.domain.booking.VerifyLevel;
import com.trip.booking.spa.gateway.domain.product.ResolveGate;
import com.trip.booking.spa.gateway.domain.supplier.SupplierSourceEnum;
import com.trip.booking.spa.platform.observability.MetricNames;
import com.trip.booking.spa.platform.observability.MetricTags;
import com.trip.booking.spa.platform.observability.Monitor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 验价流程模板：现取 → 找票 → 换票 → 分档 → 验价。流程归模板，供应商只填钩子。
 *
 * <p>为什么流程不能留给各家自己写（R-3.6）：{@link ResolveGate} 一直是共用的，但
 * 「令牌死了先按 productKey 换票再判 RATE_DEAD」这一步此前是每家实现里的一行——
 * 艺龙写了、Expedia 抄了、飞猪没抄，CI 全绿，生产 44% 验价 RATE_DEAD 而酒店明明有货
 * （2026-09-05 实测 8/18）。同款：分档截断（AVAILABILITY 不打 validate）三家里曾只有
 * 艺龙兑现。凡是「新接一家必须记得写」的步骤，都该长在骨架上而不是各家的记性里。
 *
 * <p>骨架固定的两条规则：
 * <ul>
 *   <li>{@link VerifyLevel#AVAILABILITY}：找到票即回「有货」，<b>不打 validate、不签句柄</b></li>
 *   <li>{@link VerifyLevel#BOOKABLE}：找到票后<b>必经</b> {@link #validate}，由它给可订与句柄</li>
 * </ul>
 *
 * @param <S> 现货：适配层现取一趟拿回的整店响应
 * @param <C> 票：现货里的一条报价
 */
@Slf4j
public abstract class AbstractCheckPriceFlow<S, C> extends AbstractCheckPriceSyncSupportService<CheckPriceRespDTO> {

    @Resource
    private PriceCacheService priceCacheService;

    protected abstract SupplierSourceEnum supplier();

    protected abstract ResolveProperties resolveProperties();

    /** 调供应商前的自检（凭证是否配置等）。返回非 null 即以之为终态；默认无 */
    protected CheckPriceRespDTO precondition(CheckPriceReq request) {
        return null;
    }

    /**
     * 要依次找票的售卖环境。默认一个（null）；仅当前一个环境<b>确证 RATE_DEAD</b> 才试下一个——
     * 不确定或已售罄时再查一遍既救不回也会掩盖成因（Expedia 零售→打包的口径）。
     */
    protected List<String> salesEnvironments(CheckPriceReq request) {
        return Collections.singletonList(null);
    }

    /** 现取现验（R-3.1）：重打一次现货接口。验价即刷等副作用也在这里 */
    protected abstract LiveStock<S> fetchLiveStock(CheckPriceReq request, String salesEnvironment);

    /** 按上游回传的令牌（sProductId）精确找票；找不到返回 null */
    protected abstract C findByToken(S stock, CheckPriceReq request);

    /**
     * 换票候选：现货里每条在售、可成交的报价，附<b>与查价同口径</b>派生的 productKey 与上游口径总价。
     * 只收 productKey 与请求相等的（硬门 R-3.2 由键相等保证）。
     */
    protected abstract List<ResolveCandidate<C>> resolveCandidates(S stock, CheckPriceReq request);

    /** 票的令牌，只用于日志 */
    protected abstract String tokenOf(C candidate);

    /**
     * 找到票之后、分档之前的供应商自检（停售、缺验价凭据、床型不可选……）。
     * 返回非 null 即以之为终态；默认通过。
     */
    protected CheckPriceRespDTO inspect(C candidate, S stock, CheckPriceReq request) {
        return null;
    }

    /** 曝光档：只答「还在售」。必须回 AVAILABLE 且不签句柄——模板对 BOOKABLE 的句柄自洽检查在父类 */
    protected abstract CheckPriceRespDTO availabilityOnlyResp(C candidate, S stock, CheckPriceReq request);

    /** 下单前档：真打供应商 validate，给出 BOOKABLE（带句柄）或确定的失败态 */
    protected abstract CheckPriceRespDTO validate(C candidate, S stock, CheckPriceReq request);

    @Override
    public final CheckPriceRespDTO doCheckPrice(CheckPriceReq request) {
        CheckPriceRespDTO rejected = precondition(request);
        if (rejected != null) {
            return recorded(rejected);
        }
        CheckPriceRespDTO last = null;
        for (String salesEnvironment : salesEnvironments(request)) {
            last = attempt(request, salesEnvironment);
            if (last.getOutcome() != CheckPriceOutcome.RATE_DEAD) {
                break;
            }
        }
        return recorded(last);
    }

    @Override
    public final CheckPriceRespDTO checkPriceRespConvert(CheckPriceRespDTO dto) {
        return dto;
    }

    private CheckPriceRespDTO attempt(CheckPriceReq request, String salesEnvironment) {
        LiveStock<S> live = fetchLiveStock(request, salesEnvironment);
        if (live.isTerminal()) {
            return live.terminal();
        }
        S stock = live.stock();
        C found = findByToken(stock, request);
        if (found == null) {
            found = resolve(stock, request);
        }
        if (found == null) {
            log.info("验价：所点报价已不在现货且未能换票,supplier={},sHotelId={},sProductId={},productKey={},salesEnvironment={}",
                    supplier().getDesc(), request.getSHotelId(), request.getSProductId(),
                    request.getProductKey(), salesEnvironment);
            return CheckPriceRespDTO.builder().outcome(CheckPriceOutcome.RATE_DEAD)
                    .message("该产品已不在供应商当前报价中，请重新查价后再选择").build();
        }
        CheckPriceRespDTO rejected = inspect(found, stock, request);
        if (rejected != null) {
            return rejected;
        }
        if (request.getVerifyLevel() == VerifyLevel.AVAILABILITY) {
            return availabilityOnlyResp(found, stock, request);
        }
        return validate(found, stock, request);
    }

    /**
     * 令牌死后按 productKey 在现货里换等价新票（resolve ②，docs/product-identity.md §3）。
     * 未换到的成因必须可区分（§6.2.2）：无键、闸口关、无基准、无等价票、超容差。
     */
    private C resolve(S stock, CheckPriceReq request) {
        String supplier = supplier().getDesc();
        if (StringUtils.isBlank(request.getProductKey())) {
            log.info("验价：上游未携 productKey，无法换票,supplier={},sHotelId={},sProductId={}",
                    supplier, request.getSHotelId(), request.getSProductId());
            return resolveMissed(MetricNames.RESOLVE_NO_PRODUCT_KEY);
        }
        ResolveProperties properties = resolveProperties();
        if (properties == null || !properties.isResolveEnabled()) {
            // §3.8.4：上游明确请求了换票（带 productKey）而被闸口拒绝，必须可检索
            log.info("闸口 supplier.{}.resolve-enabled 关闭，拒绝按 productKey 自动换票,sHotelId={},sProductId={}",
                    supplier, request.getSHotelId(), request.getSProductId());
            return resolveMissed(MetricNames.RESOLVE_GATE_CLOSED);
        }
        List<ResolveCandidate<C>> equivalents = resolveCandidates(stock, request);
        if (equivalents == null) {
            equivalents = new ArrayList<>();
        }
        // 容差基准：上游给了就用上游的；没给则反查本网关刷价时写入的原价（调用方未必持有价格）
        Integer baseline = request.getSeenPrice() != null ? request.getSeenPrice() : lookupTotalPriceFromCache(request);
        if (baseline == null) {
            log.info("验价：无容差基准价（上游未携且缓存反查不到），不自动换票,supplier={},sHotelId={},sProductId={}",
                    supplier, request.getSHotelId(), request.getSProductId());
            return resolveMissed(MetricNames.RESOLVE_NO_BASELINE);
        }
        Optional<ResolveCandidate<C>> chosen = ResolveGate.pickCheapestWithinTolerance(equivalents,
                ResolveCandidate::priceCents, baseline, properties.getResolvePriceTolerance(),
                properties.getResolvePriceCapCents());
        if (chosen.isPresent()) {
            log.info("验价：令牌已死，按productKey换票成功,supplier={},原sProductId={},新令牌={},新价={}分,展示价={}分",
                    supplier, request.getSProductId(), tokenOf(chosen.get().candidate()),
                    chosen.get().priceCents(), baseline);
            Monitor.recordOne(MetricNames.CHECK_PRICE_RESOLVE,
                    MetricTags.outcomeOf(supplier(), MetricNames.RESOLVE_SWAPPED));
            return chosen.get().candidate();
        }
        if (equivalents.isEmpty()) {
            log.info("验价：resolve 未命中——现货中无同卖法等价报价,supplier={},sHotelId={},sProductId={},productKey={}",
                    supplier, request.getSHotelId(), request.getSProductId(), request.getProductKey());
            return resolveMissed(MetricNames.RESOLVE_NO_EQUIVALENT);
        }
        log.info("验价：存在等价报价但超出容差，拒绝自动换票,supplier={},sProductId={},展示价={}分,候选最低={}分",
                supplier, request.getSProductId(), baseline,
                equivalents.stream().mapToInt(ResolveCandidate::priceCents).min().orElse(-1));
        return resolveMissed(MetricNames.RESOLVE_OVER_TOLERANCE);
    }

    private C resolveMissed(String reason) {
        Monitor.recordOne(MetricNames.CHECK_PRICE_RESOLVE, MetricTags.outcomeOf(supplier(), reason));
        return null;
    }

    /**
     * 容差基准的反查：走与出价<b>完全相同</b>的 {@link PriceCacheService#getPrice} 路径、
     * 按客人的查询区间、按 productKey 限定。客人看的是按其区间累加的多晚价，拿刷价快照里
     * 的单晚价当基准会小一个量级（2026-08-19）；缓存字段是 productKey 不是报价码，
     * 按报价码找恒 miss（2026-08-20）。查不到返回 null——无基准则不换票。
     */
    Integer lookupTotalPriceFromCache(CheckPriceReq request) {
        try {
            PriceReq priceReq = PriceReq.builder()
                    .checkIn(request.getCheckIn()).checkout(request.getCheckOut())
                    .roomNum(request.getRoomNum())
                    .adultNum(request.getAdultCount()).childNum(request.getChildNum())
                    .childAges(request.getChildAges() == null ? new ArrayList<>() : request.getChildAges())
                    .build();
            Supplier supplier = Supplier.builder()
                    .supplierId(supplier().getCode())
                    .sHotelId(request.getSHotelId())
                    .build();
            List<ProductRespDTO> products = priceCacheService.getPrice(priceReq, supplier, request.getProductKey());
            if (products == null || products.isEmpty()) {
                return null;
            }
            Integer total = products.get(0).getTotalPrice();
            return total != null && total > 0 ? total : null;
        } catch (Exception e) {
            log.warn("验价：总价反查失败,supplier={},sHotelId={},sProductId={},err={}",
                    supplier().getDesc(), request.getSHotelId(), request.getSProductId(), e.toString());
            return null;
        }
    }

    private CheckPriceRespDTO recorded(CheckPriceRespDTO resp) {
        CheckPriceOutcome outcome = resp == null ? CheckPriceOutcome.INDETERMINATE : resp.getOutcome();
        String tag = outcome == null ? "indeterminate" : outcome.name().toLowerCase(Locale.ROOT);
        Monitor.recordOne(MetricNames.CHECK_PRICE_OUTCOME, MetricTags.outcomeOf(supplier(), tag));
        return resp;
    }
}
