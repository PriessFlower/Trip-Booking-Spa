package com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.pricing;

import com.trip.booking.spa.platform.ratelimit.CallPurpose;
import com.fasterxml.jackson.databind.JsonNode;
import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.CancelPolicy;
import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.CheckPriceRespDTO;
import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.Meal;
import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.PriceInfo;
import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.ProductInfo;
import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.ProductRespDTO;
import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.Room;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.CheckPriceReq;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.PriceReq;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.Supplier;
import com.trip.booking.spa.gateway.adapter.outbound.state.catalog.ElongQueryPriceTaskMapper;
import com.trip.booking.spa.gateway.adapter.outbound.state.offer.OfferStore;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.content.ElongCatalogService;
import com.trip.booking.spa.gateway.adapter.outbound.state.pricecache.PriceCacheService;
import com.trip.booking.spa.gateway.application.pricing.PricingResult;
import com.trip.booking.spa.gateway.domain.booking.PricingOutcome;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.checkprice.client.DataValidateAccess;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.pricing.client.HotelDetailAccess;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared.ElongOfferCredentials;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared.ElongProductKeyDeriver;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared.ElongProperties;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared.ElongRestCall;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared.model.request.ElongDataValidateRequest;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared.model.request.ElongHotelDetailRequest;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared.model.request.ElongRequestEnvelope;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared.model.response.ElongDataValidateResponse;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared.model.response.ElongHotelDetailResponse;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared.model.response.ElongNightlyRate;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.elong.shared.model.response.ElongRatePlan;
import com.trip.booking.spa.gateway.domain.booking.CheckPriceOutcome;
import com.trip.booking.spa.gateway.domain.booking.VerifyLevel;
import com.trip.booking.spa.gateway.domain.product.Occupancy;
import com.trip.booking.spa.gateway.domain.product.ProductIdentity;
import com.trip.booking.spa.gateway.domain.product.ResolveGate;
import com.trip.booking.spa.gateway.domain.supplier.SupplierSourceEnum;
import com.trip.booking.spa.platform.http.asynchttp.ResponseResult;
import com.trip.booking.spa.platform.redis.RedisUtils;
import com.trip.booking.spa.platform.observability.DropReason;
import com.trip.booking.spa.platform.observability.FunnelStage;
import com.trip.booking.spa.platform.observability.MetricNames;
import com.trip.booking.spa.platform.observability.MetricTags;
import com.trip.booking.spa.platform.observability.Monitor;
import com.trip.booking.spa.platform.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 艺龙查价/验价协议逻辑。第一家按移植标准（docs/product-identity.md §7 阶段4）
 * 生在新管线上的 cursor 供应商：申报（SupplierIdentityProfile.ELONG）→ 适配层两钩子
 * （{@link ElongProductKeyDeriver}）→ productKey/resolve/OfferStore 全复用。
 *
 * <p><b>现取现验（R-3.1）</b>：验价第一步永远是重打一次 hotel.detail 现取新马甲与
 * 报价码，绝不复用任何早前会话的凭证——cursor 复用验价缓存里的死马甲，实测隔时重放
 * 45/47 全灭（H001144），是其失败率的头号病灶。
 *
 * <p><b>逐店查询（移植风险⑤）</b>：hotel.detail 混批下部分酒店返回 Code=0 且
 * Rooms=[] 的假空，会被误当真实无房。本实现一次只查一店。
 */
@Slf4j
@Service
public class ElongPriceServiceImpl implements ElongPriceService {

    private static final String METHOD_HOTEL_DETAIL = "hotel.detail";

    private static final String METHOD_DATA_VALIDATE = "hotel.data.validate";

    /** 验价通过的 ResultCode 原文 */
    private static final String RESULT_CODE_OK = "OK";




    private static final Map<String, Object> ELONG_TAG =
            Map.of(MetricTags.SUPPLIER, SupplierSourceEnum.ELONG.name());

    @Resource
    private ElongProperties properties;

    /** 规范化与键派生的唯一权威（查价组装与 resolve 匹配共用，键与门同源） */
    @Resource
    private ElongProductKeyDeriver productKeyDeriver;
    @Resource
    private RedisUtils redisUtils;
    /** 批次4 反馈环:验价事件升档任务行(直调 mapper,避免与 CPS service 循环依赖)。 */
    @Resource
    private ElongQueryPriceTaskMapper elongQueryPriceTaskMapper;

    @Resource
    private OfferStore offerStore;

    /** 刷价落缓存用；与 Expedia 共用同一实现，键结构 price:hotelId:date 与供应商无关 */
    @Resource
    private PriceCacheService priceCacheService;

    @Resource
    private ElongCatalogService elongCatalogService;

    /**
     * 查价并落缓存（刷价任务的唯一入口）。查价逻辑复用 {@link #queryPrices}，
     * 只多一步写缓存——刷价与实时查价必须同源，否则 productKey/退改/餐食会分叉。
     *
     * <p>空结果（该店当日无在售）也照常落缓存：那是"确实没有"的事实，
     * 让缓存如实反映，比留着上一轮的陈价对外报要好（宁可少卖，不可卖错，R-1.6）。
     */
    @Override
    public List<ProductRespDTO> queryPricesCache(PriceReq request, Supplier supplier) {
        // 后台刷价这条路：没人在等，限流上阻塞排队而不是失败（F-5.1 失败不动缓存）
        PricingResult result = queryPrices(request, supplier, CallPurpose.REFRESH);
        if (result.outcome() == PricingOutcome.INDETERMINATE) {
            // 调用失败与"无在售"必须分开（F-5.1）：没问出结果时不动缓存，
            // 避免一次网络抖动清空在售价
            return null;
        }
        List<ProductRespDTO> products = result.products();
        priceCacheService.productToCache(products, request, supplier);
        // 建档(R-2.6):稳定事实落库,与写缓存同一处、同一份数据,不额外调供应商。
        // 开关默认关;失败不打断刷价(服务内部已吞异常)
        elongCatalogService.upsert(products);
        return products;
    }

    @Override
    public PricingResult queryPrices(PriceReq request, Supplier supplier, CallPurpose purpose) {
        if (!properties.isConfigured()) {
            log.error("艺龙查价：凭证未配置（ELONG_USER/ELONG_APP_KEY/ELONG_SECRET），无法调用,sHotelId={}",
                    supplier.getSHotelId());
            return PricingResult.indeterminate();
        }
        List<String> occupancies = Occupancy.perRoom(request.getRoomNum(), request.getAdultNum(),
                request.getChildNum(), request.getChildAges());
        request.setOccupancies(occupancies);

        ResponseResult<ElongHotelDetailResponse> result = queryHotelDetail(supplier.getSHotelId(), request.getCheckIn(),
                request.getCheckout(), request.getRoomNum(), request.getAdultNum(), request.getChildNum(), request.getChildAges(),
                purpose);
        if (result == null || result.getData() == null) {
            log.warn("艺龙查价：调用未取得结果,sHotelId={},checkIn={}", supplier.getSHotelId(), request.getCheckIn());
            return PricingResult.indeterminate();
        }
        return toPricingResult(result.getData(), request, supplier.getSHotelId());
    }

    /**
     * 现货响应 → 分态+产品。查价与验价即刷回写共用这一段——两条路对「无货 / 未能确认 /
     * 在售」的口径必须同源，回写另起口径会把 F-5.1（失败不动缓存）或僵尸价清理（B7）弄丢一头。
     */
    PricingResult toPricingResult(ElongHotelDetailResponse data, PriceReq request, String sHotelId) {
        if (!data.isSucc()) {
            // 错误码透传不归并（移植风险④）：码义未核实的一律原样落日志。
            // 码义未核实就不能断言「确实无房」，故落未能确认
            log.warn("艺龙查价：供应商返回业务错误,sHotelId={},code={}", sHotelId, data.getCode());
            return PricingResult.indeterminate();
        }
        if (data.isEmptyResult()) {
            log.info("艺龙查价：该店当日无在售产品,sHotelId={},checkIn={}", sHotelId, request.getCheckIn());
            return PricingResult.noInventory();
        }
        ElongHotelDetailResponse.ElongHotel hotel = data.getResult().getHotels().get(0);
        if (countPlans(hotel) == 0) {
            // 供应商把酒店回来了、但一条报价都没给 —— 这是「确定无货」，不是「没问出来」。
            // isEmptyResult() 只看 Hotels 是否为空，盖不住这一种（2026-08-20 生产实测：
            // 一轮 900 行里有 134 个酒店-日期是这个形态）。误判成 INDETERMINATE 的代价是
            // 缓存不被清（F-5.1 失败不动缓存），已下架的酒店会留着陈价继续对外报，
            // 直到 TTL 过期 —— 正是 B7 说的僵尸价。
            log.info("艺龙查价：该店当日无任何报价,sHotelId={},checkIn={}", sHotelId, request.getCheckIn());
            return PricingResult.noInventory();
        }
        List<ProductRespDTO> products = convertPriceResp(hotel, request);
        if (products.isEmpty()) {
            // 供应商给了产品、但被我们三道过滤全部丢掉（停售/零库存、缺会话凭据、无每日价，
            // 分类计数见 convertPriceResp 的日志）。这里<b>不能</b>说 NO_INVENTORY：
            // 只有「缺凭据」「无每日价」那两类是我方原因，房其实还在，说成无房会让上游
            // 据此劝退旅客。要升级成确定态，得先把三类跳过原因分开统计（待做）
            log.info("艺龙查价：供应商给了报价但被我方过滤全丢，按未能确认回报,sHotelId={},checkIn={}",
                    sHotelId, request.getCheckIn());
            return PricingResult.indeterminate();
        }
        return PricingResult.available(products);
    }

