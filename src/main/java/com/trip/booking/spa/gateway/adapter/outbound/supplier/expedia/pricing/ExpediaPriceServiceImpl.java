package com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.pricing;

import com.trip.booking.spa.platform.ratelimit.CallPurpose;
import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.BedCheckInfo;
import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.CancelPolicy;
import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.CheckPriceRespDTO;
import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.Meal;
import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.PriceInfo;
import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.ProductInfo;
import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.ProductRespDTO;
import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.Room;
import com.trip.booking.spa.gateway.domain.shared.Money;
import com.trip.booking.spa.gateway.domain.product.RefundType;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.CheckPriceReq;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.PriceReq;
import com.trip.booking.spa.gateway.adapter.inbound.rest.request.Supplier;
import com.trip.booking.spa.platform.http.asynchttp.ResponseResult;
import com.trip.booking.spa.gateway.domain.booking.CheckPriceOutcome;
import com.trip.booking.spa.gateway.domain.supplier.SupplierSourceEnum;
import com.trip.booking.spa.gateway.domain.product.CancelClass;
import com.trip.booking.spa.gateway.domain.product.MealSignature;
import com.trip.booking.spa.gateway.domain.product.ProductKeyFactory;
import com.trip.booking.spa.gateway.domain.product.ProductIdentity;
import com.trip.booking.spa.gateway.adapter.outbound.state.offer.OfferStore;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.checkprice.client.CheckPriceAccess;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.pricing.client.QueryProductAccess;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.shared.model.request.QueryPriceRequest;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.shared.model.response.CheckPriceResponse;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.shared.model.response.QueryPriceResponse;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.shared.ExpediaContractProfile;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.shared.ExpediaProductKeyDeriver;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.shared.ExpediaRapidProperties;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.shared.ExpediaOfferCredentials;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.pricing.ExpediaPriceService;
import com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.shared.ExpediaUtils;
import com.trip.booking.spa.gateway.adapter.outbound.state.pricecache.PriceCacheService;
import com.trip.booking.spa.gateway.application.checkprice.LiveStock;
import com.trip.booking.spa.gateway.application.checkprice.ResolveCandidate;
import com.trip.booking.spa.gateway.application.pricing.PricingResult;
import com.trip.booking.spa.platform.observability.CallStatus;
import com.trip.booking.spa.platform.observability.DropReason;
import com.trip.booking.spa.platform.observability.FunnelStage;
import com.trip.booking.spa.platform.observability.MetricNames;
import com.trip.booking.spa.platform.observability.MetricTags;
import com.trip.booking.spa.platform.observability.Monitor;
import com.trip.booking.spa.platform.redis.DistributedRateLimiter;
import com.trip.booking.spa.platform.util.DateUtil;
import com.trip.booking.spa.platform.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.compress.utils.Lists;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


@Service
@Slf4j
public class ExpediaPriceServiceImpl implements ExpediaPriceService {

    @Value("${expedia.url.host}")
    String host;
    @Value("${expedia.session}")
    String sessionId;
    @Value("${expedia.ownIp}")
    String ownIp;

    /** 合同车道参数的唯一来源；查价起手式与验价链接补参数都经由它，二者从此同源 */
    @Resource
    private ExpediaContractProfile contractProfile;

    @Resource
    private ExpediaRapidProperties rapidProperties;

    /** 规范化与键派生的唯一权威(建档/查价/resolve 三链路共用,详见该类 javadoc) */
    @Resource
    private ExpediaProductKeyDeriver productKeyDeriver;

    /** 报价展示币种：与 EAC 结算币种（CNY）对齐；上游 request.currency 为空时用此默认 */
    private static final String DEFAULT_QUOTE_CURRENCY = "CNY";

    public static final String SALES_ENV_HOTEL_ONLY = "hotel_only";
    public static final String SALES_ENV_HOTEL_PACKAGE = "hotel_package";

    /** Expedia 验价响应中表示满房的状态原文 */
    private static final String STATUS_SOLD_OUT = "sold_out";

    @Resource
    private OfferStore offerStore;

    /**
     * 由验价响应装配对外的验价结果，并为本次报价签发句柄。
     *
     * <p><b>签发不成则本次验价视为失败</b>（返回 null）：报价能报出来却下不了单，
     * 比直接告诉上游验价失败更糟——上游会把这个价展示给旅客，等到下单时才发现不可用。
     */
    private CheckPriceRespDTO buildCheckPriceResp(CheckPriceResponse checkPrice,
                                                  QueryPriceResponse.Occupancy_pricing occupancyPricing) {
        String bookHref = checkPrice.getLinks() == null || checkPrice.getLinks().getBook() == null
                ? null : checkPrice.getLinks().getBook().getHref();
        if (StringUtils.isBlank(bookHref)) {
            // 供应商说可订却没给下单链接，属响应自相矛盾，不可报可订
            log.error("expedia验价通过但未返回下单链接，无法签发报价句柄");
            return outcome(CheckPriceOutcome.INDETERMINATE, "验价响应缺少下单链接，未能确认该产品是否可订");
        }
        String offerId = offerStore.issue(SupplierSourceEnum.EXPEDIA.getCode(),
                Map.of(ExpediaOfferCredentials.BOOK_HREF, bookHref));
        if (StringUtils.isBlank(offerId)) {
            // 句柄签发不成属我方原因，重试可能成功
            return outcome(CheckPriceOutcome.INDETERMINATE, "报价句柄签发失败，请稍后重试");
        }
        int inclusiveCents = Money.toCents(new BigDecimal(occupancyPricing.getTotals().getInclusive()
                .getRequest_currency().getValue()));
        return CheckPriceRespDTO.builder()
                .outcome(CheckPriceOutcome.BOOKABLE)
                .offerId(offerId)
                .offerTtlSeconds(offerStore.ttlSecondsOf(SupplierSourceEnum.EXPEDIA.getCode()))
                .salePrice(inclusiveCents)
                .subPrice(inclusiveCents)
                .currencyType(occupancyPricing.getTotals().getInclusive()
                        .getRequest_currency().getCurrency())
                .brokerage(calcCommissionCents(occupancyPricing))
                .build();
    }

