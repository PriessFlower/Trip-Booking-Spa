package com.trip.booking.spa.core.api.expedia.service.impl;

import com.trip.booking.spa.core.api.dto.BedCheckInfo;
import com.trip.booking.spa.core.api.dto.CancelPolicy;
import com.trip.booking.spa.core.api.dto.CheckPriceRespDTO;
import com.trip.booking.spa.core.api.dto.Meal;
import com.trip.booking.spa.core.api.dto.PriceInfo;
import com.trip.booking.spa.core.api.dto.ProductInfo;
import com.trip.booking.spa.core.api.dto.ProductRespDTO;
import com.trip.booking.spa.core.api.dto.Room;
import com.trip.booking.spa.core.api.common.enums.RefundType;
import com.trip.booking.spa.core.api.request.CheckPriceReq;
import com.trip.booking.spa.core.api.request.PriceReq;
import com.trip.booking.spa.core.api.request.Supplier;
import com.trip.booking.spa.core.api.common.asynchttp.ResponseResult;
import com.trip.booking.spa.core.api.common.enums.CheckPriceOutcome;
import com.trip.booking.spa.core.api.common.enums.SupplierSourceEnum;
import com.trip.booking.spa.core.api.common.offer.OfferStore;
import com.trip.booking.spa.core.api.expedia.access.CheckPriceAccess;
import com.trip.booking.spa.core.api.expedia.access.QueryProductAccess;
import com.trip.booking.spa.core.api.expedia.bean.request.QueryPriceRequest;
import com.trip.booking.spa.core.api.expedia.bean.response.CheckPriceResponse;
import com.trip.booking.spa.core.api.expedia.bean.response.QueryPriceResponse;
import com.trip.booking.spa.core.api.expedia.config.ExpediaContractProfile;
import com.trip.booking.spa.core.api.expedia.service.ExpediaOfferCredentials;
import com.trip.booking.spa.core.api.expedia.service.ExpediaPriceService;
import com.trip.booking.spa.core.api.expedia.utils.ExpediaUtils;
import com.trip.booking.spa.core.api.service.CachePriceService;
import com.trip.booking.spa.core.monitor.Monitor;
import com.trip.booking.spa.core.redis.DistributedRateLimiter;
import com.trip.booking.spa.core.util.DateUtil;
import com.trip.booking.spa.core.util.JsonUtils;
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
    private final static String mealList = "1073742857,21022103,2104,2105,2205,1073742786,1073744734,1073744735,2106,2107,2193,2194,2203,2206,2207,1073744459";

    @Autowired
    private CachePriceService cachePriceService;

    @Override
    public List<ProductRespDTO> queryPrices(PriceReq request, Supplier supplier) {
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
        Monitor.recordOne("expedia_all_query");
        if (null == hotelPriceOnly && null == hotelPricePackage) {
            log.info("expedia查询零售价和打包价全部失败,request:{},response:{}", JsonUtils.writeObject2Json(queryPriceRequest), JsonUtils.writeObject2Json(resultOnly));
            Monitor.recordOne("expedia_all_query_fail");
            return null;
        }
        Monitor.recordOne("expedia_all_query_success");
        // 零售价(hotel_only)与打包价(hotel_package)是两类不同产品，规则上不可混卖，
        // 各自独立成品返回、各带自己的 priceFlag，不做比价合并
        return convertSeparated(hotelPriceOnly, hotelPricePackage, request);
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
            ProductRespDTO productRespDTO = ProductRespDTO.builder().hotelId(hotelId).productId(rate.getId()).supplierId(SupplierSourceEnum.EXPEDIA.getCode()).room(Room.builder().roomName(roomName).roomId(roomId).build()).productInfo(ProductInfo.builder().inventory(1).productStatus(1).productName(roomName).build()).currencyType(occupancyPricing.getTotals().getInclusive().getRequest_currency().getCurrency()).totalPrice(totalPrice - sumCommission).roomTotalPrice(roomTotalPrice - sumCommission).brokerage(sumCommission).stayPrice(buildStayPrice(occupancyPricing.getStay())).priceInfos(buildQueryPriceInfos(occupancyPricing.getNightly(), request.getCheckIn(), sumCommission)).meal(convertMeal(request.getAdultNum(), rate.getAmenities())).cancelPolicy(CollectionUtils.isNotEmpty(rate.getNonrefundable_date_ranges()) ? List.of(CancelPolicy.builder().cancelType(0).build()) : convertCancelPolicy(request.getCheckIn(), cancelPolicies)).maxOccupancy(request.getAdultNum()).priceFlag(salesType).distribution(rate.getSale_scenario().getDistribution()).build();
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

    public List<PriceInfo> buildQueryPriceInfos(List<List<QueryPriceResponse.Nightly>> nightlyLists, String checkIn, int sumCommission) {
        List<PriceInfo> priceInfos = Lists.newArrayList();
        int commission = sumCommission / nightlyLists.size();
        for (int i = 0; i < nightlyLists.size(); i++) {
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
                        ProductRespDTO productRespDTO = ProductRespDTO.builder().hotelId(hotelPrice.getProperty_id()).productId(rate.getId()).supplierId(SupplierSourceEnum.EXPEDIA.getCode()).room(Room.builder().roomName(room.getRoom_name()).roomId(room.getId()).build()).productInfo(ProductInfo.builder().inventory(1).productStatus(1).productName(room.getRoom_name()).build()).currencyType(occupancyPricing.getTotals().getInclusive().getRequest_currency().getCurrency()).totalPrice(totalPrice - sumCommission).stayPrice(buildStayPrice(occupancyPricing.getStay())).storePayPrice(null == occupancyPricing.getTotals().getProperty_fees() ? 0 : new BigDecimal(occupancyPricing.getTotals().getProperty_fees().getBillable_currency().getValue()).multiply(new BigDecimal("100")).intValue()).storePayCurrency(null == occupancyPricing.getTotals().getProperty_fees() ? request.getCurrency() : occupancyPricing.getTotals().getProperty_fees().getBillable_currency().getCurrency()).roomTotalPrice(roomTotalPrice - sumCommission).brokerage(sumCommission).priceInfos(buildQueryPriceInfos(occupancyPricing.getNightly(), request.getCheckIn(), sumCommission)).meal(convertMeal(request.getAdultNum(), rate.getAmenities())).cancelPolicy(CollectionUtils.isNotEmpty(rate.getNonrefundable_date_ranges()) ? List.of(CancelPolicy.builder().cancelType(0).build()) : convertCancelPolicy(request.getCheckIn(), cancelPolicies)).maxOccupancy(request.getAdultNum()).priceFlag(queryPriceRequest.getSales_environment()).distribution(rate.getSale_scenario().getDistribution()).bedCheckInfos(bedCheckInfos).build();
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
                            ProductRespDTO productRespDTO = ProductRespDTO.builder().hotelId(hotelPrice.getProperty_id()).productId(rate.getId()).supplierId(SupplierSourceEnum.EXPEDIA.getCode()).room(Room.builder().roomName(room.getRoom_name()).roomId(room.getId()).build()).productInfo(ProductInfo.builder().inventory(1).productStatus(1).productName(room.getRoom_name()).build()).currencyType(occupancyPricing.getTotals().getInclusive().getRequest_currency().getCurrency()).totalPrice(totalPrice - sumCommission).stayPrice(buildStayPrice(occupancyPricing.getStay())).storePayPrice(null == occupancyPricing.getTotals().getProperty_fees() ? 0 : new BigDecimal(occupancyPricing.getTotals().getProperty_fees().getBillable_currency().getValue()).multiply(new BigDecimal("100")).intValue()).storePayCurrency(null == occupancyPricing.getTotals().getProperty_fees() ? request.getCurrency() : occupancyPricing.getTotals().getProperty_fees().getBillable_currency().getCurrency()).roomTotalPrice(roomTotalPrice - sumCommission).brokerage(sumCommission).priceInfos(buildQueryPriceInfos(occupancyPricing.getNightly(), request.getCheckIn(), sumCommission)).meal(convertMeal(request.getAdultNum(), rate.getAmenities())).cancelPolicy(CollectionUtils.isNotEmpty(rate.getNonrefundable_date_ranges()) ? List.of(CancelPolicy.builder().cancelType(0).build()) : convertCancelPolicy(request.getCheckIn(), cancelPolicies)).maxOccupancy(request.getAdultNum()).priceFlag(queryPriceRequest.getSales_environment()).distribution(rate.getSale_scenario().getDistribution()).bedCheckInfos(bedCheckInfos).build();
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
            // Expedia 每次查价重铸 rate.id，且产品本身也可能已下架。无论哪种，
            // 拿同一个 sProductId 重试必再失败，故是确定性结果而非「不确定」。
            // 注意这不等于满房——同一房型往往仍有房，只是那份报价换代了
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
        Monitor.recordOne("expedia_all_query");

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

    public List<PriceInfo> buildCheckPriceInfos(List<List<QueryPriceResponse.Nightly>> nightlyLists, String checkIn) {
        List<PriceInfo> priceInfos = Lists.newArrayList();
        for (int i = 0; i < nightlyLists.size(); i++) {
            BigDecimal sumPrice = BigDecimal.ZERO; // 初始化累加器为0
            for (QueryPriceResponse.Nightly nightly : nightlyLists.get(i)) {
                sumPrice = sumPrice.add(new BigDecimal(nightly.getValue()));
            }
            PriceInfo priceInfo = PriceInfo.builder().date(DateUtil.getFutureDay(checkIn, i)).price(sumPrice.multiply(BigDecimal.valueOf(100)).intValue()).build();
            priceInfos.add(priceInfo);
        }
        return priceInfos;
    }

    public Meal convertMeal(Integer adultNum, Map<String, QueryPriceResponse.Amenity> amenities) {
        // 部分 rate 不下发 amenities（实测 2342 行刷价中 30 次），视为无餐食。
        // 取值必须与下方 default 分支一致：count 为 0 而非 null，否则缓存复用时
        // CachePriceServiceImpl 的 meal.count.equals(...) 比较会空指针。
        if (null == amenities) {
            return Meal.builder().count(0).lunchCount(0).dinnerCount(0).mealDesc("").build();
        }
        String[] meals = mealList.split(",");
        String mealId = "";
        for (String meal : meals) {
            if (amenities.containsKey(meal)) {
                mealId = meal;
            }
        }
        Meal meal = new Meal();
        switch (mealId) {
            case "1073742857": //单早
                meal = Meal.builder().count(1).lunchCount(0).dinnerCount(0).mealDesc(amenities.get(mealId).getName()).build();
                break;
            case "2102":  //三餐（早+中+晚）
            case "2207":  //全包
                meal = Meal.builder().count(adultNum).lunchCount(adultNum).dinnerCount(adultNum).mealDesc(amenities.get(mealId).getName()).build();
                break;
            case "2103":
            case "2104":
            case "2105":
            case "2205":
            case "1073742786":
            case "1073744734":
            case "1073744735":  //免费早餐（份数=入住人数）
            case "1073744459":  //咖啡面包形式的早餐
                meal = Meal.builder().count(adultNum).lunchCount(0).dinnerCount(0).mealDesc(amenities.get(mealId).getName()).build();
                break;
            case "2106":  //免费午餐
                meal = Meal.builder().count(0).lunchCount(adultNum).dinnerCount(0).mealDesc(amenities.get(mealId).getName()).build();
                break;
            case "2107":  //免费晚餐
                meal = Meal.builder().count(0).lunchCount(0).dinnerCount(adultNum).mealDesc(amenities.get(mealId).getName()).build();
                break;
            case "2193":
            case "2194":  //双早（当入住人数=1时，只有一份）
                meal = Meal.builder().count(Math.min(2, adultNum)).lunchCount(0).dinnerCount(0).mealDesc(amenities.get(mealId).getName()).build();
                break;
            case "2206":  //半包
                meal = Meal.builder().count(adultNum).lunchCount(0).dinnerCount(adultNum).mealDesc(amenities.get(mealId).getName()).build();
                break;
            default:
                meal = Meal.builder().count(0).lunchCount(0).dinnerCount(0).mealDesc("").build();
        }
        return meal;
    }

    public List<CancelPolicy> convertCancelPolicy(String checkIn, List<QueryPriceResponse.CancelPolicy> cancelPolicies) {
        List<CancelPolicy> cancelPolicyList = new ArrayList<>();

        QueryPriceResponse.CancelPolicy cancelPolicy = null;
        if (CollectionUtils.isEmpty(cancelPolicies)) {
            cancelPolicyList.add(CancelPolicy.builder().cancelType(0).build());
            return cancelPolicyList;
        }
        cancelPolicy = cancelPolicies.stream().min(Comparator.comparing(QueryPriceResponse.CancelPolicy::getStart)).get();
        // 创建SimpleDateFormat对象，并设置日期时间模式
//        SimpleDateFormat sdfTime = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
//        sdfTime.setTimeZone(TimeZone.getTimeZone("GMT"));
        SimpleDateFormat sdfTime = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
        SimpleDateFormat sdfDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        int beforeEnd = 0;
        int beforeStart = 0;
        try {
            beforeEnd = DateUtil.diffHour(sdfTime.parse(cancelPolicy.getEnd()), sdfDate.parse(checkIn + " 24:00:00"));
            beforeStart = DateUtil.diffHour(sdfTime.parse(cancelPolicy.getStart()), sdfDate.parse(checkIn + " 24:00:00"));
        } catch (Exception e) {
            log.info("时间转换校验异常", e);
        }
        if (StringUtils.isNotBlank(cancelPolicy.getAmount())) {
            cancelPolicyList.add(CancelPolicy.builder().cancelType(1).timeZone(subDateGMT(cancelPolicy.getStart())).before(Math.max(25, beforeStart)).type(RefundType.NO_DEDUCTION).build());
            if (beforeStart > 25) {
                cancelPolicyList.add(CancelPolicy.builder().cancelType(1).timeZone(subDateGMT(cancelPolicy.getEnd())).before(beforeEnd).type(RefundType.DEDUCT_BY_AMOUNT).value(Double.valueOf(cancelPolicy.getAmount())).build());
            }
        } else if (StringUtils.isNotBlank(cancelPolicy.getPercent())) {
            if ("100%".equals(cancelPolicy.getPercent())) {
                cancelPolicyList.add(CancelPolicy.builder().cancelType(1).timeZone(subDateGMT(cancelPolicy.getStart())).before(Math.max(25, beforeStart)).type(RefundType.NO_DEDUCTION).build());
            } else {
                cancelPolicyList.add(CancelPolicy.builder().cancelType(1).timeZone(subDateGMT(cancelPolicy.getStart())).before(Math.max(25, beforeStart)).type(RefundType.NO_DEDUCTION).build());
                if (beforeStart > 25) {
                    cancelPolicyList.add(CancelPolicy.builder().cancelType(1).timeZone(subDateGMT(cancelPolicy.getEnd())).before(Math.max(25, beforeEnd)).type(RefundType.DEDUCT_BY_PERCENT).value(Double.valueOf(cancelPolicy.getPercent().replace("%", ""))).build());
                }
            }
        } else if (StringUtils.isNotBlank(cancelPolicy.getNights())) {
            if ("0".equals(cancelPolicy.getNights())) {
                cancelPolicyList.add(CancelPolicy.builder().cancelType(1).timeZone(subDateGMT(cancelPolicy.getEnd())).before(Math.max(25, beforeEnd)).type(RefundType.NO_DEDUCTION).build());
            } else {
                cancelPolicyList.add(CancelPolicy.builder().cancelType(1).timeZone(subDateGMT(cancelPolicy.getStart())).before(Math.max(25, beforeStart)).type(RefundType.NO_DEDUCTION).build());
                if (beforeStart > 25) {
                    cancelPolicyList.add(CancelPolicy.builder().cancelType(1).timeZone(subDateGMT(cancelPolicy.getEnd())).before(Math.max(25, beforeEnd)).type(RefundType.DEDUCT_DAY_NIGHT).value(Double.valueOf(cancelPolicy.getNights())).build());
                }
            }
        } else {
            cancelPolicyList.add(CancelPolicy.builder().cancelType(0).build());
        }
        return cancelPolicyList;
    }

    private static String subDateGMT(String cancelDate) {
        return "GMT" + cancelDate.substring(cancelDate.length() - 6, cancelDate.length() - 3);
    }

//    public static void main(String[] args) {
////        // 创建一个LocalDate对象表示日期
////        LocalDate date = LocalDate.of(2023, 10, 15); // 这里你可以用你想要查询的日期替换它
////        // 使用一个明确的日期来构建LocalDateTime
////        LocalDateTime localDateTime = LocalDateTime.of(date, LocalTime.now()); // LocalTime也可以指定为具体的本地时间
////
////        // 获取GMT时区
////        ZoneId gmtZoneId = ZoneId.of("GMT");
////        // 将本地日期时间转换为ZonedDateTime并设置到GMT时区
////        ZonedDateTime gmtDateTime = ZonedDateTime.of(localDateTime, gmtZoneId);
////
////        // 打印结果，查看这个日期在GMT时区的时间
////        System.out.println("Zoned DateTime in GMT: " + gmtDateTime);
//
//
//        String str = "{\"start\":\"2024-10-26T10:00:00.000-07:00\",\"end\":\"2024-10-28T10:00:00.000-07:00\",\"percent\":\"10%\",\"currency\":\"CNY\"}";
//        QueryPriceResponse.CancelPolicy cancelPolicy = new QueryPriceResponse.CancelPolicy();
//        convertCancelPolicy("2024-10-28", Arrays.asList(JsonUtils.readValue(str, QueryPriceResponse.CancelPolicy.class)));
//    }


}