    /** 供应商这一店回了多少条报价（过滤之前）。0 = 供应商没给货，属确定无货 */
    private static int countPlans(ElongHotelDetailResponse.ElongHotel hotel) {
        int n = 0;
        for (ElongHotelDetailResponse.ElongRoom room : emptyIfNull(hotel.getRooms())) {
            n += emptyIfNull(room.getRatePlans()).size();
        }
        return n;
    }

    private List<ProductRespDTO> convertPriceResp(ElongHotelDetailResponse.ElongHotel hotel, PriceReq request) {
        List<ProductRespDTO> products = new ArrayList<>();
        int totalPlans = 0;
        int skippedNotOnSale = 0;
        int skippedNoCredentials = 0;
        int skippedNoPrice = 0;
        String occupancy = request.getOccupancies().get(0);
        for (ElongHotelDetailResponse.ElongRoom room : emptyIfNull(hotel.getRooms())) {
            for (ElongRatePlan plan : emptyIfNull(room.getRatePlans())) {
                totalPlans++;
                if (!isOnSale(plan)) {
                    skippedNotOnSale++;
                    continue;
                }
                if (!hasSessionCredentials(plan)) {
                    skippedNoCredentials++;
                    continue;
                }
                List<ElongDataValidateRequest.DayPrice> dayPrices = buildDayPrices(plan.getNightlyRates());
                if (dayPrices == null) {
                    skippedNoPrice++;
                    continue;
                }
                products.add(convertPlan(hotel.getHotelId(), room, plan, dayPrices, occupancy, request));
            }
        }
        // 非常态走向必须可观测（§6.2.1）：跳过的每一类都有落点，排障时能看出报价去哪了。
        // 日志答「这一家这一天」，指标答「整体丢了多少」（O-1.3：算出来的计数不得只落日志）
        log.info("艺龙查价：转换完成,hotelId={},checkIn={},产品总数={},在售出报={},跳过_停售或无库存={},跳过_缺会话凭据={},跳过_缺每日价={}",
                hotel.getHotelId(), request.getCheckIn(), totalPlans, products.size(),
                skippedNotOnSale, skippedNoCredentials, skippedNoPrice);
        countConvertDropped(DropReason.NOT_ON_SALE, skippedNotOnSale);
        countConvertDropped(DropReason.NO_SESSION_CREDENTIALS, skippedNoCredentials);
        countConvertDropped(DropReason.NO_DAY_PRICE, skippedNoPrice);
        return products;
    }

    private static void countConvertDropped(DropReason reason, int count) {
        if (count > 0) {
            Monitor.recordMany(MetricNames.QUOTE_DROPPED,
                    MetricTags.dropped(SupplierSourceEnum.ELONG, FunnelStage.CONVERT, reason), count);
        }
    }

    private ProductRespDTO convertPlan(String hotelId, ElongHotelDetailResponse.ElongRoom room, ElongRatePlan plan,
                                       List<ElongDataValidateRequest.DayPrice> dayPrices, String occupancy, PriceReq request) {
        Meal meal = productKeyDeriver.convertMeal(plan);
        List<CancelPolicy> cancelPolicy = productKeyDeriver.convertCancelPolicy(request.getCheckIn(), plan.getPrepayResult());
        int displayTotalCents = sumCents(dayPrices);
        // 身份与成分一次算出（R-2.8）：建档照抄 identity，不得再判一遍
        ProductIdentity identity = productKeyDeriver.deriveIdentity(
                hotelId, plan.getRoomTypeId(), meal, cancelPolicy, occupancy);
        ProductRespDTO product = ProductRespDTO.builder()
                .hotelId(hotelId)
                // 报价标识=GoodsUniqId（会话级易腐，申报见 SupplierIdentityProfile.ELONG）；
                // 身份=productKey，二者永不同字段
                .productId(plan.getGoodsUniqId())
                .productKey(identity.productKey())
                .identity(identity)
                .supplierId(SupplierSourceEnum.ELONG.getCode())
                .room(Room.builder().roomId(plan.getRoomTypeId()).roomName(room.getName()).build())
                // inventory 原样透出艺龙的 CurrentAlloment（房量限额，0/999/9999=不限，非剩余房量）。
                // 上游若按 inventory<=0 过滤，会误杀"不限"的产品——语义见 ElongRatePlan 字段注释
                .productInfo(ProductInfo.builder().inventory(plan.getCurrentAlloment()).productStatus(1)
                        .productName(StringUtils.isNotBlank(plan.getRatePlanName()) ? plan.getRatePlanName() : room.getName()).build())
                .currencyType(plan.getCurrencyCode())
                // totalPrice=含税总额（Σ Rate）；roomTotalPrice=税前房费（Σ MinRate）。
                // 二者之差即税费，与 priceInfos 逐日的 taxes 之和一致（国际对接指南 ①②）
                .totalPrice(displayTotalCents)
                .roomTotalPrice(sumRoomCents(dayPrices))
                .priceInfos(buildPriceInfos(dayPrices))
                .meal(meal)
                .cancelPolicy(cancelPolicy)
                .maxOccupancy(plan.getAdultOccupancyPerRoom() != null ? plan.getAdultOccupancyPerRoom() : request.getAdultNum())
                .build();
        product.setTotalTaxes(0);
        return product;
    }