    /**
     * 佣金（分）：取 Expedia 按商务协议预计算的 marketing_fee，未下发则为 0
     */
    private int calcCommissionCents(QueryPriceResponse.Occupancy_pricing occupancyPricing) {
        QueryPriceResponse.Totals totals = occupancyPricing.getTotals();
        if (totals == null || totals.getMarketing_fee() == null
                || totals.getMarketing_fee().getRequest_currency() == null) {
            return 0;
        }
        return Money.toCents(new BigDecimal(totals.getMarketing_fee().getRequest_currency().getValue()));
    }

    @Resource
    private ExpediaUtils expediaUtils;
    @Resource
    private DistributedRateLimiter rateLimiter;

    @Autowired
    private PriceCacheService priceCacheService;

    @Resource
    private com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.content.service.ExpediaCatalogService expediaCatalogService;

    @Override
    public PricingResult queryPrices(PriceReq request, Supplier supplier) {
        ResponseResult<QueryPriceResponse> resultOnly = null;
        ResponseResult<QueryPriceResponse> resultPackage = null;
        QueryPriceResponse.HotelPrice hotelPriceOnly = null;
        QueryPriceResponse.HotelPrice hotelPricePackage = null;

        QueryPriceRequest queryPriceRequest = contractProfile.newRequestBuilder().property_id(supplier.getSHotelId()).checkin(request.getCheckIn()).checkout(request.getCheckout()).currency(StringUtils.isBlank(request.getCurrency()) ? DEFAULT_QUOTE_CURRENCY : request.getCurrency()).sales_environment("hotel_only").build();
        List<String> occupancies = new ArrayList<>();
        for (int i = 0; i < request.getRoomNum(); i++) {
            String childrenList = "";
            if (null != request.getChildNum() && 0 != request.getChildNum() && CollectionUtils.isNotEmpty(request.getChildAges())) {
                for (Integer childAge : request.getChildAges()) {
                    if (StringUtils.isBlank(childrenList)) {
                        childrenList = "-" + childAge;
                    } else {
                        childrenList = childrenList + "," + childAge;
                    }
                }
            }
            occupancies.add(request.getAdultNum() + childrenList);
        }
        queryPriceRequest.setOccupancies(occupancies);
        request.setOccupancies(occupancies);
        if ("en-US".equals(request.getLanguage())) {
            if ("hotel_only".equals(request.getPriceFlag())) {
                //先查询零售价
                queryPriceRequest.setSales_environment("hotel_only");
                resultOnly = new QueryProductAccess(host, "en-US", expediaUtils.generateSign(), ownIp, sessionId, rateLimiter).access(queryPriceRequest, CallPurpose.LIVE);
            } else if ("hotel_package".equals(request.getPriceFlag())) {
                //查询打包价
                queryPriceRequest.setSales_environment("hotel_package");
                resultPackage = new QueryProductAccess(host, "en-US", expediaUtils.generateSign(), ownIp, sessionId, rateLimiter).access(queryPriceRequest, CallPurpose.LIVE);
            } else {
                //先查询零售价
                queryPriceRequest.setSales_environment("hotel_only");
                resultOnly = new QueryProductAccess(host, "en-US", expediaUtils.generateSign(), ownIp, sessionId, rateLimiter).access(queryPriceRequest, CallPurpose.LIVE);
                //查询打包价
                queryPriceRequest.setSales_environment("hotel_package");
                resultPackage = new QueryProductAccess(host, "en-US", expediaUtils.generateSign(), ownIp, sessionId, rateLimiter).access(queryPriceRequest, CallPurpose.LIVE);
            }
        } else {
            if ("hotel_only".equals(request.getPriceFlag())) {
                //先查询零售价
                queryPriceRequest.setSales_environment("hotel_only");
                resultOnly = new QueryProductAccess(host, "zh-CN", expediaUtils.generateSign(), ownIp, sessionId, rateLimiter).access(queryPriceRequest, CallPurpose.LIVE);
            } else if ("hotel_package".equals(request.getPriceFlag())) {
                //查询打包价
                queryPriceRequest.setSales_environment("hotel_package");
                resultPackage = new QueryProductAccess(host, "zh-CN", expediaUtils.generateSign(), ownIp, sessionId, rateLimiter).access(queryPriceRequest, CallPurpose.LIVE);
            } else {
                //先查询零售价
                queryPriceRequest.setSales_environment("hotel_only");
                resultOnly = new QueryProductAccess(host, "zh-CN", expediaUtils.generateSign(), ownIp, sessionId, rateLimiter).access(queryPriceRequest, CallPurpose.LIVE);
                //查询打包价
                queryPriceRequest.setSales_environment("hotel_package");
                resultPackage = new QueryProductAccess(host, "zh-CN", expediaUtils.generateSign(), ownIp, sessionId, rateLimiter).access(queryPriceRequest, CallPurpose.LIVE);
            }
        }
        if (resultOnly != null && resultOnly.isSucc() && null != resultOnly.getData() && CollectionUtils.isNotEmpty(resultOnly.getData().getHotelPrices())) {
            hotelPriceOnly = resultOnly.getData().getHotelPrices().get(0);
        }
        if (resultPackage != null && resultPackage.isSucc() && null != resultPackage.getData() && CollectionUtils.isNotEmpty(resultPackage.getData().getHotelPrices())) {
            hotelPricePackage = resultPackage.getData().getHotelPrices().get(0);
        }
        // pricing_supplier_query 由查价模板按分态统一打（O-4.3），此处只判分态不再自行计数
        if (null == hotelPriceOnly && null == hotelPricePackage) {
            // 「问到了、答没有」与「压根没问出结果」必须分开（PricingOutcome）：
            // 只要有一趟调用是成功回应的，无报价就是 Expedia 明确说这个住期没有可售；
            // 两趟都没成功回应（超时、非 2xx、限流被拒、响应无法判读）则我们并不知道
            boolean answered = isAnswered(resultOnly) || isAnswered(resultPackage);
            if (answered) {
                log.info("expedia查价：该店该住期无可售报价,property_id={},checkin={}",
                        queryPriceRequest.getProperty_id(), queryPriceRequest.getCheckin());
                return PricingResult.noInventory();
            }
            log.info("expedia查询零售价和打包价全部失败,request:{},response:{}", JsonUtils.writeObject2Json(queryPriceRequest), JsonUtils.writeObject2Json(resultOnly));
            return PricingResult.indeterminate();
        }
        // 零售价(hotel_only)与打包价(hotel_package)是两类不同产品，规则上不可混卖，
        // 各自独立成品返回、各带自己的 priceFlag，不做比价合并
        return PricingResult.of(convertSeparated(hotelPriceOnly, hotelPricePackage, request));
    }

