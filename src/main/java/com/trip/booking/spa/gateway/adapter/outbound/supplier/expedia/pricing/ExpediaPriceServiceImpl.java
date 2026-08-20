package com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.pricing;

import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.BedCheckInfo;
import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.CancelPolicy;
import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.CheckPriceRespDTO;
import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.Meal;
import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.PriceInfo;
import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.ProductInfo;
import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.ProductRespDTO;
import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.Room;
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
import com.trip.booking.spa.gateway.domain.product.ResolveGate;
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
import com.trip.booking.spa.gateway.application.pricing.CachePriceService;
import com.trip.booking.spa.gateway.application.pricing.PricingResult;
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

    private static final String SALES_ENV_HOTEL_ONLY = "hotel_only";
    private static final String SALES_ENV_HOTEL_PACKAGE = "hotel_package";

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
        int inclusiveCents = new BigDecimal(occupancyPricing.getTotals().getInclusive()
                .getRequest_currency().getValue()).multiply(new BigDecimal("100")).intValue();
        return CheckPriceRespDTO.builder()
                .outcome(CheckPriceOutcome.BOOKABLE)
                .offerId(offerId)
                .offerTtlSeconds(offerStore.getTtlSeconds())
                .salePrice(inclusiveCents)
                .subPrice(inclusiveCents)
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
        return new BigDecimal(totals.getMarketing_fee().getRequest_currency().getValue())
                .multiply(new BigDecimal("100")).setScale(0, BigDecimal.ROUND_DOWN).intValue();
    }

    @Resource
    private ExpediaUtils expediaUtils;
    @Resource
    private DistributedRateLimiter rateLimiter;

    @Autowired
    private CachePriceService cachePriceService;

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
                resultOnly = new QueryProductAccess(host, "en-US", expediaUtils.signGeneration(), ownIp, sessionId, rateLimiter).access(queryPriceRequest);
            } else if ("hotel_package".equals(request.getPriceFlag())) {
                //查询打包价
                queryPriceRequest.setSales_environment("hotel_package");
                resultPackage = new QueryProductAccess(host, "en-US", expediaUtils.signGeneration(), ownIp, sessionId, rateLimiter).access(queryPriceRequest);
            } else {
                //先查询零售价
                queryPriceRequest.setSales_environment("hotel_only");
                resultOnly = new QueryProductAccess(host, "en-US", expediaUtils.signGeneration(), ownIp, sessionId, rateLimiter).access(queryPriceRequest);
                //查询打包价
                queryPriceRequest.setSales_environment("hotel_package");
                resultPackage = new QueryProductAccess(host, "en-US", expediaUtils.signGeneration(), ownIp, sessionId, rateLimiter).access(queryPriceRequest);
            }
        } else {
            if ("hotel_only".equals(request.getPriceFlag())) {
                //先查询零售价
                queryPriceRequest.setSales_environment("hotel_only");
                resultOnly = new QueryProductAccess(host, "zh-CN", expediaUtils.signGeneration(), ownIp, sessionId, rateLimiter).access(queryPriceRequest);
            } else if ("hotel_package".equals(request.getPriceFlag())) {
                //查询打包价
                queryPriceRequest.setSales_environment("hotel_package");
                resultPackage = new QueryProductAccess(host, "zh-CN", expediaUtils.signGeneration(), ownIp, sessionId, rateLimiter).access(queryPriceRequest);
            } else {
                //先查询零售价
                queryPriceRequest.setSales_environment("hotel_only");
                resultOnly = new QueryProductAccess(host, "zh-CN", expediaUtils.signGeneration(), ownIp, sessionId, rateLimiter).access(queryPriceRequest);
                //查询打包价
                queryPriceRequest.setSales_environment("hotel_package");
                resultPackage = new QueryProductAccess(host, "zh-CN", expediaUtils.signGeneration(), ownIp, sessionId, rateLimiter).access(queryPriceRequest);
            }
        }
        if (resultOnly != null && resultOnly.isSucc() && null != resultOnly.getData() && CollectionUtils.isNotEmpty(resultOnly.getData().getHotelPrices())) {
            hotelPriceOnly = resultOnly.getData().getHotelPrices().get(0);
        }
        if (resultPackage != null && resultPackage.isSucc() && null != resultPackage.getData() && CollectionUtils.isNotEmpty(resultPackage.getData().getHotelPrices())) {
            hotelPricePackage = resultPackage.getData().getHotelPrices().get(0);
        }
        Monitor.recordOne("pricing_supplier_query", pricingTags("all"));
        if (null == hotelPriceOnly && null == hotelPricePackage) {
            // 「问到了、答没有」与「压根没问出结果」必须分开（PricingOutcome）：
            // 只要有一趟调用是成功回应的，无报价就是 Expedia 明确说这个住期没有可售；
            // 两趟都没成功回应（超时、非 2xx、限流被拒、响应无法判读）则我们并不知道
            boolean answered = answered(resultOnly) || answered(resultPackage);
            if (answered) {
                log.info("expedia查价：该店该住期无可售报价,property_id={},checkin={}",
                        queryPriceRequest.getProperty_id(), queryPriceRequest.getCheckin());
                Monitor.recordOne("pricing_supplier_query", pricingTags("empty"));
                return PricingResult.noInventory();
            }
            log.info("expedia查询零售价和打包价全部失败,request:{},response:{}", JsonUtils.writeObject2Json(queryPriceRequest), JsonUtils.writeObject2Json(resultOnly));
            Monitor.recordOne("pricing_supplier_query", pricingTags("fail"));
            return PricingResult.indeterminate();
        }
        Monitor.recordOne("pricing_supplier_query", pricingTags("success"));
        // 零售价(hotel_only)与打包价(hotel_package)是两类不同产品，规则上不可混卖，
        // 各自独立成品返回、各带自己的 priceFlag，不做比价合并
        return PricingResult.of(convertSeparated(hotelPriceOnly, hotelPricePackage, request));
    }

    /**
     * 该趟调用是否「拿到了供应商的回答」——成功回应且响应体可判读。
     *
     * <p>{@code null} 表示这趟压根没发（按 priceFlag 只查了另一类），同样不构成回答。
     */
    private static boolean answered(ResponseResult<QueryPriceResponse> result) {
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

    private void convertRateResp(String hotelId, String roomName, String roomId, QueryPriceResponse.Rates rate, String salesType, List<ProductRespDTO> productRespDTOS, PriceReq request) {
        if (rate.getOccupancy_pricing().containsKey(request.getOccupancies().get(0))) {
            QueryPriceResponse.Occupancy_pricing occupancyPricing = rate.getOccupancy_pricing().get(request.getOccupancies().get(0));
            List<QueryPriceResponse.CancelPolicy> cancelPolicies = rate.getCancel_penalties();
            int sumCommission = calcCommissionCents(occupancyPricing);
            int totalPrice = new BigDecimal(occupancyPricing.getTotals().getInclusive().getRequest_currency().getValue()).multiply(new BigDecimal("100")).intValue();
            int roomTotalPrice = new BigDecimal(occupancyPricing.getTotals().getExclusive().getRequest_currency().getValue()).multiply(new BigDecimal("100")).intValue();
            Meal meal = productKeyDeriver.convertMeal(request.getAdultNum(), rate.getAmenities());
            List<CancelPolicy> cancelPolicy = CollectionUtils.isNotEmpty(rate.getNonrefundable_date_ranges()) ? List.of(CancelPolicy.builder().cancelType(0).build()) : productKeyDeriver.convertCancelPolicy(request.getCheckIn(), cancelPolicies);
            ProductRespDTO productRespDTO = ProductRespDTO.builder().hotelId(hotelId).productId(rate.getId()).productKey(productKeyDeriver.deriveProductKey(hotelId, roomId, meal, cancelPolicy, request.getOccupancies().get(0))).supplierId(SupplierSourceEnum.EXPEDIA.getCode()).room(Room.builder().roomName(roomName).roomId(roomId).build()).productInfo(ProductInfo.builder().inventory(1).productStatus(1).productName(roomName).build()).currencyType(occupancyPricing.getTotals().getInclusive().getRequest_currency().getCurrency()).totalPrice(totalPrice - sumCommission).roomTotalPrice(roomTotalPrice - sumCommission).brokerage(sumCommission).stayPrice(buildStayPrice(occupancyPricing.getStay())).priceInfos(buildQueryPriceInfos(occupancyPricing.getNightly(), request.getCheckIn(), sumCommission)).meal(meal).cancelPolicy(cancelPolicy).maxOccupancy(request.getAdultNum()).priceFlag(salesType).distribution(rate.getSale_scenario().getDistribution()).build();
            productRespDTO.setTotalTaxes(productRespDTO.getTotalPrice() - productRespDTO.getRoomTotalPrice());
            productRespDTOS.add(productRespDTO);
        }
    }

    private static Integer buildStayPrice(List<QueryPriceResponse.Stay> stayList) {
        Integer stayPrice = 0;
        if (CollectionUtils.isNotEmpty(stayList)) {
            for (QueryPriceResponse.Stay stay : stayList) {
                stayPrice += new BigDecimal(stay.getValue()).multiply(new BigDecimal("100")).intValue();
            }
        }
        return stayPrice;
    }

    /**
     * 逐晚拆分报价，并把总佣金摊到各晚。
     *
     * <p><b>不变量：{@code Σ priceInfos.price == totalPrice}</b>。走缓存的读路径是逐晚累加
     * 重算总价（{@code CachePriceServiceImpl.getPrice}），而实时路径扣的是全额佣金；佣金若
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
            PriceInfo priceInfo = PriceInfo.builder().date(DateUtil.getFutureDay(checkIn, i)).price(sumPrice.multiply(BigDecimal.valueOf(100)).intValue() - commission).roomPrice(roomPrice.multiply(BigDecimal.valueOf(100)).intValue() - commission).taxes(taxes.multiply(BigDecimal.valueOf(100)).intValue()).build();
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
        ResponseResult<QueryPriceResponse> resultPackage = new QueryProductAccess(host, StringUtils.isBlank(request.getLanguage()) ? "zh-CN" : request.getLanguage(), expediaUtils.signGeneration(), ownIp, sessionId, rateLimiter).access(queryPriceRequest);
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
                        ResponseResult<CheckPriceResponse> checkPriceResult = new CheckPriceAccess(host, StringUtils.isBlank(request.getLanguage()) ? "zh-CN" : request.getLanguage(), expediaUtils.signGeneration(), ownIp, sessionId, rateLimiter).access(contractProfile.appendTo(bedCheckInfos.get(0).getCheckHref()));
                        if (!checkPriceResult.isSucc() || null == checkPriceResult.getData() || "sold_out".equals(checkPriceResult.getData().getStatus())) {
                            log.info("expedia验价失败,request:{},response:{}", JsonUtils.writeObject2Json(request), JsonUtils.writeObject2Json(checkPriceResult));
                            return null;
                        }
                        QueryPriceResponse.Occupancy_pricing occupancyPricing = checkPriceResult.getData().getOccupancy_pricing().get(request.getOccupancies().get(0));
                        int sumCommission = calcCommissionCents(occupancyPricing);
                        int totalPrice = new BigDecimal(occupancyPricing.getTotals().getInclusive().getRequest_currency().getValue()).multiply(new BigDecimal("100")).intValue();
                        int roomTotalPrice = new BigDecimal(occupancyPricing.getTotals().getExclusive().getRequest_currency().getValue()).multiply(new BigDecimal("100")).intValue();
                        List<QueryPriceResponse.CancelPolicy> cancelPolicies = rate.getCancel_penalties();
                        Meal meal = productKeyDeriver.convertMeal(request.getAdultNum(), rate.getAmenities());
                        List<CancelPolicy> cancelPolicy = CollectionUtils.isNotEmpty(rate.getNonrefundable_date_ranges()) ? List.of(CancelPolicy.builder().cancelType(0).build()) : productKeyDeriver.convertCancelPolicy(request.getCheckIn(), cancelPolicies);
                        ProductRespDTO productRespDTO = ProductRespDTO.builder().hotelId(hotelPrice.getProperty_id()).productId(rate.getId()).productKey(productKeyDeriver.deriveProductKey(hotelPrice.getProperty_id(), room.getId(), meal, cancelPolicy, request.getOccupancies().get(0))).supplierId(SupplierSourceEnum.EXPEDIA.getCode()).room(Room.builder().roomName(room.getRoom_name()).roomId(room.getId()).build()).productInfo(ProductInfo.builder().inventory(1).productStatus(1).productName(room.getRoom_name()).build()).currencyType(occupancyPricing.getTotals().getInclusive().getRequest_currency().getCurrency()).totalPrice(totalPrice - sumCommission).stayPrice(buildStayPrice(occupancyPricing.getStay())).storePayPrice(null == occupancyPricing.getTotals().getProperty_fees() ? 0 : new BigDecimal(occupancyPricing.getTotals().getProperty_fees().getBillable_currency().getValue()).multiply(new BigDecimal("100")).intValue()).storePayCurrency(null == occupancyPricing.getTotals().getProperty_fees() ? request.getCurrency() : occupancyPricing.getTotals().getProperty_fees().getBillable_currency().getCurrency()).roomTotalPrice(roomTotalPrice - sumCommission).brokerage(sumCommission).priceInfos(buildQueryPriceInfos(occupancyPricing.getNightly(), request.getCheckIn(), sumCommission)).meal(meal).cancelPolicy(cancelPolicy).maxOccupancy(request.getAdultNum()).priceFlag(queryPriceRequest.getSales_environment()).distribution(rate.getSale_scenario().getDistribution()).bedCheckInfos(bedCheckInfos).build();
                        productRespDTO.setTotalTaxes(productRespDTO.getTotalPrice() - productRespDTO.getRoomTotalPrice());
                        return Arrays.asList(productRespDTO);
                    }
                }
            }
        }
        if (isHave) {
            queryPriceRequest.setSales_environment(StringUtils.isBlank(request.getPriceFlag()) ? "hotel_package" : request.getPriceFlag());
            ResponseResult<QueryPriceResponse> onlyResult = new QueryProductAccess(host, StringUtils.isBlank(request.getLanguage()) ? "zh-CN" : request.getLanguage(), expediaUtils.signGeneration(), ownIp, sessionId, rateLimiter).access(queryPriceRequest);
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
                            ResponseResult<CheckPriceResponse> checkPriceResult = new CheckPriceAccess(host, StringUtils.isBlank(request.getLanguage()) ? "zh-CN" : request.getLanguage(), expediaUtils.signGeneration(), ownIp, sessionId, rateLimiter).access(contractProfile.appendTo(bedCheckInfos.get(0).getCheckHref()));
                            if (!checkPriceResult.isSucc() || null == checkPriceResult.getData() || "sold_out".equals(checkPriceResult.getData().getStatus())) {
                                log.info("expedia验价失败,request:{},response:{}", JsonUtils.writeObject2Json(request), JsonUtils.writeObject2Json(checkPriceResult));
                                return null;
                            }
                            QueryPriceResponse.Occupancy_pricing occupancyPricing = checkPriceResult.getData().getOccupancy_pricing().get(request.getOccupancies().get(0));
                            int sumCommission = calcCommissionCents(occupancyPricing);
                            int totalPrice = new BigDecimal(occupancyPricing.getTotals().getInclusive().getRequest_currency().getValue()).multiply(new BigDecimal("100")).intValue();
                            int roomTotalPrice = new BigDecimal(occupancyPricing.getTotals().getExclusive().getRequest_currency().getValue()).multiply(new BigDecimal("100")).intValue();
                            List<QueryPriceResponse.CancelPolicy> cancelPolicies = rate.getCancel_penalties();
                            Meal meal = productKeyDeriver.convertMeal(request.getAdultNum(), rate.getAmenities());
                            List<CancelPolicy> cancelPolicy = CollectionUtils.isNotEmpty(rate.getNonrefundable_date_ranges()) ? List.of(CancelPolicy.builder().cancelType(0).build()) : productKeyDeriver.convertCancelPolicy(request.getCheckIn(), cancelPolicies);
                            ProductRespDTO productRespDTO = ProductRespDTO.builder().hotelId(hotelPrice.getProperty_id()).productId(rate.getId()).productKey(productKeyDeriver.deriveProductKey(hotelPrice.getProperty_id(), room.getId(), meal, cancelPolicy, request.getOccupancies().get(0))).supplierId(SupplierSourceEnum.EXPEDIA.getCode()).room(Room.builder().roomName(room.getRoom_name()).roomId(room.getId()).build()).productInfo(ProductInfo.builder().inventory(1).productStatus(1).productName(room.getRoom_name()).build()).currencyType(occupancyPricing.getTotals().getInclusive().getRequest_currency().getCurrency()).totalPrice(totalPrice - sumCommission).stayPrice(buildStayPrice(occupancyPricing.getStay())).storePayPrice(null == occupancyPricing.getTotals().getProperty_fees() ? 0 : new BigDecimal(occupancyPricing.getTotals().getProperty_fees().getBillable_currency().getValue()).multiply(new BigDecimal("100")).intValue()).storePayCurrency(null == occupancyPricing.getTotals().getProperty_fees() ? request.getCurrency() : occupancyPricing.getTotals().getProperty_fees().getBillable_currency().getCurrency()).roomTotalPrice(roomTotalPrice - sumCommission).brokerage(sumCommission).priceInfos(buildQueryPriceInfos(occupancyPricing.getNightly(), request.getCheckIn(), sumCommission)).meal(meal).cancelPolicy(cancelPolicy).maxOccupancy(request.getAdultNum()).priceFlag(queryPriceRequest.getSales_environment()).distribution(rate.getSale_scenario().getDistribution()).bedCheckInfos(bedCheckInfos).build();
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

    @Override
    public CheckPriceRespDTO checkPrices(CheckPriceReq request) {
        QueryPriceRequest queryPriceRequest = contractProfile.newRequestBuilder().property_id(request.getSHotelId()).checkin(request.getCheckIn()).checkout(request.getCheckOut()).currency(StringUtils.isBlank(request.getCurrency()) ? DEFAULT_QUOTE_CURRENCY : request.getCurrency()).build();
        queryPriceRequest.setOccupancies(buildOccupancies(request));

        // 上游指定了售卖类型就只按它查；未指定时先零售、未命中再打包。
        // 原实现在指定的情况下也会再查一次，而两次的售卖类型完全相同——纯属白打一次供应商接口
        if (StringUtils.isNotBlank(request.getPriceFlag())) {
            return attemptCheckPrice(request, queryPriceRequest, request.getPriceFlag());
        }
        CheckPriceRespDTO retail = attemptCheckPrice(request, queryPriceRequest, SALES_ENV_HOTEL_ONLY);
        if (retail.getOutcome() != CheckPriceOutcome.RATE_DEAD) {
            return retail;
        }
        // 仅当零售侧确证「没有这个产品」时才换打包价再找一次。
        // 若零售侧是不确定或已售罄，再查一次打包价既救不回也会掩盖成因
        return attemptCheckPrice(request, queryPriceRequest, SALES_ENV_HOTEL_PACKAGE);
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

    /**
     * 在指定售卖类型下验一次价，并把结果归入确定的分态。
     *
     * <p><b>本方法存在的意义就是不让这些情形塌成同一个 null。</b>原实现对「查价调用失败」
     * 「所点产品不在响应里」「床型不可选」「已售罄」一律返回 null，上游因此无从区分
     * 该重新查价、该告知满房、还是该稍后重试——而这三件事的处置完全不同。
     */
    private CheckPriceRespDTO attemptCheckPrice(CheckPriceReq request, QueryPriceRequest queryPriceRequest,
                                                String salesEnvironment) {
        queryPriceRequest.setSales_environment(salesEnvironment);
        ResponseResult<QueryPriceResponse> result = new QueryProductAccess(host, StringUtils.isBlank(request.getLanguage()) ? "zh-CN" : request.getLanguage(), expediaUtils.signGeneration(), ownIp, sessionId, rateLimiter).access(queryPriceRequest);
        if (result == null || !result.isSucc() || null == result.getData()
                || CollectionUtils.isEmpty(result.getData().getHotelPrices())) {
            log.warn("expedia查价未取得结果,salesEnvironment={},request:{}", salesEnvironment, JsonUtils.writeObject2Json(request));
            return outcome(CheckPriceOutcome.INDETERMINATE, "查价调用未取得结果，未能确认该产品是否可订，请稍后重试");
        }

        QueryPriceResponse.Rates rate = findRate(result.getData(), request.getSProductId());
        if (rate == null) {
            // rate.id 实测稳定（docs/product-identity.md E-1 修正），走到这里意味着该报价
            // 当日确实不在售（卖法下架/未开售）。先尝试按 productKey 在现货中换等价新票
            // （resolve ②）；换不到才是确定性 RATE_DEAD——拿同一个 sProductId 重试必再
            // 失败。注意这不等于满房——同一房型往往仍有房
            rate = tryResolveByProductKey(result.getData(), request, queryPriceRequest.getOccupancies().get(0));
        }
        if (rate == null) {
            log.info("expedia验价：所点产品已不在当前报价中,salesEnvironment={},sProductId={}",
                    salesEnvironment, request.getSProductId());
            return outcome(CheckPriceOutcome.RATE_DEAD, "该产品已不在供应商当前报价中，请重新查价后再选择");
        }

        QueryPriceResponse.Bed_groups bedGroups = pickBedGroup(rate, request.getBedId());
        if (null == bedGroups) {
            log.info("expedia验价：所选床型已不可选,sProductId={},bedId={}", request.getSProductId(), request.getBedId());
            return outcome(CheckPriceOutcome.RATE_DEAD, "所选床型已不可选，请重新查价后再选择");
        }

        ResponseResult<CheckPriceResponse> checkPriceResult = new CheckPriceAccess(host, StringUtils.isBlank(request.getLanguage()) ? "zh-CN" : request.getLanguage(), expediaUtils.signGeneration(), ownIp, sessionId, rateLimiter).access(contractProfile.appendTo(bedGroups.getLinks().getPrice_check().getHref()));
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
                        : checkPriceResult.getData().getOccupancy_pricing().get(queryPriceRequest.getOccupancies().get(0));
        if (occupancyPricing == null || occupancyPricing.getTotals() == null
                || occupancyPricing.getTotals().getInclusive() == null) {
            // 供应商说可订却没给出本次占用的价格，属响应自相矛盾：既不能报可订（没有价），
            // 也不能报不可订（供应商并未这么说）
            log.error("expedia验价：响应缺少本次占用的价格,sProductId={},occupancy={}",
                    request.getSProductId(), queryPriceRequest.getOccupancies().get(0));
            return outcome(CheckPriceOutcome.INDETERMINATE, "验价响应缺少本次占用的价格，未能确认该产品是否可订");
        }
        return buildCheckPriceResp(checkPriceResult.getData(), occupancyPricing);
    }

    /**
     * 令牌已死时按 productKey 在当前现货中找等价新票（resolve ②，docs/product-identity.md §3）。
     *
     * <p>三个前置缺一即放弃（返回 null，走 RATE_DEAD 正门）：开关开启（默认关，行为与旧实现
     * 完全一致）、上游携带 productKey、上游携带展示价 totalPrice（容差门的基准，R-3.3）。
     *
     * <p>硬门（R-3.2）不必单列：productKey 本身由房型ID+餐食+退改类+占用派生，
     * 对每条现货报价按<b>与查价时完全相同的口径</b>重新派生再比对，键相等即四门全过。
     * 多条命中交给 {@link ResolveGate}：取最低价、过容差门。
     *
     * <p>匹配在已取回的查价响应上进行，不追加供应商调用——天然在时间预算内（R-3.4）。
     */
    QueryPriceResponse.Rates tryResolveByProductKey(QueryPriceResponse data, CheckPriceReq request, String occupancy) {
        if (StringUtils.isBlank(request.getProductKey())) {
            return null;
        }
        if (rapidProperties == null || !rapidProperties.isResolveEnabled()) {
            // §3.8.4：上游明确请求了换票（带 productKey）而被闸口拒绝，必须可检索
            log.info("闸口 supplier.expedia.resolve-enabled 关闭，拒绝按 productKey 自动换票,sHotelId={},sProductId={}",
                    request.getSHotelId(), request.getSProductId());
            return null;
        }
        QueryPriceResponse.HotelPrice hotelPrice = data.getHotelPrices().get(0);
        if (hotelPrice.getRooms() == null) {
            return null;
        }
        List<ResolveCandidate> equivalents = new ArrayList<>();
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
                // 上游口径总价（分）：含税价 − 佣金，与查价响应透出的 totalPrice 同一算法
                int priceCents = new BigDecimal(pricing.getTotals().getInclusive().getRequest_currency().getValue())
                        .multiply(new BigDecimal("100")).intValue() - calcCommissionCents(pricing);
                equivalents.add(new ResolveCandidate(candidate, priceCents));
            }
        }
        return ResolveGate.pickCheapestWithinTolerance(equivalents, ResolveCandidate::priceCents,
                        request.getSeenPrice(), rapidProperties.getResolvePriceTolerance(),
                        rapidProperties.getResolvePriceCapCents())
                .map(chosen -> {
                    log.info("expedia验价：令牌已死，按productKey换票成功,原sProductId={},新rateId={},新价={}分,展示价={}分",
                            request.getSProductId(), chosen.rate().getId(), chosen.priceCents(), request.getSeenPrice());
                    return chosen.rate();
                })
                .orElseGet(() -> {
                    // 未救回的两种成因必须可区分：排障时"没等价票"该查建档/键口径，"价格不合"该查容差参数
                    if (equivalents.isEmpty()) {
                        log.info("expedia验价：resolve 未命中——现货中无同卖法等价报价,sHotelId={},sProductId={},productKey={}",
                                request.getSHotelId(), request.getSProductId(), request.getProductKey());
                    } else {
                        log.info("expedia验价：存在等价报价但超出容差，拒绝自动换票,sProductId={},展示价={}分,候选最低={}分",
                                request.getSProductId(), request.getSeenPrice(),
                                equivalents.stream().mapToInt(ResolveCandidate::priceCents).min().orElse(-1));
                    }
                    return null;
                });
    }

    /** resolve 候选：已过硬门（productKey 相等）的现货报价及其上游口径价格 */
    private record ResolveCandidate(QueryPriceResponse.Rates rate, int priceCents) {
    }

    /** 在查价响应中找出所点的报价；找不到返回 null */
    QueryPriceResponse.Rates findRate(QueryPriceResponse data, String sProductId) {
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
        ResponseResult<QueryPriceResponse> resultOnly = new QueryProductAccess(host, "zh-CN", expediaUtils.signGeneration(), ownIp, sessionId, rateLimiter).access(queryPriceRequest);
        Monitor.recordOne("pricing_supplier_query", pricingTags("all"));

        if (null == resultOnly || !resultOnly.isSucc() || null == resultOnly.getData()
                || CollectionUtils.isEmpty(resultOnly.getData().getHotelPrices())) {
            log.info("expedia缓存查询零售价失败,request:{}", JsonUtils.writeObject2Json(queryPriceRequest));
            return null;
        }
        List<ProductRespDTO> productRespDTOList = convertPriceResp(resultOnly.getData().getHotelPrices().get(0), "hotel_only", request);

        //插入缓存
        cachePriceService.productToCache(productRespDTOList, request);
        return productRespDTOList;
    }

    /**
     * 出价指标的维度。supplier 与成败都必须进 tag，不进名字（§3.9.2）——
     * 原先是 {@code expedia_all_query} / {@code _fail} / {@code _success} 三个名字，
     * 接第二家供应商就要再造三个。
     */
    private static Map<String, Object> pricingTags(String outcome) {
        Map<String, Object> tags = new HashMap<>(2);
        tags.put("supplier", "expedia");
        tags.put("outcome", outcome);
        return tags;
    }
}