    @Override
    public CheckPriceRespDTO checkPrices(CheckPriceReq request) {
        if (!properties.isConfigured()) {
            log.error("艺龙验价：凭证未配置（ELONG_USER/ELONG_APP_KEY/ELONG_SECRET），无法调用,sHotelId={}", request.getSHotelId());
            return outcome(CheckPriceOutcome.INDETERMINATE, "艺龙凭证未配置，未能确认该产品是否可订");
        }
        // 批次4 反馈环(F-6):验价=真实需求信号,该酒店升档 24h 被高频档跟刷(fire-and-forget)
        markHotelHot(request.getSHotelId());
        String occupancy = Occupancy.canonical(request.getAdultCount(), request.getChildNum(), request.getChildAges());

        // 现取现验（R-3.1）：重打一次 hotel.detail 取本会话的新马甲与新报价码
        ResponseResult<ElongHotelDetailResponse> result = queryHotelDetail(request.getSHotelId(), request.getCheckIn(),
                request.getCheckOut(), request.getRoomNum(), request.getAdultCount(), request.getChildNum(), request.getChildAges(),
                CallPurpose.CHECK_PRICE);
        if (result == null || result.getData() == null) {
            log.warn("艺龙验价：现货查询未取得结果,sHotelId={},sProductId={}", request.getSHotelId(), request.getSProductId());
            return outcome(CheckPriceOutcome.INDETERMINATE, "现货查询未取得结果，未能确认该产品是否可订，请稍后重试");
        }
        ElongHotelDetailResponse data = result.getData();
        // 验价即刷（F-6 的即时半边）：手里这份现货就是最新报价，验完即弃等于白白留着
        // 缓存里的陈价继续对外报（实证 2026-08-22 河内 Daewoo：市场价已涨 4.3%，
        // 缓存价换不到票，每次点击都 RATE_DEAD）。异步回写，不占验价预算。
        freshPricesToCacheAsync(request, data);
        if (!data.isSucc()) {
            log.warn("艺龙验价：现货查询返回业务错误,sHotelId={},sProductId={},code={}",
                    request.getSHotelId(), request.getSProductId(), data.getCode());
            return outcome(CheckPriceOutcome.INDETERMINATE, "现货查询失败(" + data.errorCode() + ")，未能确认该产品是否可订");
        }
        if (data.isEmptyResult()) {
            // 逐店查询下的空 Rooms 是该店当日确无在售（混批假空已由逐店纪律排除）
            log.info("艺龙验价：该店当日无在售产品,sHotelId={},sProductId={}", request.getSHotelId(), request.getSProductId());
            return outcome(CheckPriceOutcome.RATE_DEAD, "该酒店当日已无在售产品，请重新查价");
        }

        ElongHotelDetailResponse.ElongHotel hotel = data.getResult().getHotels().get(0);
        PlanWithRoom found = findPlan(hotel, request.getSProductId());
        if (found != null && Boolean.FALSE.equals(found.plan().getStatus())) {
            log.info("艺龙验价：所点产品已停售,sHotelId={},sProductId={}", request.getSHotelId(), request.getSProductId());
            return outcome(CheckPriceOutcome.RATE_DEAD, "该产品已停售，请重新查价后再选择");
        }
        // 此处原有一段「CurrentAlloment <= 0 → SOLD_OUT」已删除：该字段是"房量限额"不是"剩余房量"，
        // 0/999/9999 均表示不限，官方对每种口径都写明「最少有 1 间可以预定」，而我方一次只订 1 间。
        // 判成售罄的方向是少卖。详见 ElongRatePlan#getCurrentAlloment() 字段注释。
        // 真正的售罄由 validate 的 ResultCode=Inventory 给出——那是供应商此刻的确定答复（见下方分态）。
        if (found == null) {
            // 报价码已不在现货（会话级轮换是常态）：先按 productKey 换等价新票（resolve ②），
            // 换不到才是确定性 RATE_DEAD。
            //
            // 原先此处还有一段「productKey 反查自愈」：上游只回传 productId 时，去详情缓存
            // product:{hotelId}:{productId} 里把 key 找回来。0853d11 把详情键改成按 productKey
            // 存之后，这条反查在结构上就不可能命中了——productId→productKey 需要全店扫描，
            // 而给易腐码另建一套反向索引，正是那次改名要消除的内存增长。已删除，不留静默空转。
            //
            // 代价：上游不携 productKey 时 resolve 无检索键，一律走 RATE_DEAD 正门。
            // 补法在上游——查价响应里 productKey 与 productId 是分开两个字段发出去的，
            // 验价时原样带回来即可。上游已回传（2026-08-24 生产实测 21.5h 内 14 次验价
            // 13 次携带，换票触发 9 次、仅 1 次换不到），故本段已是活路径而非空转。
            found = tryResolveByProductKey(hotel, request, occupancy);
        }
        if (found == null) {
            log.info("艺龙验价：所点报价码已不在现货且未能换票,sHotelId={},sProductId={},productKey={}",
                    request.getSHotelId(), request.getSProductId(), request.getProductKey());
            return outcome(CheckPriceOutcome.RATE_DEAD, "该产品已不在供应商当前报价中，请重新查价后再选择");
        }

        ElongRatePlan plan = found.plan();
        if (!hasSessionCredentials(plan) || StringUtils.isAnyBlank(plan.getHotelCode(), plan.getSupplierId(),
                plan.getSubSupplierId(), plan.getShopperProductId())) {
            // 产品在售却缺验价必需的七项凭据，属响应自相矛盾：不能说可订，也没证据说不可订
            log.error("艺龙验价：现货产品缺验价凭据,sHotelId={},goodsUniqId={},ratePlanId={}",
                    request.getSHotelId(), plan.getGoodsUniqId(), plan.getRatePlanId());
            return outcome(CheckPriceOutcome.INDETERMINATE, "供应商响应缺少验价凭据，未能确认该产品是否可订");
        }
        if (request.getVerifyLevel() == VerifyLevel.AVAILABILITY) {
            // 渠道验价档：到此为止，不打 validate（供应商预算 1200ms，完整验价约 4.6s 塞不进）。
            // 只回"有货"，不回句柄——现货里的马甲与报价码是会话级易腐凭证，到真下单必已过期
            return availabilityOnlyResp(request, plan);
        }
        List<ElongDataValidateRequest.DayPrice> dayPrices = buildDayPrices(plan.getNightlyRates());
        if (dayPrices == null) {
            log.error("艺龙验价：现货产品缺每日价,sHotelId={},goodsUniqId={}", request.getSHotelId(), plan.getGoodsUniqId());
            return outcome(CheckPriceOutcome.INDETERMINATE, "供应商响应缺少每日价，未能确认该产品是否可订");
        }
        return validate(request, hotel.getHotelId(), plan, dayPrices);
    }

    /** 二段：以本会话凭据打 hotel.data.validate，并把结果归入确定的分态 */
    private CheckPriceRespDTO validate(CheckPriceReq request, String hotelId, ElongRatePlan plan,
                                       List<ElongDataValidateRequest.DayPrice> dayPrices) {
        ElongDataValidateRequest validateRequest = buildValidateRequest(request, hotelId, plan, dayPrices);
        BigDecimal declaredTotalYuan = validateRequest.getDeclaredTotal();
        String dataJson = JsonUtils.writeObject2Json(new ElongRequestEnvelope(properties.getVersion(), validateRequest));
        ElongDataValidateResponse data = callValidate(dataJson);
        if (data == null) {
            log.warn("艺龙验价：validate 调用未取得结果,sHotelId={},goodsUniqId={}", hotelId, plan.getGoodsUniqId());
            return outcome(CheckPriceOutcome.INDETERMINATE, "验价调用未取得结果，未能确认该产品是否可订，请稍后重试");
        }
        // H001189 自纠正：艺龙在拒绝的同一份响应里回传了它自己认可的 MinRate，用它重打一次。
        // 详见 alignMinRateToSupplier 的注释。Price（Σ Rate）一个字不动，故申报总价不受影响
        if (isPerDayPriceMismatch(data)) {
            Monitor.recordOne(MetricNames.VALIDATE_DAY_PRICE_ALIGN,
                    MetricTags.outcomeOf(SupplierSourceEnum.ELONG, MetricNames.ALIGN_MISMATCH));
            List<ElongDataValidateRequest.DayPrice> aligned = alignMinRateToSupplier(dayPrices, data);
            if (aligned == null) {
                log.warn("艺龙验价：H001189 但响应未回传可用的逐日 MinRate，无法自纠正,sHotelId={},goodsUniqId={}",
                        hotelId, plan.getGoodsUniqId());
            } else {
                log.info("艺龙验价：H001189 按艺龙回传的 MinRate 重试,sHotelId={},goodsUniqId={},原={},纠正后={}",
                        hotelId, plan.getGoodsUniqId(), minRatesOf(dayPrices), minRatesOf(aligned));
                validateRequest.setDayPriceList(aligned);
                ElongDataValidateResponse retried = callValidate(
                        JsonUtils.writeObject2Json(new ElongRequestEnvelope(properties.getVersion(), validateRequest)));
                if (retried != null) {
                    data = retried;
                    dayPrices = aligned;
                    Monitor.recordOne(MetricNames.VALIDATE_DAY_PRICE_ALIGN,
                            MetricTags.outcomeOf(SupplierSourceEnum.ELONG,
                                    isPerDayPriceMismatch(retried)
                                            ? MetricNames.ALIGN_RETRY_FAILED : MetricNames.ALIGN_RETRY_OK));
                }
            }
        }
        if (!data.isSucc()) {
            return classifyValidateError(request, plan, data);
        }
        // ResultCode 语义（官方 hotel.data.validate 文档，2026-08-15 核对）：
        // OK=正常可预订 / Product=产品无效或关房 / Inventory=房量不够 / Rate=价格不符。
        // 三个失败态对本次所点报价都是确定性结果，重试同参数不会改变
        String resultCode = StringUtils.trimToEmpty(data.getResult() == null ? null : data.getResult().getResultCode());
        if (RESULT_CODE_OK.equalsIgnoreCase(resultCode)) {
            return buildBookableResp(request, hotelId, plan, dayPrices, declaredTotalYuan, data);
        }
        if ("Inventory".equalsIgnoreCase(resultCode)) {
            log.info("艺龙验价：供应商明确房量不够,sHotelId={},goodsUniqId={},resultCode={}",
                    hotelId, plan.getGoodsUniqId(), resultCode);
            return outcome(CheckPriceOutcome.SOLD_OUT, "该产品已售罄");
        }
        if ("Product".equalsIgnoreCase(resultCode) || "Rate".equalsIgnoreCase(resultCode)) {
            // 产品无效/关房、价格不符：该票已死，上游重新查价即可拿到换代后的报价
            log.info("艺龙验价：产品级死态,sHotelId={},goodsUniqId={},resultCode={},errorMessage={}",
                    hotelId, plan.getGoodsUniqId(), resultCode,
                    data.getResult() == null ? null : data.getResult().getErrorMessage());
            return outcome(CheckPriceOutcome.RATE_DEAD,
                    "该产品已不可订(ResultCode=" + resultCode + ")，请重新查价后再选择");
        }
        // 官方表外的取值只透传不归并（移植风险④）
        log.warn("艺龙验价：ResultCode 表外取值，按不确定处理,sHotelId={},goodsUniqId={},resultCode={},errorMessage={}",
                hotelId, plan.getGoodsUniqId(), resultCode,
                data.getResult() == null ? null : data.getResult().getErrorMessage());
        return outcome(CheckPriceOutcome.INDETERMINATE,
                "验价未通过(ResultCode=" + resultCode + ")，未能确认该产品是否可订");
    }