    /**
     * 该趟调用是否「拿到了供应商的回答」——成功回应且响应体可判读。
     *
     * <p>{@code null} 表示这趟压根没发（按 priceFlag 只查了另一类），同样不构成回答。
     */
    private static boolean isAnswered(ResponseResult<QueryPriceResponse> result) {
        return result != null && result.isSucc() && null != result.getData();
    }

    /**
     * 零售价与打包价各自独立成品返回，不做比价合并。
     * hotel_only 与 hotel_package 是两类不同的售卖产品（打包价为捆绑/不透明价，规则上不可当零售价单独售卖），
     * 必须作为独立条目分别返回、各带自己的 priceFlag，交由上游按渠道展示与选择。
     */
    private List<ProductRespDTO> convertSeparated(QueryPriceResponse.HotelPrice hotelPriceOnly,
                                                  QueryPriceResponse.HotelPrice hotelPricePackage,
                                                  PriceReq request) {
        List<ProductRespDTO> productRespDTOS = new ArrayList<>();
        if (null != hotelPriceOnly) {
            productRespDTOS.addAll(convertPriceResp(hotelPriceOnly, "hotel_only", request));
        }
        if (null != hotelPricePackage) {
            productRespDTOS.addAll(convertPriceResp(hotelPricePackage, "hotel_package", request));
        }
        return productRespDTOS;
    }

    private List<ProductRespDTO> convertPriceResp(QueryPriceResponse.HotelPrice hotelPrice, String salesType, PriceReq request) {
        List<ProductRespDTO> productRespDTOS = new ArrayList<>();
        hotelPrice.getRooms().forEach(room -> {
            convertRoomResp(hotelPrice.getProperty_id(), room, salesType, productRespDTOS, request);
        });
        return productRespDTOS;
    }

    private void convertRoomResp(String hotelId, QueryPriceResponse.Rooms room, String salesType, List<ProductRespDTO> productRespDTOS, PriceReq request) {
        if (CollectionUtils.isNotEmpty(room.getRates())) {
            for (QueryPriceResponse.Rates rate : room.getRates()) {
                convertRateResp(hotelId, room.getRoom_name(), room.getId(), rate, salesType, productRespDTOS, request);
            }
        }
    }

    // 包私有以便直测丢弃分支（同艺龙 toPricingResult 的先例）
    void convertRateResp(String hotelId, String roomName, String roomId, QueryPriceResponse.Rates rate, String salesType, List<ProductRespDTO> productRespDTOS, PriceReq request) {
        if (!rate.getOccupancy_pricing().containsKey(request.getOccupancies().get(0))) {
            // 供应商给了这条 rate，但没有本次占用档的价——丢弃必须可数（O-4.5）：
            // 此前这里无日志无计数，Expedia 被过滤的报价无声消失，「丢在哪」只能 grep 和猜
            log.info("expedia查价：rate 缺所查占用档的价，弃之,hotelId={},rateId={},occupancy={}",
                    hotelId, rate.getId(), request.getOccupancies().get(0));
            Monitor.recordOne(MetricNames.QUOTE_DROPPED, MetricTags.dropped(
                    SupplierSourceEnum.EXPEDIA, FunnelStage.CONVERT, DropReason.NO_OCCUPANCY_PRICING));
            return;
        }
        QueryPriceResponse.Occupancy_pricing occupancyPricing = rate.getOccupancy_pricing().get(request.getOccupancies().get(0));
        List<QueryPriceResponse.CancelPolicy> cancelPolicies = rate.getCancel_penalties();
        int sumCommission = calcCommissionCents(occupancyPricing);
        int totalPrice = Money.toCents(new BigDecimal(occupancyPricing.getTotals().getInclusive().getRequest_currency().getValue()));
        int roomTotalPrice = Money.toCents(new BigDecimal(occupancyPricing.getTotals().getExclusive().getRequest_currency().getValue()));
        Meal meal = productKeyDeriver.convertMeal(request.getAdultNum(), rate.getAmenities());
        List<CancelPolicy> cancelPolicy = CollectionUtils.isNotEmpty(rate.getNonrefundable_date_ranges()) ? List.of(CancelPolicy.builder().cancelType(0).build()) : productKeyDeriver.convertCancelPolicy(request.getCheckIn(), cancelPolicies);
        // 身份与成分一次算出（R-2.8）：建档照抄 identity，不得再判一遍
        ProductIdentity identity = productKeyDeriver.deriveIdentity(hotelId, roomId, meal, cancelPolicy, request.getOccupancies().get(0));
        ProductRespDTO productRespDTO = ProductRespDTO.builder().hotelId(hotelId).productId(rate.getId()).productKey(identity.productKey()).identity(identity).supplierId(SupplierSourceEnum.EXPEDIA.getCode()).room(Room.builder().roomName(roomName).roomId(roomId).build()).productInfo(ProductInfo.builder().inventory(1).productStatus(1).productName(roomName).build()).currencyType(occupancyPricing.getTotals().getInclusive().getRequest_currency().getCurrency()).totalPrice(totalPrice - sumCommission).roomTotalPrice(roomTotalPrice - sumCommission).brokerage(sumCommission).stayPrice(buildStayPrice(occupancyPricing.getStay())).priceInfos(buildQueryPriceInfos(occupancyPricing.getNightly(), request.getCheckIn(), sumCommission)).meal(meal).cancelPolicy(cancelPolicy).maxOccupancy(request.getAdultNum()).priceFlag(salesType).distribution(rate.getSale_scenario().getDistribution()).build();
        productRespDTO.setTotalTaxes(productRespDTO.getTotalPrice() - productRespDTO.getRoomTotalPrice());
        productRespDTOS.add(productRespDTO);
    }