    /**
     * 验价业务错误码 → 三态。纪律：只有确证不因重试而改变的才判确定态；产品级死码必须
     * RATE_DEAD，绝不折叠进"不确定"——cursor 把 H001083 兜成可订导致建单暴死丢真单。
     */
    private CheckPriceRespDTO classifyValidateError(CheckPriceReq request, ElongRatePlan plan,
                                                    ElongDataValidateResponse data) {
        String errorCode = StringUtils.trimToEmpty(data.errorCode());
        String full = data.getCode() + "|" + (data.getResult() == null ? "" : StringUtils.trimToEmpty(data.getResult().getErrorMessage()));
        if (errorCode.startsWith("H001083")) {
            // 内层 7015=餐食变化、7010=国际产品不可订：对本次所点报价而言都意味着该票已死，
            // 上游重新查价即可拿到换代后的报价。区分二者只为排障方向（§6.2.2）
            String innerCause = full.contains("7015") ? "7015餐食变化" : (full.contains("7010") ? "7010产品不可订" : "未携内层码");
            log.info("艺龙验价：产品级死码,sHotelId={},goodsUniqId={},errorCode={},内层={}",
                    request.getSHotelId(), plan.getGoodsUniqId(), errorCode, innerCause);
            return outcome(CheckPriceOutcome.RATE_DEAD, "该产品已不可订(" + errorCode + ")，请重新查价后再选择");
        }
        if (errorCode.startsWith("H001144")) {
            // 马甲过期本可现取现验救回，但本次马甲就是刚取的——即刻过期属链路异常，重试可能成功
            log.warn("艺龙验价：本会话刚取的马甲被报过期,sHotelId={},goodsUniqId={},errorCode={}",
                    request.getSHotelId(), plan.getGoodsUniqId(), errorCode);
            return outcome(CheckPriceOutcome.INDETERMINATE, "验价凭证异常(H001144)，未能确认该产品是否可订，请稍后重试");
        }
        if (errorCode.startsWith("H001197") || errorCode.startsWith("H001188")) {
            // 缺马甲/每日价字段错，均属我方请求组装缺陷，需人工介入
            log.error("艺龙验价：请求组装缺陷被供应商拒绝,sHotelId={},goodsUniqId={},errorCode={},response={}",
                    request.getSHotelId(), plan.getGoodsUniqId(), errorCode, result(data));
            return outcome(CheckPriceOutcome.INDETERMINATE, "验价请求异常(" + errorCode + ")，未能确认该产品是否可订");
        }
        if (errorCode.startsWith("H001189")) {
            // 走到这里说明自纠正没救回来，两种成因：①响应未回传可用的逐日 MinRate，无从纠正；
            // ②按艺龙自己回传的 MinRate 重试后仍被拒（2026-08-21 实测约 3%，成因未明，
            // 已随复现报告提报艺龙）。二者靠指标区分：mismatch 命中数 − retry_ok − retry_failed。
            //
            // 取 INDETERMINATE 而非 RATE_DEAD：该报价本身好得很（Rate 两个接口一致、库存也在），
            // 拦下它的是 MinRate 口径差，不是票死了。说成 RATE_DEAD 会让上游告诉客人"这份报价
            // 没了、请重新查价"，那是把不知道的事说成确定的（R-1.6）。而 detail 的 MinRate 实测
            // 会随时间漂移（同一产品数秒内 82.58→82.53），上游稍后重新查价确有救回可能，
            // 故"可重试"这个语义是站得住的。
            log.error("艺龙验价：每日价口径不符且自纠正未生效,sHotelId={},goodsUniqId={},errorCode={},response={}",
                    request.getSHotelId(), plan.getGoodsUniqId(), errorCode, result(data));
            return outcome(CheckPriceOutcome.INDETERMINATE,
                    "验价未通过(" + errorCode + " 每日价口径不符)，未能确认该产品是否可订，请稍后重试");
        }
        if (errorCode.startsWith("H001084")) {
            // 官方错误码表（2026-08-15 核对）：总价计算错误——我方提交的 TotalPrice 与
            // 供应商现价不符，即该票价格已换代，重试同参数必再失败
            log.info("艺龙验价：总价与现价不符,sHotelId={},goodsUniqId={},errorCode={}",
                    request.getSHotelId(), plan.getGoodsUniqId(), errorCode);
            return outcome(CheckPriceOutcome.RATE_DEAD, "该产品价格已变化(H001084)，请重新查价后再选择");
        }
        // 其余码义未核实的一律透传不归并（移植风险④）。H001189 已于 2026-08-21 查清并单列在上，
        // 不再落到这里——这行注释此前把它举为例子，已过时
        log.warn("艺龙验价：未核实错误码，按不确定处理,sHotelId={},goodsUniqId={},code={}",
                request.getSHotelId(), plan.getGoodsUniqId(), data.getCode());
        return outcome(CheckPriceOutcome.INDETERMINATE, "验价未通过(" + errorCode + ")，未能确认该产品是否可订");
    }

    /**
     * 验价通过 → 签发句柄并回报 BOOKABLE。
     *
     * <p>价格以验价响应 interValidateInfo.ratePlanInfo.RateNightlyRateList 为准
     * （供应商验后价）；未下发或与住期对不齐时回落本会话查价合计。签发不成即不可报可订
     * （报得出价却下不了单，比验价失败更糟）。
     */
    private CheckPriceRespDTO buildBookableResp(CheckPriceReq request, String hotelId, ElongRatePlan plan,
                                                List<ElongDataValidateRequest.DayPrice> dayPrices,
                                                BigDecimal declaredTotalYuan, ElongDataValidateResponse data) {
        int rooms = roomsOf(request);
        // salePrice 是整单口径（上游拿它与渠道整单上限比价）：验后价是单间逐晚合计，须乘间数；
        // 回落值 declaredTotalYuan 在组装时已乘过
        int salePriceCents = validatedPriceCents(data, dayPrices.size())
                .map(perRoomCents -> perRoomCents * rooms)
                .orElse(declaredTotalYuan.multiply(BigDecimal.valueOf(100)).intValue());
        Map<String, String> credentials = new HashMap<>();
        credentials.put(ElongOfferCredentials.HOTEL_ID, hotelId);
        credentials.put(ElongOfferCredentials.HOTEL_CODE, plan.getHotelCode());
        credentials.put(ElongOfferCredentials.ROOM_TYPE_ID, plan.getRoomTypeId());
        credentials.put(ElongOfferCredentials.RATE_PLAN_ID, String.valueOf(plan.getRatePlanId()));
        credentials.put(ElongOfferCredentials.GOODS_UNIQ_ID, plan.getGoodsUniqId());
        credentials.put(ElongOfferCredentials.LITTLE_MAJIA_ID, plan.getLittleMajiaId());
        credentials.put(ElongOfferCredentials.SUPPLIER_ID, plan.getSupplierId());
        credentials.put(ElongOfferCredentials.SUB_SUPPLIER_ID, plan.getSubSupplierId());
        credentials.put(ElongOfferCredentials.SHOPPER_PRODUCT_ID, plan.getShopperProductId());
        credentials.put(ElongOfferCredentials.DECLARED_TOTAL, declaredTotalYuan.toPlainString());
        // 间数与申报总价绑定入句柄：TotalPrice=Σ每日价×间数，下单侧必须用同一间数回放
        credentials.put(ElongOfferCredentials.ROOM_NUM, String.valueOf(rooms));
        credentials.put(ElongOfferCredentials.DAY_PRICE_LIST, JsonUtils.writeObject2Json(dayPrices));
        credentials.put(ElongOfferCredentials.CHECK_IN, request.getCheckIn());
        credentials.put(ElongOfferCredentials.CHECK_OUT, request.getCheckOut());
        credentials.put(ElongOfferCredentials.ADULT_COUNT,
                String.valueOf(request.getAdultCount() == null ? 1 : request.getAdultCount()));
        String offerId = offerStore.issue(SupplierSourceEnum.ELONG.getCode(), credentials);
        if (StringUtils.isBlank(offerId)) {
            return outcome(CheckPriceOutcome.INDETERMINATE, "报价句柄签发失败，请稍后重试");
        }
        // 退改与每日价以验价时点为准（查价与验价之间条款可能已变，下单认的是这一份）；
        // 解析不出则回落本会话查价口径，两者皆无即空——不猜（R-5.4）
        List<CancelPolicy> cancelPolicy = productKeyDeriver.convertValidatedCancelPolicy(
                request.getCheckIn(), validatedCancelPolicyList(data));
        if (cancelPolicy.isEmpty()) {
            cancelPolicy = productKeyDeriver.convertCancelPolicy(request.getCheckIn(), plan.getPrepayResult());
            log.info("艺龙验价：验价响应退改不可解析，回落查价条款,sHotelId={},goodsUniqId={},回落后条数={}",
                    hotelId, plan.getGoodsUniqId(), cancelPolicy.size());
        }
        List<PriceInfo> priceInfos = validatedPriceInfos(data).orElseGet(() -> buildPriceInfos(dayPrices));
        log.info("艺龙验价：通过并签发句柄,sHotelId={},goodsUniqId={},salePrice={}分,offerId={},退改条数={},每日价条数={}",
                hotelId, plan.getGoodsUniqId(), salePriceCents, offerId, cancelPolicy.size(), priceInfos.size());
        return CheckPriceRespDTO.builder()
                .outcome(CheckPriceOutcome.BOOKABLE)
                .offerId(offerId)
                .offerTtlSeconds(offerStore.ttlSecondsOf(SupplierSourceEnum.ELONG.getCode()))
                .salePrice(salePriceCents)
                .subPrice(salePriceCents)
                .remainRoomNum(restInventory(data))
                .cancelPolicy(cancelPolicy)
                .priceInfos(priceInfos)
                .build();
    }

    /**
     * 渠道验价档的应答：只报"有货"，不报"可订"。
     *
     * <p><b>三条纪律</b>：
     * <ul>
     *   <li><b>offerId 恒为 null</b>——没打验价就没有任何"此刻可成单"的证据，签句柄等于把
     *       不确定说成确定（R-1.6）；且现货里的马甲到真下单时必已过期，签出去只会诱导上游
     *       拿必死的凭据建单</li>
     *   <li><b>退改取 detail 的 {@code PrepayResult}</b>——艺龙【国际酒店】国际对接指南明文
     *       「取消政策取 PrepayResult」。解析不出即空列表，不猜（R-5.4）</li>
     *   <li><b>remainRoomNum 取 {@code CurrentAlloment}</b>——2026-08-21 实测它与验价回传的
     *       {@code RestInventoryCount} 相等。注意该字段是"房量限额"，0/999/9999 表示不限，
     *       语义见 {@link ElongRatePlan#getCurrentAlloment()}</li>
     * </ul>
     *
     * <p>价格与税费仍是 detail 口径（{@code Rate} 与 {@code Rate − MinRate}）；它只用于展示，
     * 真正对账的数在下单前那一档由验价响应给出。
     */
    private CheckPriceRespDTO availabilityOnlyResp(CheckPriceReq request, ElongRatePlan plan) {
        List<ElongDataValidateRequest.DayPrice> dayPrices = buildDayPrices(plan.getNightlyRates());
        if (dayPrices == null) {
            log.info("艺龙验价(仅现货)：所点产品缺每日价,sHotelId={},goodsUniqId={}",
                    request.getSHotelId(), plan.getGoodsUniqId());
            return outcome(CheckPriceOutcome.INDETERMINATE, "供应商未给出每日价，未能确认该产品");
        }
        List<PriceInfo> priceInfos = buildPriceInfos(dayPrices);
        List<CancelPolicy> cancelPolicy =
                productKeyDeriver.convertCancelPolicy(request.getCheckIn(), plan.getPrepayResult());
        // salePrice 整单口径：每日价是单间的，多间须乘间数（与 BOOKABLE 档一致）
        int totalCents = sumCents(dayPrices) * roomsOf(request);
        log.info("艺龙验价(仅现货)：有货但未验证可订性,sHotelId={},goodsUniqId={},价格={}分,退改条数={},房量限额={}",
                request.getSHotelId(), plan.getGoodsUniqId(), totalCents, cancelPolicy.size(),
                plan.getCurrentAlloment());
        return CheckPriceRespDTO.builder()
                .outcome(CheckPriceOutcome.AVAILABLE)
                .salePrice(totalCents)
                .subPrice(totalCents)
                .remainRoomNum(plan.getCurrentAlloment())
                .cancelPolicy(cancelPolicy)
                .priceInfos(priceInfos)
                .build();
    }

    /** 发一次 validate；调用失败或响应体缺失返回 null（网络类失败由调用方落 INDETERMINATE） */
    private ElongDataValidateResponse callValidate(String dataJson) {
        ResponseResult<ElongDataValidateResponse> result = new DataValidateAccess(properties)
                .access(new ElongRestCall(METHOD_DATA_VALIDATE, dataJson), CallPurpose.CHECK_PRICE);
        return result == null ? null : result.getData();
    }

    /**
     * 是否为「每日价传参异常」（{@code H001189}）——可自纠正的那一类价格不符。
     *
     * <p>与 {@code H001188} 分开：后者是我方请求组装缺陷（缺字段、总价与逐日之和不等），
     * 属人工介入范畴，不重试。
     */
    private static boolean isPerDayPriceMismatch(ElongDataValidateResponse data) {
        return StringUtils.trimToEmpty(data.errorCode()).startsWith("H001189");
    }

    /**
     * 把逐日 {@code MinRate} 换成艺龙在本次响应里回传的值，{@code Price} 一个字不动。
     *
     * <p><b>为什么要这么做</b>（2026-08-21 生产实打约 150 次坐实）：{@code hotel.detail} 与
     * {@code hotel.data.validate} 对同一产品的 {@code MinRate} 会给出不同值（detail 偏高
     * 0.01~0.33 元，而 {@code Rate} 两边完全一致）。判据是<b>申报税费 {@code Price − MinRate}
     * 须不低于艺龙自己算的税费</b>，detail 的 MinRate 偏高就让申报税费偏小，正好压在边界下沿，
     * 实测 14%~39% 被判 {@code H001189}。
     *
     * <p>偏差随时间漂移（同一产品同一入参，几分钟前恒拒、几分钟后恒过），故固定容差治不住；
     * 而艺龙<b>在拒绝的同一份响应里就回传了它认可的 MinRate</b>，用它重试是唯一权威解法。
     *
     * <p><b>只在 detail 的值过不去时才换</b>：首次通过就沿用 detail 那份——它已被艺龙接受，
     * 是唯一有实证的组合，下单原样replay必然同样通过。
     *
     * <p><b>不动 {@code Price} 是关键</b>：申报总价（我方应付金额）由它决定，{@code MinRate}
     * 只影响税费拆分。曾考虑"给 Price 加 0.05 元边际"，那是错的——要多付钱，且实测不可靠。
     *
     * @return 纠正后的逐日价；响应未回传、日期对不齐、或值与原来完全相同（重试无意义）时返回 null
     */
    private static List<ElongDataValidateRequest.DayPrice> alignMinRateToSupplier(
            List<ElongDataValidateRequest.DayPrice> dayPrices, ElongDataValidateResponse data) {
        JsonNode list = validatedNightlyRateList(data);
        if (list == null || !list.isArray() || list.size() != dayPrices.size()) {
            return null;
        }
        // 按 Date 匹配而非按下标：数组顺序是艺龙的实现细节，不是契约
        Map<String, BigDecimal> byDate = new HashMap<>();
        for (JsonNode night : list) {
            String date = StringUtils.trimToEmpty(night.path("Date").asText());
            if (date.length() < 10 || !night.hasNonNull("MinRate")) {
                return null;
            }
            byDate.put(date.substring(0, 10), night.get("MinRate").decimalValue());
        }
        List<ElongDataValidateRequest.DayPrice> aligned = new ArrayList<>();
        boolean changed = false;
        for (ElongDataValidateRequest.DayPrice day : dayPrices) {
            BigDecimal supplierMinRate = byDate.get(day.getDate());
            if (supplierMinRate == null || supplierMinRate.signum() <= 0) {
                return null;
            }
            if (supplierMinRate.compareTo(day.getMinRate()) != 0) {
                changed = true;
            }
            aligned.add(ElongDataValidateRequest.DayPrice.builder()
                    .date(day.getDate()).price(day.getPrice()).minRate(supplierMinRate).build());
        }
        return changed ? aligned : null;
    }