    private static Integer buildStayPrice(List<QueryPriceResponse.Stay> stayList) {
        Integer stayPrice = 0;
        if (CollectionUtils.isNotEmpty(stayList)) {
            for (QueryPriceResponse.Stay stay : stayList) {
                stayPrice += Money.toCents(new BigDecimal(stay.getValue()));
            }
        }
        return stayPrice;
    }

    /**
     * 逐晚拆分报价，并把总佣金摊到各晚。
     *
     * <p><b>不变量：{@code Σ priceInfos.price == totalPrice}</b>。走缓存的读路径是逐晚累加
     * 重算总价（{@code PriceCacheServiceImpl.getPrice}），而实时路径扣的是全额佣金；佣金若
     * 按晚整除、余数丢弃，同一产品两条路径就会报出相差 {@code sumCommission mod n} 分的
     * 两个总价（issue #99）。金额极小（最多 n-1 分），但口径不唯一，日后对账会冒出一批
     * 无法解释的分位差。故余数必须摊回去：前 {@code remainder} 晚各多扣 1 分。
     *
     * <p>{@code nightlyLists} 为空或 null 时返回空列表而不是抛异常：整除会 {@code / 0}，
     * 取 size 会 NPE，而调用链一路无 guard，抛出后在刷价路径上被单行 try 兜成"该行整体
     * 报废"，在线路径上就是查价失败。尚无证据表明 Expedia 会返回没有 nightly 明细的 rate，
     * 故此处属预防。
     */
    public List<PriceInfo> buildQueryPriceInfos(List<List<QueryPriceResponse.Nightly>> nightlyLists, String checkIn, int sumCommission) {
        if (CollectionUtils.isEmpty(nightlyLists)) {
            // §6.2.1 非常态走向必须有落点；此处拿得到的键只有住期与佣金，有几个带几个（§6.1.2）
            log.warn("expedia查价：rate 无 nightly 明细，逐晚报价按空处理,checkIn={},sumCommission={}分",
                    checkIn, sumCommission);
            return Lists.newArrayList();
        }
        List<PriceInfo> priceInfos = Lists.newArrayList();
        int nights = nightlyLists.size();
        // Math.floorDiv/floorMod 而非 / 与 %：佣金理论上非负，但一旦为负，
        // Java 的 % 会给出负余数，摊派后总和不再等于 sumCommission，不变量即失守
        int commissionPerNight = Math.floorDiv(sumCommission, nights);
        int remainder = Math.floorMod(sumCommission, nights);
        for (int i = 0; i < nightlyLists.size(); i++) {
            // 前 remainder 晚各多扣 1 分，使 Σ 各晚扣减恰好等于 sumCommission
            int commission = commissionPerNight + (i < remainder ? 1 : 0);
            BigDecimal sumPrice = BigDecimal.ZERO; // 初始化总价累加器为0
            BigDecimal taxes = BigDecimal.ZERO; // 初始化税费累加器为0
            BigDecimal roomPrice = BigDecimal.ZERO; // 初始化房费累加器为0
            for (QueryPriceResponse.Nightly nightly : nightlyLists.get(i)) {
                sumPrice = sumPrice.add(new BigDecimal(nightly.getValue()));
                if ("base_rate".equals(nightly.getType()) || "extra_person_fee".equals(nightly.getType())) {
                    roomPrice = roomPrice.add(new BigDecimal(nightly.getValue()));
                } else {
                    taxes = taxes.add(new BigDecimal(nightly.getValue()));
                }
            }
            PriceInfo priceInfo = PriceInfo.builder().date(DateUtil.getFutureDay(checkIn, i)).price(Money.toCents(sumPrice) - commission).roomPrice(Money.toCents(roomPrice) - commission).taxes(Money.toCents(taxes)).build();
            priceInfos.add(priceInfo);
        }
        return priceInfos;
    }

    @Override
    public List<ProductRespDTO> queryProductPrice(PriceReq request, Supplier supplier) {

        QueryPriceRequest queryPriceRequest = contractProfile.newRequestBuilder().property_id(supplier.getSHotelId()).checkin(request.getCheckIn()).checkout(request.getCheckout()).currency(StringUtils.isBlank(request.getCurrency()) ? DEFAULT_QUOTE_CURRENCY : request.getCurrency()).build();
        List<String> occupancies = new ArrayList<>();
        for (int i = 0; i < request.getRoomNum(); i++) {
            String childrenList = "";
            if (null != request.getChildNum() && 0 != request.getChildNum() && CollectionUtils.isNotEmpty(request.getChildAges())) {
                for (Integer childAge : request.getChildAges()) {
                    if (StringUtils.isBlank(childrenList)) {
                        childrenList = "-" + childAge;
                    } else {
                        childrenList = childrenList + "," + childAge;
                    }
                }
            }
            occupancies.add(request.getAdultNum() + childrenList);
        }
        queryPriceRequest.setOccupancies(occupancies);
        request.setOccupancies(occupancies);
        // 缺省优先零售价(hotel_only，常态且无捆绑限制)，第一趟未命中再回退打包价(hotel_package)
        queryPriceRequest.setSales_environment(StringUtils.isBlank(request.getPriceFlag()) ? "hotel_only" : request.getPriceFlag());
        ResponseResult<QueryPriceResponse> resultPackage = new QueryProductAccess(host, StringUtils.isBlank(request.getLanguage()) ? "zh-CN" : request.getLanguage(), expediaUtils.generateSign(), ownIp, sessionId, rateLimiter).access(queryPriceRequest, CallPurpose.LIVE);
        boolean isHave = true;
        if (resultPackage != null && resultPackage.isSucc() && null != resultPackage.getData() && CollectionUtils.isNotEmpty(resultPackage.getData().getHotelPrices())) {
            QueryPriceResponse.HotelPrice hotelPrice = resultPackage.getData().getHotelPrices().get(0);
            for (QueryPriceResponse.Rooms room : hotelPrice.getRooms()) {
                for (QueryPriceResponse.Rates rate : room.getRates()) {
                    if (supplier.getSProductId().equals(rate.getId())) {
                        isHave = false;
                        ArrayList<BedCheckInfo> bedCheckInfos = new ArrayList<>();
                        for (String bedId : rate.getBed_groups().keySet()) {
                            QueryPriceResponse.Bed_groups bedGroups = rate.getBed_groups().get(bedId);
                            bedCheckInfos.add(BedCheckInfo.builder().bedId(bedGroups.getId()).bedType(bedGroups.getDescription()).checkHref(bedGroups.getLinks().getPrice_check().getHref()).build());
                        }
                        ResponseResult<CheckPriceResponse> checkPriceResult = new CheckPriceAccess(host, StringUtils.isBlank(request.getLanguage()) ? "zh-CN" : request.getLanguage(), expediaUtils.generateSign(), ownIp, sessionId, rateLimiter).access(contractProfile.appendTo(bedCheckInfos.get(0).getCheckHref()), CallPurpose.LIVE);
                        if (!checkPriceResult.isSucc() || null == checkPriceResult.getData() || "sold_out".equals(checkPriceResult.getData().getStatus())) {
                            log.info("expedia验价失败,request:{},response:{}", JsonUtils.writeObject2Json(request), JsonUtils.writeObject2Json(checkPriceResult));
                            return null;
                        }
                        QueryPriceResponse.Occupancy_pricing occupancyPricing = checkPriceResult.getData().getOccupancy_pricing().get(request.getOccupancies().get(0));
                        int sumCommission = calcCommissionCents(occupancyPricing);
                        int totalPrice = Money.toCents(new BigDecimal(occupancyPricing.getTotals().getInclusive().getRequest_currency().getValue()));
                        int roomTotalPrice = Money.toCents(new BigDecimal(occupancyPricing.getTotals().getExclusive().getRequest_currency().getValue()));
                        List<QueryPriceResponse.CancelPolicy> cancelPolicies = rate.getCancel_penalties();
                        Meal meal = productKeyDeriver.convertMeal(request.getAdultNum(), rate.getAmenities());
                        List<CancelPolicy> cancelPolicy = CollectionUtils.isNotEmpty(rate.getNonrefundable_date_ranges()) ? List.of(CancelPolicy.builder().cancelType(0).build()) : productKeyDeriver.convertCancelPolicy(request.getCheckIn(), cancelPolicies);
                        // 身份与成分一次算出（R-2.8）：建档照抄 identity，不得再判一遍
                        ProductIdentity identity = productKeyDeriver.deriveIdentity(hotelPrice.getProperty_id(), room.getId(), meal, cancelPolicy, request.getOccupancies().get(0));
                        ProductRespDTO productRespDTO = ProductRespDTO.builder().hotelId(hotelPrice.getProperty_id()).productId(rate.getId()).productKey(identity.productKey()).identity(identity).supplierId(SupplierSourceEnum.EXPEDIA.getCode()).room(Room.builder().roomName(room.getRoom_name()).roomId(room.getId()).build()).productInfo(ProductInfo.builder().inventory(1).productStatus(1).productName(room.getRoom_name()).build()).currencyType(occupancyPricing.getTotals().getInclusive().getRequest_currency().getCurrency()).totalPrice(totalPrice - sumCommission).stayPrice(buildStayPrice(occupancyPricing.getStay())).storePayPrice(null == occupancyPricing.getTotals().getProperty_fees() ? 0 : Money.toCents(new BigDecimal(occupancyPricing.getTotals().getProperty_fees().getBillable_currency().getValue()))).storePayCurrency(null == occupancyPricing.getTotals().getProperty_fees() ? request.getCurrency() : occupancyPricing.getTotals().getProperty_fees().getBillable_currency().getCurrency()).roomTotalPrice(roomTotalPrice - sumCommission).brokerage(sumCommission).priceInfos(buildQueryPriceInfos(occupancyPricing.getNightly(), request.getCheckIn(), sumCommission)).meal(meal).cancelPolicy(cancelPolicy).maxOccupancy(request.getAdultNum()).priceFlag(queryPriceRequest.getSales_environment()).distribution(rate.getSale_scenario().getDistribution()).bedCheckInfos(bedCheckInfos).build();
                        productRespDTO.setTotalTaxes(productRespDTO.getTotalPrice() - productRespDTO.getRoomTotalPrice());
                        return Arrays.asList(productRespDTO);
                    }
                }
            }
        }
        if (isHave) {
            queryPriceRequest.setSales_environment(StringUtils.isBlank(request.getPriceFlag()) ? "hotel_package" : request.getPriceFlag());
            ResponseResult<QueryPriceResponse> onlyResult = new QueryProductAccess(host, StringUtils.isBlank(request.getLanguage()) ? "zh-CN" : request.getLanguage(), expediaUtils.generateSign(), ownIp, sessionId, rateLimiter).access(queryPriceRequest, CallPurpose.LIVE);
            if (onlyResult != null && onlyResult.isSucc() && null != onlyResult.getData() && CollectionUtils.isNotEmpty(onlyResult.getData().getHotelPrices())) {
                QueryPriceResponse.HotelPrice hotelPrice = onlyResult.getData().getHotelPrices().get(0);
                for (QueryPriceResponse.Rooms room : hotelPrice.getRooms()) {
                    for (QueryPriceResponse.Rates rate : room.getRates()) {
                        if (supplier.getSProductId().equals(rate.getId())) {
                            isHave = false;
                            ArrayList<BedCheckInfo> bedCheckInfos = new ArrayList<>();
                            for (String bedId : rate.getBed_groups().keySet()) {
                                QueryPriceResponse.Bed_groups bedGroups = rate.getBed_groups().get(bedId);
                                bedCheckInfos.add(BedCheckInfo.builder().bedId(bedGroups.getId()).bedType(bedGroups.getDescription()).checkHref(bedGroups.getLinks().getPrice_check().getHref()).build());
                            }
                            ResponseResult<CheckPriceResponse> checkPriceResult = new CheckPriceAccess(host, StringUtils.isBlank(request.getLanguage()) ? "zh-CN" : request.getLanguage(), expediaUtils.generateSign(), ownIp, sessionId, rateLimiter).access(contractProfile.appendTo(bedCheckInfos.get(0).getCheckHref()), CallPurpose.LIVE);
                            if (!checkPriceResult.isSucc() || null == checkPriceResult.getData() || "sold_out".equals(checkPriceResult.getData().getStatus())) {
                                log.info("expedia验价失败,request:{},response:{}", JsonUtils.writeObject2Json(request), JsonUtils.writeObject2Json(checkPriceResult));
                                return null;
                            }
                            QueryPriceResponse.Occupancy_pricing occupancyPricing = checkPriceResult.getData().getOccupancy_pricing().get(request.getOccupancies().get(0));
                            int sumCommission = calcCommissionCents(occupancyPricing);
                            int totalPrice = Money.toCents(new BigDecimal(occupancyPricing.getTotals().getInclusive().getRequest_currency().getValue()));
                            int roomTotalPrice = Money.toCents(new BigDecimal(occupancyPricing.getTotals().getExclusive().getRequest_currency().getValue()));
                            List<QueryPriceResponse.CancelPolicy> cancelPolicies = rate.getCancel_penalties();
                            Meal meal = productKeyDeriver.convertMeal(request.getAdultNum(), rate.getAmenities());
                            List<CancelPolicy> cancelPolicy = CollectionUtils.isNotEmpty(rate.getNonrefundable_date_ranges()) ? List.of(CancelPolicy.builder().cancelType(0).build()) : productKeyDeriver.convertCancelPolicy(request.getCheckIn(), cancelPolicies);
                            // 身份与成分一次算出（R-2.8）：建档照抄 identity，不得再判一遍
                            ProductIdentity identity = productKeyDeriver.deriveIdentity(hotelPrice.getProperty_id(), room.getId(), meal, cancelPolicy, request.getOccupancies().get(0));
                            ProductRespDTO productRespDTO = ProductRespDTO.builder().hotelId(hotelPrice.getProperty_id()).productId(rate.getId()).productKey(identity.productKey()).identity(identity).supplierId(SupplierSourceEnum.EXPEDIA.getCode()).room(Room.builder().roomName(room.getRoom_name()).roomId(room.getId()).build()).productInfo(ProductInfo.builder().inventory(1).productStatus(1).productName(room.getRoom_name()).build()).currencyType(occupancyPricing.getTotals().getInclusive().getRequest_currency().getCurrency()).totalPrice(totalPrice - sumCommission).stayPrice(buildStayPrice(occupancyPricing.getStay())).storePayPrice(null == occupancyPricing.getTotals().getProperty_fees() ? 0 : Money.toCents(new BigDecimal(occupancyPricing.getTotals().getProperty_fees().getBillable_currency().getValue()))).storePayCurrency(null == occupancyPricing.getTotals().getProperty_fees() ? request.getCurrency() : occupancyPricing.getTotals().getProperty_fees().getBillable_currency().getCurrency()).roomTotalPrice(roomTotalPrice - sumCommission).brokerage(sumCommission).priceInfos(buildQueryPriceInfos(occupancyPricing.getNightly(), request.getCheckIn(), sumCommission)).meal(meal).cancelPolicy(cancelPolicy).maxOccupancy(request.getAdultNum()).priceFlag(queryPriceRequest.getSales_environment()).distribution(rate.getSale_scenario().getDistribution()).bedCheckInfos(bedCheckInfos).build();
                            productRespDTO.setTotalTaxes(productRespDTO.getTotalPrice() - productRespDTO.getRoomTotalPrice());
                            return Arrays.asList(productRespDTO);
                        }
                    }
                }
            }
        }
        log.info("expedia查询价格失败,request:{},response:{}", JsonUtils.writeObject2Json(queryPriceRequest), JsonUtils.writeObject2Json(resultPackage));
        return null;
    }