    /** 验价响应里的逐日价（{@code Rate}/{@code MinRate}）；缺任一层级返回 null */
    private static JsonNode validatedNightlyRateList(ElongDataValidateResponse data) {
        JsonNode info = data.getResult() == null ? null : data.getResult().getInterValidateInfo();
        return info == null ? null : info.path("ratePlanInfo").path("RateNightlyRateList");
    }

    private static String minRatesOf(List<ElongDataValidateRequest.DayPrice> dayPrices) {
        List<String> out = new ArrayList<>();
        for (ElongDataValidateRequest.DayPrice day : dayPrices) {
            out.add(day.getDate() + "=" + day.getMinRate().toPlainString());
        }
        return String.join(",", out);
    }

    /** 验价响应里的退改分段；缺任一层级返回 null，交调用方回落 */
    private static JsonNode validatedCancelPolicyList(ElongDataValidateResponse data) {
        JsonNode info = data.getResult() == null ? null : data.getResult().getInterValidateInfo();
        return info == null ? null : info.path("ratePlanInfo").path("CancelPolicyList");
    }

    /**
     * 验后每日价：{@code RateNightlyRateList[{Rate,MinRate,Date}]} → 契约每日价（分）。
     * Date 为带偏移的 ISO 时刻，截取日期部分与查价侧 {@code yyyy-MM-dd} 对齐。
     */
    private static Optional<List<PriceInfo>> validatedPriceInfos(ElongDataValidateResponse data) {
        JsonNode info = data.getResult() == null ? null : data.getResult().getInterValidateInfo();
        JsonNode list = info == null ? null : info.path("ratePlanInfo").path("RateNightlyRateList");
        if (list == null || !list.isArray() || list.isEmpty()) {
            return Optional.empty();
        }
        List<PriceInfo> priceInfos = new ArrayList<>();
        for (JsonNode night : list) {
            if (!night.hasNonNull("Rate") || !night.hasNonNull("Date")) {
                return Optional.empty();
            }
            int cents = night.get("Rate").decimalValue().multiply(BigDecimal.valueOf(100)).intValue();
            String date = StringUtils.substringBefore(night.get("Date").asText(), "T");
            priceInfos.add(PriceInfo.builder().date(date).price(cents).roomPrice(cents).taxes(0).build());
        }
        return Optional.of(priceInfos);
    }

    /**
     * 令牌已死时按 productKey 在本会话现货中找等价新票（resolve ②，docs/product-identity.md §3）。
     *
     * <p>三个前置缺一即放弃（走 RATE_DEAD 正门）：开关 supplier.elong.resolve-enabled
     * 开启（默认关）、上游携带 productKey、上游携带展示价 totalPrice（容差门基准，R-3.3）。
     * 硬门（R-3.2）由键相等保证：对每条现货按与查价<b>完全相同的口径</b>重新派生再比对。
     * 匹配在已取回的现货响应上进行，不追加供应商调用（R-3.4）。
     */
    /**
     * 批次4 反馈环(F-6):真实验价事件 → 该酒店全部日期行临时升档 24h,被高频档
     * 借入跟刷,让刷价额度自动流向有真实需求的酒店。fire-and-forget:任何异常只记
     * 日志,绝不影响验价主流程。刷价内部走 queryPrices 不经此处,无自激。
     */
    /**
     * 验价即刷回写线程：单线程 + 有界队列 + 满则弃。回写是验价的副产品、尽力而为——
     * 宁可丢一次回写（下轮刷价会补），不许排队积压拖住任何东西。守护线程随进程退出。
     */
    private static final java.util.concurrent.ExecutorService FRESH_PRICES_POOL =
            new java.util.concurrent.ThreadPoolExecutor(1, 1, 60L, java.util.concurrent.TimeUnit.SECONDS,
                    new java.util.concurrent.ArrayBlockingQueue<>(64), r -> {
                Thread t = new Thread(r, "elong-fresh-prices");
                t.setDaemon(true);
                return t;
            });

    /**
     * 验价即刷（F-6 即时半边）：把验价现取的这份现货异步回写价格缓存。
     *
     * <ul>
     *   <li><b>零额外供应商调用</b>——数据是验价本来就拉的；</li>
     *   <li><b>口径同源</b>——分态与转换复用 {@link #toPricingResult}，异常价拦截、
     *       TTL 分档、无货落缓存全部继承刷价写路径（{@code productToCache}）；</li>
     *   <li><b>F-5.1 不破</b>——INDETERMINATE（业务错误/全被过滤）不动缓存；</li>
     *   <li><b>占用键随验价走</b>——客人问 2 大 1 小就回写 2-x 键，长尾占用按需成盘
     *       （刷价任务只铺 1 人档）。</li>
     * </ul>
     * 任何失败只落日志，绝不影响验价主流程。
     */
    void freshPricesToCacheAsync(CheckPriceReq request, ElongHotelDetailResponse data) {
        try {
            FRESH_PRICES_POOL.execute(() -> freshPricesToCache(request, data));
        } catch (java.util.concurrent.RejectedExecutionException e) {
            log.warn("验价即刷：回写队列满，本次丢弃(下轮刷价会补) sHotelId={}", request.getSHotelId());
        }
    }

    /** 回写本体（同步，供测试直接驱动）。 */
    void freshPricesToCache(CheckPriceReq request, ElongHotelDetailResponse data) {
        try {
            PriceReq priceReq = PriceReq.builder()
                    .checkIn(request.getCheckIn())
                    .checkout(request.getCheckOut())
                    .roomNum(request.getRoomNum() == null ? 1 : request.getRoomNum())
                    .adultNum(request.getAdultCount())
                    .childNum(request.getChildNum() == null ? 0 : request.getChildNum())
                    .childAges(request.getChildAges() == null ? new ArrayList<>() : request.getChildAges())
                    .guestType(0)
                    .build();
            priceReq.setOccupancies(Occupancy.perRoom(priceReq.getRoomNum(), priceReq.getAdultNum(),
                    priceReq.getChildNum(), priceReq.getChildAges()));
            PricingResult classified = toPricingResult(data, priceReq, request.getSHotelId());
            if (classified.outcome() == PricingOutcome.INDETERMINATE) {
                return; // F-5.1：没问出结果不动缓存
            }
            Supplier supplier = Supplier.builder().sHotelId(request.getSHotelId()).build();
            priceCacheService.productToCache(classified.products(), priceReq, supplier);
            log.info("验价即刷：现货已回写缓存,sHotelId={},occupancy={},checkIn={},产品={}",
                    request.getSHotelId(), priceReq.getOccupancies().get(0), request.getCheckIn(),
                    classified.products().size());
        } catch (Exception e) {
            log.warn("验价即刷：回写失败不影响验价,sHotelId={},err={}", request.getSHotelId(), e.toString());
        }
    }

    void markHotelHot(String shId) {
        try {
            int rows = elongQueryPriceTaskMapper.upgradeByShId(shId);
            if (rows > 0) {
                log.info("elong 反馈环:验价事件升档 shId={},影响 {} 行(24h)", shId, rows);
            }
        } catch (Exception e) {
            log.warn("elong 反馈环:升档失败不影响验价 shId={},err={}", shId, e.toString());
        }
    }

    /**
     * 反查该报价在<b>本次入离日期区间</b>的总价（分），作为 resolve 换票的容差基准。
     * 用于上游未携展示价的场景（见 {@link CheckPriceReq#getSeenPrice()}）。
     *
     * <p><b>必须走 {@link PriceCacheService#getPrice} 这条与出价完全相同的路径</b>，
     * 不能读产品详情缓存里的 totalPrice 字段：后者是<b>刷价那一次</b>写入的快照
     * （任务行区间通常 1 晚），而客人看到的价是出价时按其查询区间<b>逐日累加</b>
     * 出来的。客人查 3 晚、基准取 1 晚，容差判断会整体失真——那不是精度问题，
     * 是量级错误（2026-08-19 Owner 质疑时发现）。
     *
     * <p><b>限定用 productKey 而不是 sProductId</b>：缓存字段自 0853d11 起是 productKey
     * （艺龙报价码会话级轮换，用它当字段会让多晚查询的交集恒为空）。本方法只在
     * {@link #tryResolveByProductKey} 内被调用，那里已保证 productKey 非空。
     *
     * <p>查不到返回 null——无基准则不换票，不猜。
     */
    Integer lookupTotalPriceFromCache(CheckPriceReq request) {
        try {
            PriceReq priceReq = PriceReq.builder()
                    .checkIn(request.getCheckIn()).checkout(request.getCheckOut())
                    .roomNum(request.getRoomNum())
                    .adultNum(request.getAdultCount()).childNum(request.getChildNum())
                    .childAges(request.getChildAges() == null ? new ArrayList<>() : request.getChildAges())
                    .guestType(0)
                    .build();
            Supplier supplier = Supplier.builder()
                    .supplierId(SupplierSourceEnum.ELONG.getCode())
                    .sHotelId(request.getSHotelId())
                    .build();
            List<ProductRespDTO> products = priceCacheService.getPrice(priceReq, supplier, request.getProductKey());
            if (products == null || products.isEmpty()) {
                return null;
            }
            Integer total = products.get(0).getTotalPrice();
            return total != null && total > 0 ? total : null;
        } catch (Exception e) {
            log.warn("艺龙验价：总价反查失败,sHotelId={},sProductId={},err={}",
                    request.getSHotelId(), request.getSProductId(), e.toString());
            return null;
        }
    }