    // ---------- 验价钩子：流程在 ExpediaCheckPriceServiceImpl（模板），这里只是供应商侧的读法 ----------

    /**
     * 要依次找票的售卖环境：上游指定了 priceFlag 就只按它查；未指定先零售、零售侧确证
     * RATE_DEAD 才换打包价再找一次（不确定或已售罄时再查既救不回也会掩盖成因）。
     */
    public List<String> salesEnvironments(CheckPriceReq request) {
        if (StringUtils.isNotBlank(request.getPriceFlag())) {
            return List.of(request.getPriceFlag());
        }
        return List.of(SALES_ENV_HOTEL_ONLY, SALES_ENV_HOTEL_PACKAGE);
    }

    /** 现取：在指定售卖环境下重打一次查价 */
    public LiveStock<QueryPriceResponse> fetchLiveStock(CheckPriceReq request, String salesEnvironment) {
        QueryPriceRequest queryPriceRequest = contractProfile.newRequestBuilder().property_id(request.getSHotelId()).checkin(request.getCheckIn()).checkout(request.getCheckOut()).currency(StringUtils.isBlank(request.getCurrency()) ? DEFAULT_QUOTE_CURRENCY : request.getCurrency()).build();
        queryPriceRequest.setOccupancies(buildOccupancies(request));
        queryPriceRequest.setSales_environment(salesEnvironment);
        ResponseResult<QueryPriceResponse> result = new QueryProductAccess(host, StringUtils.isBlank(request.getLanguage()) ? "zh-CN" : request.getLanguage(), expediaUtils.generateSign(), ownIp, sessionId, rateLimiter).access(queryPriceRequest, CallPurpose.CHECK_PRICE);
        if (result == null || !result.isSucc() || null == result.getData()
                || CollectionUtils.isEmpty(result.getData().getHotelPrices())) {
            log.warn("expedia查价未取得结果,salesEnvironment={},request:{}", salesEnvironment, JsonUtils.writeObject2Json(request));
            return LiveStock.terminal(outcome(CheckPriceOutcome.INDETERMINATE, "查价调用未取得结果，未能确认该产品是否可订，请稍后重试"));
        }
        return LiveStock.of(result.getData());
    }

    /**
     * 换票候选：对每条现货报价按<b>与查价时完全相同的口径</b>重派生 productKey，键相等才收
     * （硬门 R-3.2 由键相等保证）；价格=含税价 − 佣金，与查价透出的 totalPrice 同一算法。
     */
    public List<ResolveCandidate<QueryPriceResponse.Rates>> resolveCandidates(QueryPriceResponse data, CheckPriceReq request) {
        String occupancy = buildOccupancies(request).get(0);
        List<ResolveCandidate<QueryPriceResponse.Rates>> equivalents = new ArrayList<>();
        QueryPriceResponse.HotelPrice hotelPrice = data.getHotelPrices().get(0);
        if (hotelPrice.getRooms() == null) {
            return equivalents;
        }
        for (QueryPriceResponse.Rooms room : hotelPrice.getRooms()) {
            if (room.getRates() == null) {
                continue;
            }
            for (QueryPriceResponse.Rates candidate : room.getRates()) {
                QueryPriceResponse.Occupancy_pricing pricing = candidate.getOccupancy_pricing() == null
                        ? null : candidate.getOccupancy_pricing().get(occupancy);
                if (pricing == null || pricing.getTotals() == null || pricing.getTotals().getInclusive() == null) {
                    continue;
                }
                Meal meal = productKeyDeriver.convertMeal(request.getAdultCount(), candidate.getAmenities());
                List<CancelPolicy> cancelPolicy = CollectionUtils.isNotEmpty(candidate.getNonrefundable_date_ranges())
                        ? List.of(CancelPolicy.builder().cancelType(0).build())
                        : productKeyDeriver.convertCancelPolicy(request.getCheckIn(), candidate.getCancel_penalties());
                String key = productKeyDeriver.deriveProductKey(hotelPrice.getProperty_id(), room.getId(), meal, cancelPolicy, occupancy);
                if (!request.getProductKey().equals(key)) {
                    continue;
                }
                int priceCents = Money.toCents(new BigDecimal(
                        pricing.getTotals().getInclusive().getRequest_currency().getValue()))
                        - calcCommissionCents(pricing);
                equivalents.add(new ResolveCandidate<>(candidate, priceCents));
            }
        }
        return equivalents;
    }

    /** 找到票之后的自检：所选床型已不可选即死票 */
    public CheckPriceRespDTO inspect(QueryPriceResponse.Rates rate, CheckPriceReq request) {
        if (null == pickBedGroup(rate, request.getBedId())) {
            log.info("expedia验价：所选床型已不可选,sProductId={},bedId={}", request.getSProductId(), request.getBedId());
            return outcome(CheckPriceOutcome.RATE_DEAD, "所选床型已不可选，请重新查价后再选择");
        }
        return null;
    }

    /**
     * 下单前档：打 price_check 并归入确定的分态。原实现对「所点产品不在响应里」「床型不可选」
     * 「已售罄」「调用失败」一律返回 null，上游无从区分该重新查价、告知满房还是稍后重试。
     */
    public CheckPriceRespDTO validate(CheckPriceReq request, QueryPriceResponse.Rates rate) {
        String occupancy = buildOccupancies(request).get(0);
        QueryPriceResponse.Bed_groups bedGroups = pickBedGroup(rate, request.getBedId());
        ResponseResult<CheckPriceResponse> checkPriceResult = new CheckPriceAccess(host, StringUtils.isBlank(request.getLanguage()) ? "zh-CN" : request.getLanguage(), expediaUtils.generateSign(), ownIp, sessionId, rateLimiter).access(contractProfile.appendTo(bedGroups.getLinks().getPrice_check().getHref()), CallPurpose.CHECK_PRICE);
        if (checkPriceResult == null || !checkPriceResult.isSucc() || null == checkPriceResult.getData()) {
            log.warn("expedia验价未取得结果,sProductId={},response:{}", request.getSProductId(), JsonUtils.writeObject2Json(checkPriceResult));
            return outcome(CheckPriceOutcome.INDETERMINATE, "验价调用未取得结果，未能确认该产品是否可订，请稍后重试");
        }
        if (STATUS_SOLD_OUT.equals(checkPriceResult.getData().getStatus())) {
            // 供应商明确回答满房，这是确定性结果，可以如实告知旅客
            return outcome(CheckPriceOutcome.SOLD_OUT, "该产品已售罄");
        }

        checkPriceResult.getData().setAdultCount(request.getAdultCount());
        QueryPriceResponse.Occupancy_pricing occupancyPricing =
                checkPriceResult.getData().getOccupancy_pricing() == null ? null
                        : checkPriceResult.getData().getOccupancy_pricing().get(occupancy);
        if (occupancyPricing == null || occupancyPricing.getTotals() == null
                || occupancyPricing.getTotals().getInclusive() == null) {
            // 供应商说可订却没给出本次占用的价格，属响应自相矛盾：既不能报可订（没有价），
            // 也不能报不可订（供应商并未这么说）
            log.error("expedia验价：响应缺少本次占用的价格,sProductId={},occupancy={}",
                    request.getSProductId(), occupancy);
            return outcome(CheckPriceOutcome.INDETERMINATE, "验价响应缺少本次占用的价格，未能确认该产品是否可订");
        }
        return buildCheckPriceResp(checkPriceResult.getData(), occupancyPricing);
    }