    PlanWithRoom tryResolveByProductKey(ElongHotelDetailResponse.ElongHotel hotel, CheckPriceReq request, String occupancy) {
        if (StringUtils.isBlank(request.getProductKey())) {
            return null;
        }
        if (!properties.isResolveEnabled()) {
            // §3.8.4：上游明确请求了换票（带 productKey）而被闸口拒绝，必须可检索
            log.info("闸口 supplier.elong.resolve-enabled 关闭，拒绝按 productKey 自动换票,sHotelId={},sProductId={}",
                    request.getSHotelId(), request.getSProductId());
            return null;
        }
        List<ResolveCandidate> equivalents = new ArrayList<>();
        for (ElongHotelDetailResponse.ElongRoom room : emptyIfNull(hotel.getRooms())) {
            for (ElongRatePlan plan : emptyIfNull(room.getRatePlans())) {
                if (!isOnSale(plan) || !hasSessionCredentials(plan)) {
                    continue;
                }
                List<ElongDataValidateRequest.DayPrice> dayPrices = buildDayPrices(plan.getNightlyRates());
                if (dayPrices == null) {
                    continue;
                }
                Meal meal = productKeyDeriver.convertMeal(plan);
                List<CancelPolicy> cancelPolicy = productKeyDeriver.convertCancelPolicy(request.getCheckIn(), plan.getPrepayResult());
                String key = productKeyDeriver.deriveProductKey(hotel.getHotelId(), plan.getRoomTypeId(), meal, cancelPolicy, occupancy);
                if (!request.getProductKey().equals(key)) {
                    continue;
                }
                equivalents.add(new ResolveCandidate(new PlanWithRoom(room, plan), sumCents(dayPrices)));
            }
        }
        // 容差基准：上游给了就用上游的；没给则反查本网关刷价时写入的原价（调用方未必持有价格）
        Integer baseline = request.getSeenPrice() != null
                ? request.getSeenPrice()
                : lookupTotalPriceFromCache(request);
        if (baseline == null) {
            log.info("艺龙验价：无容差基准价（上游未携且缓存反查不到），不自动换票,sHotelId={},sProductId={}",
                    request.getSHotelId(), request.getSProductId());
            return null;
        }
        return ResolveGate.pickCheapestWithinTolerance(equivalents, ResolveCandidate::priceCents,
                        baseline, properties.getResolvePriceTolerance(),
                        properties.getResolvePriceCapCents())
                .map(chosen -> {
                    log.info("艺龙验价：令牌已死，按productKey换票成功,原sProductId={},新goodsUniqId={},新价={}分,展示价={}分",
                            request.getSProductId(), chosen.planWithRoom().plan().getGoodsUniqId(),
                            chosen.priceCents(), baseline);
                    return chosen.planWithRoom();
                })
                .orElseGet(() -> {
                    // 未救回的两种成因必须可区分（§6.2.2）："没等价票"查建档/键口径，"价格不合"查容差参数
                    if (equivalents.isEmpty()) {
                        log.info("艺龙验价：resolve 未命中——现货中无同卖法等价报价,sHotelId={},sProductId={},productKey={}",
                                request.getSHotelId(), request.getSProductId(), request.getProductKey());
                    } else {
                        log.info("艺龙验价：存在等价报价但超出容差，拒绝自动换票,sProductId={},展示价={}分,候选最低={}分",
                                request.getSProductId(), baseline,
                                equivalents.stream().mapToInt(ResolveCandidate::priceCents).min().orElse(-1));
                    }
                    return null;
                });
    }

    /**
     * 逐店 hotel.detail（SaveMajiaId=true 取本会话马甲），查价与验价共用同一起手式。
     *
     * <p>{@code purpose} 必须由调用方给出：这个接口有三路消费方（后台刷价、点订前的现取现验、
     * 上游实时查价），共用一个接口桶而各占一个用途桶，且"等还是走"也按用途分。本方法看不出
     * 自己在为谁服务，不能自行猜。
     */
    private ResponseResult<ElongHotelDetailResponse> queryHotelDetail(String hotelId, String checkIn, String checkOut,
                                                                      Integer roomNum, Integer adultNum,
                                                                      Integer childNum, List<Integer> childAges,
                                                                      CallPurpose purpose) {
        ElongHotelDetailRequest detailRequest = ElongHotelDetailRequest.builder()
                .arrivalDate(checkIn)
                .departureDate(checkOut)
                .hotelIds(hotelId)
                .numberOfAdults(adultNum)
                .numberOfRooms(roomNum)
                .childAges(childNum != null && childNum > 0 && CollectionUtils.isNotEmpty(childAges) ? childAges : List.of())
                .build();
        String dataJson = JsonUtils.writeObject2Json(new ElongRequestEnvelope(properties.getVersion(), detailRequest));
        return new HotelDetailAccess(properties).access(new ElongRestCall(METHOD_HOTEL_DETAIL, dataJson), purpose);
    }

    /** 在现货中按报价码（GoodsUniqId）找所点产品；找不到返回 null */
    private PlanWithRoom findPlan(ElongHotelDetailResponse.ElongHotel hotel, String goodsUniqId) {
        for (ElongHotelDetailResponse.ElongRoom room : emptyIfNull(hotel.getRooms())) {
            for (ElongRatePlan plan : emptyIfNull(room.getRatePlans())) {
                if (goodsUniqId != null && goodsUniqId.equals(plan.getGoodsUniqId())) {
                    return new PlanWithRoom(room, plan);
                }
            }
        }
        return null;
    }

    /** 在售判定：总开关开 ∧ 库存>0 ∧ 有房型锚（键成分，缺了无从派生身份） */
    /**
     * 在售判定：只看 {@code Status} 与房型 ID。
     *
     * <p><b>刻意不看 {@code CurrentAlloment}</b>——它是"房量限额"不是"剩余房量"，
     * 0/999/9999 都表示<b>不限</b>；官方对每种口径都写明「最少有 1 间可以预定」，
     * 而我方一次只订 1 间。此前把 {@code <=0} 当作不可订，方向是少卖，详见
     * {@link ElongRatePlan#getCurrentAlloment()} 的字段注释。
     *
     * <p>该字段留给<b>多间预订</b>（{@code NumberOfRooms > 1}）时用，届时须按境内外分口径解读。
     */
    private static boolean isOnSale(ElongRatePlan plan) {
        return !Boolean.FALSE.equals(plan.getStatus())
                && StringUtils.isNotBlank(plan.getRoomTypeId());
    }

    /** 会话凭据（马甲+报价码）齐备才可出报——报出来也验不了的价是给上游埋雷 */
    private static boolean hasSessionCredentials(ElongRatePlan plan) {
        return StringUtils.isNotBlank(plan.getGoodsUniqId()) && StringUtils.isNotBlank(plan.getLittleMajiaId());
    }