    /** 占用串：一间房一项，格式为「成人数-儿童年龄,儿童年龄」 */
    private List<String> buildOccupancies(CheckPriceReq request) {
        List<String> occupancies = new ArrayList<>();
        for (int i = 0; i < request.getRoomNum(); i++) {
            String childrenList = "";
            if (null != request.getChildNum() && 0 != request.getChildNum() && CollectionUtils.isNotEmpty(request.getChildAges())) {
                for (Integer childAge : request.getChildAges()) {
                    if (StringUtils.isBlank(childrenList)) {
                        childrenList = "-" + childAge;
                    } else {
                        childrenList = childrenList + "," + childAge;
                    }
                }
            }
            occupancies.add(request.getAdultCount() + childrenList);
        }
        return occupancies;
    }

    /** 在查价响应中找出所点的报价；找不到返回 null */
    public QueryPriceResponse.Rates findRate(QueryPriceResponse data, String sProductId) {
        QueryPriceResponse.HotelPrice hotelPrice = data.getHotelPrices().get(0);
        if (hotelPrice.getRooms() == null) {
            return null;
        }
        for (QueryPriceResponse.Rooms room : hotelPrice.getRooms()) {
            if (room.getRates() == null) {
                continue;
            }
            for (QueryPriceResponse.Rates rate : room.getRates()) {
                if (sProductId.equals(rate.getId())) {
                    return rate;
                }
            }
        }
        return null;
    }

    /** 取指定床型组合；未指定时取任意一个。所指定的床型不存在时返回 null */
    QueryPriceResponse.Bed_groups pickBedGroup(QueryPriceResponse.Rates rate, String bedId) {
        if (rate.getBed_groups() == null) {
            return null;
        }
        if (StringUtils.isNotBlank(bedId)) {
            return rate.getBed_groups().get(bedId);
        }
        QueryPriceResponse.Bed_groups any = null;
        for (String key : rate.getBed_groups().keySet()) {
            any = rate.getBed_groups().get(key);
        }
        return any;
    }

    private CheckPriceRespDTO outcome(CheckPriceOutcome outcome, String message) {
        return CheckPriceRespDTO.builder().outcome(outcome).message(message).build();
    }


    @Override
    public List<ProductRespDTO> queryPricesCache(PriceReq request, Supplier supplier) {
        QueryPriceRequest queryPriceRequest = contractProfile.newRequestBuilder().property_id(supplier.getSHotelId()).checkin(request.getCheckIn()).checkout(request.getCheckout()).currency(StringUtils.isBlank(request.getCurrency()) ? DEFAULT_QUOTE_CURRENCY : request.getCurrency()).sales_environment("hotel_only").build();
        List<String> occupancies = new ArrayList<>();
        for (int i = 0; i < request.getRoomNum(); i++) {
            String childrenList = "";
            if (null != request.getChildNum() && 0 != request.getChildNum() && CollectionUtils.isNotEmpty(request.getChildAges())) {
                for (Integer childAge : request.getChildAges()) {
                    if (StringUtils.isBlank(childrenList)) {
                        childrenList = "-" + childAge;
                    } else {
                        childrenList = childrenList + "," + childAge;
                    }
                }
            }
            occupancies.add(request.getAdultNum() + childrenList);
        }
        queryPriceRequest.setOccupancies(occupancies);
        request.setOccupancies(occupancies);

        // 缓存只刷零售价：当前渠道以 standalone 售卖为主；且缓存结构(price:hotelId:date 的 field=productId)
        // 无售卖类型维度，同一 rateId 的打包价会覆盖零售价。待渠道开卖打包价时，
        // 需先给缓存键补类型维度，再在此处放开 hotel_package 查询。
        queryPriceRequest.setSales_environment("hotel_only");
        ResponseResult<QueryPriceResponse> resultOnly = new QueryProductAccess(host, "zh-CN", expediaUtils.generateSign(), ownIp, sessionId, rateLimiter).access(queryPriceRequest, CallPurpose.REFRESH);
        if (null == resultOnly || !resultOnly.isSucc() || null == resultOnly.getData()
                || CollectionUtils.isEmpty(resultOnly.getData().getHotelPrices())) {
            // 「答了但没有」与「没问出结果」分开记（O-3.1）。这两笔是刷价腿的<b>留守</b>：
            // 本方法对两种情形都返回 null，上层 RefreshOutcome 只见 FAILED，refresh_empty
            // 对 Expedia 恒为 0——在把「答了没货→返回空列表」（对齐艺龙口径，B7 僵尸价
            // 标记也系于此）修好之前，删这两笔就删掉了该腿唯一的三态信号
            Monitor.recordOne(MetricNames.PRICING_SUPPLIER_QUERY,
                    pricingTags(isAnswered(resultOnly) ? CallStatus.NO_INVENTORY : CallStatus.ERROR));
            log.info("expedia缓存查询零售价失败,request:{}", JsonUtils.writeObject2Json(queryPriceRequest));
            return null;
        }
        Monitor.recordOne(MetricNames.PRICING_SUPPLIER_QUERY, pricingTags(CallStatus.QUOTED));
        List<ProductRespDTO> productRespDTOList = convertPriceResp(resultOnly.getData().getHotelPrices().get(0), "hotel_only", request);

        //插入缓存
        priceCacheService.productToCache(productRespDTOList, request, supplier);
        // 建档(R-2.6):稳定事实落库,与写缓存同一处、同一份数据,不额外调供应商。
        // 开关默认关;失败不打断刷价(服务内部已吞异常)
        expediaCatalogService.upsert(productRespDTOList);
        return productRespDTOList;
    }

    /**
     * 出价指标的维度。supplier 与成败都必须进 tag，不进名字（§3.9.2）——
     * 原先是 {@code expedia_all_query} / {@code _fail} / {@code _success} 三个名字，
     * 接第二家供应商就要再造三个。
     */
    private static Map<String, Object> pricingTags(CallStatus status) {
        return MetricTags.of(SupplierSourceEnum.EXPEDIA, status);
    }
}