    /**
     * 每日价：取 <b>{@code Rate}</b>（含税结算口径）。查价报给上游的总价与 validate 的
     * DayPriceList 同源，故此处一改两头都改——上游看到的价与我方向艺龙申报的价从此是同一个数。
     *
     * <p><b>依据</b>：艺龙【国际酒店】国际对接指南（open.elong.com/faq/detail?plt=2&amp;id=337）
     * 对 hotel.detail 返参的原话——「<b>①价格取Rate；②税费=Rate-MinRate</b>；③取消政策取
     * PrepayResult；④餐食取meals」。同一口径在 hotel.order.create 的 DayPrice 节点被重申：
     * 「对应于NightRate里MinRate，<b>同时Price为NightRate里Rate</b>」，并由 TotalPrice 备注
     * 「<b>国际分销商需要传入 sum(Rate) * 房间数</b>」与 H001188 校验项「DayPrice里的Price之和
     * * 房间数 是否等于TotalPrice」闭合。2026-08-21 艺龙对接人微信答复亦为「对，用 Rate」。
     *
     * <p><b>此前取 {@code Member} 的代价</b>：Member 是会员价（官方定义"可直接显示给客人"的
     * 零售价），实测比 Rate 高 <b>1.4%~10%</b>（837 报价，中位 8.3%）；而结算按申报值走
     * （2026-07 月结单订单 101000106416「艺龙卖价/分销商卖价/结算金额」三列均等于申报值），
     * 即每单多付。加价一律交由下游渠道处理，本层只报供应商事实。
     *
     * <p><b>缺 Rate 不兜底</b>：整条报价作废，不退回 Member。Rate 是国际产品必然下发的字段
     * （官方"仅用于国际及港澳台酒店"），拿不到说明数据异常；退回 Member 等于把"每单多付"
     * 保留成兜底策略。国内酒店无 Rate，接入时须按境内外分流而不是在此加兜底。
     */
    /**
     * 组装 hotel.data.validate 请求。TotalPrice 官方字段名即「多天*多间总价」：
     * DayPriceList 是单间口径，TotalPrice = ΣDayPrice.Price × NumberOfRooms（官方 H001188
     * 排查项原文）。此前漏乘间数，多间验价必被拒（2026-08-23 高德 2 间真单坐实）。
     */
    static ElongDataValidateRequest buildValidateRequest(CheckPriceReq request, String hotelId, ElongRatePlan plan,
                                                         List<ElongDataValidateRequest.DayPrice> dayPrices) {
        int rooms = roomsOf(request);
        return ElongDataValidateRequest.builder()
                .arrivalDate(request.getCheckIn())
                .departureDate(request.getCheckOut())
                // Earliest 取抓包样例常用值；Latest 固定 23:59:59——上游到店时间（如
                // amap.EarlyArrivalTime）不得透传到此，会误伤可订性判定（移植风险⑩）
                .earliestArrivalTime(request.getCheckIn() + " 12:00:00")
                .latestArrivalTime(request.getCheckIn() + " 23:59:59")
                .hotelId(hotelId)
                .hotelCode(plan.getHotelCode())
                .ratePlanId(plan.getRatePlanId())
                .roomTypeID(plan.getRoomTypeId())
                .littleMajiaId(plan.getLittleMajiaId())
                .goodsUniqId(plan.getGoodsUniqId())
                .shopperProductId(plan.getShopperProductId())
                .subSupplierId(plan.getSubSupplierId())
                .supplierId(plan.getSupplierId())
                .declaredTotal(sumYuan(dayPrices).multiply(BigDecimal.valueOf(rooms)))
                .numberOfRooms(rooms)
                .numberOfAdults(request.getAdultCount())
                .childAges(CollectionUtils.isEmpty(request.getChildAges()) ? List.of() : request.getChildAges())
                .dayPriceList(dayPrices)
                .build();
    }

    /** 间数：上游未携或非法一律按 1 间（与下单侧 buildRequest 同规） */
    private static int roomsOf(CheckPriceReq request) {
        return request.getRoomNum() == null || request.getRoomNum() < 1 ? 1 : request.getRoomNum();
    }

    static List<ElongDataValidateRequest.DayPrice> buildDayPrices(List<ElongNightlyRate> nightlyRates) {
        if (CollectionUtils.isEmpty(nightlyRates)) {
            return null;
        }
        List<ElongDataValidateRequest.DayPrice> dayPrices = new ArrayList<>();
        for (ElongNightlyRate nightly : nightlyRates) {
            if (nightly.getRate() == null || nightly.getRate().signum() <= 0
                    || nightly.getMinRate() == null || nightly.getMinRate().signum() <= 0
                    || StringUtils.isBlank(nightly.getDate()) || nightly.getDate().length() < 10) {
                return null;
            }
            dayPrices.add(ElongDataValidateRequest.DayPrice.builder()
                    .date(nightly.getDate().substring(0, 10))
                    .price(nightly.getRate())
                    .minRate(nightly.getMinRate())
                    .build());
        }
        return dayPrices;
    }

    /**
     * 逐日价拆成「含税总额 / 房费 / 税费」三项，口径按国际对接指南：{@code price} 取 Rate、
     * {@code taxes} 取 <b>Rate − MinRate</b>、{@code roomPrice} 取 MinRate（税前房费）。
     *
     * <p>此前 {@code taxes} 恒填 0 且 {@code roomPrice} 与 {@code price} 同值——等于告诉上游
     * "这个价不含税"，而 Rate 官方定义就是<b>含税</b>价。指南把税费算法写明为 Rate−MinRate
     * （那间房即 185.40−161.97=23.43），据此三项自洽：{@code price = roomPrice + taxes}。
     */
    static List<PriceInfo> buildPriceInfos(List<ElongDataValidateRequest.DayPrice> dayPrices) {
        List<PriceInfo> priceInfos = new ArrayList<>();
        for (ElongDataValidateRequest.DayPrice dayPrice : dayPrices) {
            int cents = toCents(dayPrice.getPrice());
            int roomCents = toCents(dayPrice.getMinRate());
            priceInfos.add(PriceInfo.builder().date(dayPrice.getDate())
                    .price(cents).roomPrice(roomCents).taxes(cents - roomCents).build());
        }
        return priceInfos;
    }

    private static int toCents(BigDecimal yuan) {
        return yuan.multiply(BigDecimal.valueOf(100)).intValue();
    }

    /** 税前房费合计（分）：逐日 MinRate 之和，供 roomTotalPrice 用 */
    private static int sumRoomCents(List<ElongDataValidateRequest.DayPrice> dayPrices) {
        int sum = 0;
        for (ElongDataValidateRequest.DayPrice dayPrice : dayPrices) {
            sum += toCents(dayPrice.getMinRate());
        }
        return sum;
    }

    private static BigDecimal sumYuan(List<ElongDataValidateRequest.DayPrice> dayPrices) {
        BigDecimal sum = BigDecimal.ZERO;
        for (ElongDataValidateRequest.DayPrice dayPrice : dayPrices) {
            sum = sum.add(dayPrice.getPrice());
        }
        return sum;
    }

    private static int sumCents(List<ElongDataValidateRequest.DayPrice> dayPrices) {
        return sumYuan(dayPrices).multiply(BigDecimal.valueOf(100)).intValue();
    }

    /** 验后价：interValidateInfo.ratePlanInfo.RateNightlyRateList 逐日 Rate 求和（元→分），与住期对不齐即放弃 */
    private static Optional<Integer> validatedPriceCents(ElongDataValidateResponse data, int nights) {
        JsonNode info = data.getResult() == null ? null : data.getResult().getInterValidateInfo();
        JsonNode list = info == null ? null : info.path("ratePlanInfo").path("RateNightlyRateList");
        if (list == null || !list.isArray() || list.size() != nights) {
            return Optional.empty();
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (JsonNode night : list) {
            if (!night.hasNonNull("Rate")) {
                return Optional.empty();
            }
            sum = sum.add(night.get("Rate").decimalValue());
        }
        return Optional.of(sum.multiply(BigDecimal.valueOf(100)).intValue());
    }

    private static Integer restInventory(ElongDataValidateResponse data) {
        JsonNode info = data.getResult() == null ? null : data.getResult().getInterValidateInfo();
        JsonNode rest = info == null ? null : info.path("ratePlanInfo").path("RestInventoryCount");
        return rest != null && rest.canConvertToInt() ? rest.asInt() : null;
    }

    /** 占用串与 Expedia 同口径：一间房一项，「成人数-儿童年龄,儿童年龄」 */


    private static String result(ElongDataValidateResponse data) {
        return JsonUtils.writeObject2Json(data);
    }

    private static <T> List<T> emptyIfNull(List<T> list) {
        return list == null ? List.of() : list;
    }

    private CheckPriceRespDTO outcome(CheckPriceOutcome outcome, String message) {
        return CheckPriceRespDTO.builder().outcome(outcome).message(message).build();
    }

    /** 现货产品及其所在物理房型（房名展示用） */
    record PlanWithRoom(ElongHotelDetailResponse.ElongRoom room, ElongRatePlan plan) {
    }

    /** resolve 候选：已过硬门（productKey 相等）的现货报价及其上游口径价格（分） */
    private record ResolveCandidate(PlanWithRoom planWithRoom, int priceCents) {
    }
}
