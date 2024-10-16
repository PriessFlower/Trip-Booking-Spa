package com.bingo.hotel.spa.intl.core.api.expedia.service.impl;

import com.bingo.hotel.spa.intl.cli.dto.BedCheckInfo;
import com.bingo.hotel.spa.intl.cli.dto.CancelPolicy;
import com.bingo.hotel.spa.intl.cli.dto.Meal;
import com.bingo.hotel.spa.intl.cli.dto.PriceInfo;
import com.bingo.hotel.spa.intl.cli.dto.ProductInfo;
import com.bingo.hotel.spa.intl.cli.dto.ProductRespDTO;
import com.bingo.hotel.spa.intl.cli.enums.RefundType;
import com.bingo.hotel.spa.intl.cli.seq.CheckPriceReq;
import com.bingo.hotel.spa.intl.cli.seq.PriceReq;
import com.bingo.hotel.spa.intl.cli.seq.Supplier;
import com.bingo.hotel.spa.intl.core.api.common.asynchttp.ResponseResult;
import com.bingo.hotel.spa.intl.core.api.common.enums.SupplierSourceEnum;
import com.bingo.hotel.spa.intl.core.api.expedia.access.CheckPriceAccess;
import com.bingo.hotel.spa.intl.core.api.expedia.access.QueryProductAccess;
import com.bingo.hotel.spa.intl.core.api.expedia.bean.request.QueryPriceRequest;
import com.bingo.hotel.spa.intl.core.api.expedia.bean.response.CheckPriceResponse;
import com.bingo.hotel.spa.intl.core.api.expedia.bean.response.QueryPriceResponse;
import com.bingo.hotel.spa.intl.core.api.expedia.service.ExpediaPriceService;
import com.bingo.hotel.spa.intl.core.api.expedia.utils.ExpediaUtils;
import com.bingo.hotel.spa.intl.core.redis.DistributedRateLimiter;
import com.bingo.hotel.spa.intl.core.util.DateUtil;
import com.bingo.hotel.spa.intl.core.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.compress.utils.Lists;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.stream.Collectors;


@Service
@Slf4j
public class ExpediaPriceServiceImpl implements ExpediaPriceService {

    @Value("${expedia.url.host}")
    String host;
    @Value("${expedia.session}")
    String sessionId;
    @Value("${expedia.ownIp}")
    String ownIp;
    @Value("${expedia.partner_point_of_sale}")
    private String partnerPointOfSale;
    @Value("${expedia.payment_terms}")
    private String paymentTerms;
    @Value("${expedia.billing_terms}")
    private String billingTerms;
    @Resource
    private ExpediaUtils expediaUtils;
    @Resource
    private DistributedRateLimiter rateLimiter;
    private final static String mealList = "1073742857,21022103,2104,2105,2205,1073742786,1073744734,1073744735,2106,2107,2193,2194,2203,2206,2207,1073744459";


    @Override
    public List<ProductRespDTO> queryPrices(PriceReq request, Supplier supplier) {
        ResponseResult<QueryPriceResponse> resultOnly = null;
        ResponseResult<QueryPriceResponse> resultPackage = null;
        QueryPriceResponse.HotelPrice hotelPriceOnly = null;
        QueryPriceResponse.HotelPrice hotelPricePackage = null;

        QueryPriceRequest queryPriceRequest = QueryPriceRequest.builder()
                .property_id(supplier.getSHotelId())
                .checkin(request.getCheckIn())
                .checkout(request.getCheckout())
                .currency("USD")
                .sales_environment("hotel_only")
                .billing_terms(billingTerms)
                .payment_terms(paymentTerms)
                .partner_point_of_sale(partnerPointOfSale)
                .build();
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
        if (null == hotelPriceOnly && null == hotelPricePackage) {
            log.info("expedia查询零售价失败,request:{},response:{}", JsonUtils.writeObject2Json(queryPriceRequest), JsonUtils.writeObject2Json(resultOnly));
            log.info("expedia查询打包价失败,request:{},response:{}", JsonUtils.writeObject2Json(queryPriceRequest), JsonUtils.writeObject2Json(resultPackage));
            return null;
        } else if (null == hotelPriceOnly && null != hotelPricePackage) {
            log.info("expedia查询零售价失败,request:{},response:{}", JsonUtils.writeObject2Json(queryPriceRequest), JsonUtils.writeObject2Json(resultOnly));
            return convertPriceResp(hotelPricePackage, "hotel_package", request);
        } else if (null == hotelPricePackage && null != hotelPriceOnly) {
            log.info("expedia查询打包价失败,request:{},response:{}", JsonUtils.writeObject2Json(queryPriceRequest), JsonUtils.writeObject2Json(resultPackage));
            return convertPriceResp(hotelPriceOnly, "hotel_only", request);
        } else {
            return convertPriceComparisonsResp(hotelPriceOnly, hotelPricePackage, request);
        }
    }

//    private void reqExpediaQueryPrice(PriceReq request, QueryPriceRequest queryPriceRequest, ResponseResult<QueryPriceResponse> resultOnly,
//                                      ResponseResult<QueryPriceResponse> resultPackage, String language) {
//        if ("hotel_only".equals(request.getSalesType())) {
//            //先查询零售价
//            queryPriceRequest.setSales_environment("hotel_only");
//            resultOnly = new QueryProductAccess(host, language, expediaUtils.signGeneration(), ownIp, sessionId, rateLimiter).access(queryPriceRequest);
//        } else if ("hotel_package".equals(request.getSalesType())) {
//            //查询打包价
//            queryPriceRequest.setSales_environment("hotel_package");
//            resultPackage = new QueryProductAccess(host, language, expediaUtils.signGeneration(), ownIp, sessionId, rateLimiter).access(queryPriceRequest);
//        } else {
//            //先查询零售价
//            queryPriceRequest.setSales_environment("hotel_only");
//            resultOnly = new QueryProductAccess(host, language, expediaUtils.signGeneration(), ownIp, sessionId, rateLimiter).access(queryPriceRequest);
//            //查询打包价
//            queryPriceRequest.setSales_environment("hotel_package");
//            resultPackage = new QueryProductAccess(host, language, expediaUtils.signGeneration(), ownIp, sessionId, rateLimiter).access(queryPriceRequest);
//        }
//    }

    private List<ProductRespDTO> convertPriceResp(QueryPriceResponse.HotelPrice hotelPrice, String salesType, PriceReq request) {
        List<ProductRespDTO> productRespDTOS = new ArrayList<>();
        hotelPrice.getRooms().forEach(room -> {
            convertRoomResp(hotelPrice.getProperty_id(), room, salesType, productRespDTOS, request);
        });
        return productRespDTOS;
    }

    private List<ProductRespDTO> convertPriceComparisonsResp(QueryPriceResponse.HotelPrice hotelPriceOnly, QueryPriceResponse.HotelPrice hotelPricePackage, PriceReq request) {
        List<ProductRespDTO> productRespDTOS = new ArrayList<>();
        Set<String> roomIdList = new HashSet<>();
        Map<String, QueryPriceResponse.Rooms> roomOnlyMap = hotelPriceOnly.getRooms().stream().collect(Collectors.toMap(QueryPriceResponse.Rooms::getId,
                room -> room));
        Map<String, QueryPriceResponse.Rooms> roomPackageMap = hotelPricePackage.getRooms().stream().collect(Collectors.toMap(QueryPriceResponse.Rooms::getId,
                room -> room));
        roomIdList.addAll(roomOnlyMap.keySet());
        roomIdList.addAll(roomPackageMap.keySet());
        roomIdList.forEach(roomId -> {
            if (roomOnlyMap.containsKey(roomId) && roomPackageMap.containsKey(roomId)) {
                QueryPriceResponse.Rooms roomOnly = roomOnlyMap.get(roomId);
                QueryPriceResponse.Rooms roomPackage = roomPackageMap.get(roomId);
                Map<String, QueryPriceResponse.Rates> rateOnlyMap = new HashMap<>();
                Map<String, QueryPriceResponse.Rates> ratePackageMap = new HashMap<>();
                Set<String> rateIdList = new HashSet<>();
                if (CollectionUtils.isNotEmpty(roomOnly.getRates()) && CollectionUtils.isNotEmpty(roomPackage.getRates())) {
                    rateOnlyMap = roomOnly.getRates().stream().collect(Collectors.toMap(QueryPriceResponse.Rates::getId, rate -> rate));
                    rateIdList.addAll(rateOnlyMap.keySet());
                    ratePackageMap = roomPackage.getRates().stream().collect(Collectors.toMap(QueryPriceResponse.Rates::getId, rate -> rate));
                    rateIdList.addAll(ratePackageMap.keySet());
                }
                for (String rateId : rateIdList) {
                    if (rateOnlyMap.containsKey(rateId) && ratePackageMap.containsKey(rateId)) {
                        Integer onlyPrice = 0;
                        Integer packagePrice = 0;
                        QueryPriceResponse.Rates rateOnly = rateOnlyMap.get(rateId);
                        if (rateOnly.getOccupancy_pricing().containsKey(request.getAdultNum().toString())) {
                            QueryPriceResponse.Occupancy_pricing occupancyPricingOnly = rateOnly.getOccupancy_pricing().get(request.getAdultNum().toString());
                            onlyPrice =
                                    new BigDecimal(occupancyPricingOnly.getTotals().getInclusive().getRequest_currency().getValue()).multiply(new BigDecimal("100")).intValue();
                        }
                        QueryPriceResponse.Rates ratePackage = ratePackageMap.get(rateId);
                        if (ratePackage.getOccupancy_pricing().containsKey(request.getAdultNum().toString())) {
                            QueryPriceResponse.Occupancy_pricing occupancyPricingPackage =
                                    ratePackage.getOccupancy_pricing().get(request.getAdultNum().toString());
                            packagePrice =
                                    new BigDecimal(occupancyPricingPackage.getTotals().getInclusive().getRequest_currency().getValue()).multiply(new BigDecimal("100")).intValue();
                        }
                        convertRateResp(hotelPriceOnly.getProperty_id(), roomOnly.getRoom_name(), packagePrice < onlyPrice ? ratePackage : rateOnly,
                                packagePrice < onlyPrice ? "hotel_package" : "hotel_only", productRespDTOS, request);
                    } else if (rateOnlyMap.containsKey(rateId)) {
                        convertRateResp(hotelPriceOnly.getProperty_id(), roomOnly.getRoom_name(), rateOnlyMap.get(rateId), "hotel_only", productRespDTOS, request);
                    } else if (ratePackageMap.containsKey(rateId)) {
                        convertRateResp(hotelPricePackage.getProperty_id(), roomPackage.getRoom_name(), ratePackageMap.get(rateId), "hotel_package", productRespDTOS, request);
                    }
                }
            } else if (roomOnlyMap.containsKey(roomId)) {
                convertRoomResp(hotelPriceOnly.getProperty_id(), roomOnlyMap.get(roomId), "hotel_only", productRespDTOS, request);
            } else if (roomPackageMap.containsKey(roomId)) {
                convertRoomResp(hotelPricePackage.getProperty_id(), roomPackageMap.get(roomId), "hotel_package", productRespDTOS, request);
            }
        });
        return productRespDTOS;
    }

    private void convertRoomResp(String hotelId, QueryPriceResponse.Rooms room, String salesType, List<ProductRespDTO> productRespDTOS, PriceReq request) {
        if (CollectionUtils.isNotEmpty(room.getRates())) {
            for (QueryPriceResponse.Rates rate : room.getRates()) {
                convertRateResp(hotelId, room.getRoom_name(), rate, salesType, productRespDTOS, request);
            }
        }
    }

    private void convertRateResp(String hotelId, String roomName, QueryPriceResponse.Rates rate, String salesType, List<ProductRespDTO> productRespDTOS,
                                 PriceReq request) {
        if (rate.getOccupancy_pricing().containsKey(request.getAdultNum().toString())) {
            QueryPriceResponse.Occupancy_pricing occupancyPricing = rate.getOccupancy_pricing().get(request.getAdultNum().toString());
            List<QueryPriceResponse.CancelPolicy> cancelPolicies = rate.getCancel_penalties();

            ProductRespDTO productRespDTO = ProductRespDTO.builder()
                    .hotelId(hotelId)
                    .productId(rate.getId())
                    .supplierId(SupplierSourceEnum.EXPEDIA.getCode())
                    .productInfo(ProductInfo.builder().inventory(1).productStatus(1).productName(roomName).build())
                    .currencyType(occupancyPricing.getTotals().getInclusive().getRequest_currency().getCurrency())
                    .totalPrice(new BigDecimal(occupancyPricing.getTotals().getInclusive().getRequest_currency().getValue()).multiply(new BigDecimal("100")).intValue())
                    .roomTotalPrice(new BigDecimal(occupancyPricing.getTotals().getExclusive().getRequest_currency().getValue()).multiply(new BigDecimal("100")).intValue())
                    .stayPrice(buildStayPrice(occupancyPricing.getStay()))
                    .priceInfos(buildQueryPriceInfos(occupancyPricing.getNightly(), request.getCheckIn()))
                    .meal(convertMeal(request.getAdultNum(), rate.getAmenities()))
                    .cancelPolicy(CollectionUtils.isNotEmpty(rate.getNonrefundable_date_ranges()) ? List.of(CancelPolicy.builder().cancelType(0).build()) :
                            convertCancelPolicy(request.getCheckIn(), cancelPolicies))
                    .maxOccupancy(request.getAdultNum())
                    .priceFlag(salesType)
                    .distribution(rate.getSale_scenario().getDistribution())
                    .build();
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

    public List<PriceInfo> buildQueryPriceInfos(List<List<QueryPriceResponse.Nightly>> nightlyLists, String checkIn) {
        List<PriceInfo> priceInfos = Lists.newArrayList();
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
            PriceInfo priceInfo = PriceInfo.builder()
                    .date(DateUtil.getFutureDay(checkIn, i))
                    .price(sumPrice.multiply(BigDecimal.valueOf(100)).intValue())
                    .roomPrice(roomPrice.multiply(BigDecimal.valueOf(100)).intValue())
                    .taxes(taxes.multiply(BigDecimal.valueOf(100)).intValue())
                    .build();
            priceInfos.add(priceInfo);
        }
        return priceInfos;
    }

    @Override
    public List<ProductRespDTO> queryProductPrice(PriceReq request, Supplier supplier) {

        QueryPriceRequest queryPriceRequest = QueryPriceRequest.builder()
                .property_id(supplier.getSHotelId())
                .checkin(request.getCheckIn())
                .checkout(request.getCheckout())
                .currency("USD")
                .billing_terms(billingTerms)
                .payment_terms(paymentTerms)
                .partner_point_of_sale(partnerPointOfSale)
                .build();
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
        queryPriceRequest.setSales_environment(StringUtils.isBlank(request.getPriceFlag()) ? "hotel_package" : request.getPriceFlag());
        ResponseResult<QueryPriceResponse> result = new QueryProductAccess(host, StringUtils.isBlank(request.getLanguage()) ? "zh-CN" : request.getLanguage(),
                expediaUtils.signGeneration(), ownIp, sessionId, rateLimiter).access(queryPriceRequest);
        if (result != null && result.isSucc() && null != result.getData() && CollectionUtils.isNotEmpty(result.getData().getHotelPrices())) {
            QueryPriceResponse.HotelPrice hotelPrice = result.getData().getHotelPrices().get(0);
            for (QueryPriceResponse.Rooms room : hotelPrice.getRooms()) {
                for (QueryPriceResponse.Rates rate : room.getRates()) {
                    if (supplier.getSProductId().equals(rate.getId())) {
                        ArrayList<BedCheckInfo> bedCheckInfos = new ArrayList<>();
                        for (String bedId : rate.getBed_groups().keySet()) {
                            QueryPriceResponse.Bed_groups bedGroups = rate.getBed_groups().get(bedId);
                            bedCheckInfos.add(BedCheckInfo.builder()
                                    .bedId(bedGroups.getId())
                                    .bedType(bedGroups.getDescription())
                                    .checkHref(bedGroups.getLinks().getPrice_check().getHref())
                                    .build());
                        }
                        ResponseResult<CheckPriceResponse> checkPriceResult = new CheckPriceAccess(host, StringUtils.isBlank(request.getLanguage()) ? "zh-CN" :
                                request.getLanguage(),
                                expediaUtils.signGeneration(), ownIp, sessionId, rateLimiter).access(bedCheckInfos.get(0).getCheckHref());
                        if (!checkPriceResult.isSucc() || null == checkPriceResult.getData() || "sold_out".equals(checkPriceResult.getData().getStatus())) {
                            log.info("expedia验价失败,request:{},response:{}", JsonUtils.writeObject2Json(request), JsonUtils.writeObject2Json(result));
                            return null;
                        }
                        QueryPriceResponse.Occupancy_pricing occupancyPricing = checkPriceResult.getData().getOccupancy_pricing().get(request.getAdultNum().toString());
                        List<QueryPriceResponse.CancelPolicy> cancelPolicies = rate.getCancel_penalties();
                        ProductRespDTO productRespDTO = ProductRespDTO.builder()
                                .hotelId(hotelPrice.getProperty_id())
                                .productId(rate.getId())
                                .supplierId(SupplierSourceEnum.EXPEDIA.getCode())
                                .productInfo(ProductInfo.builder().inventory(1).productStatus(1).productName(room.getRoom_name()).build())
                                .currencyType(occupancyPricing.getTotals().getInclusive().getRequest_currency().getCurrency())
                                .totalPrice(new BigDecimal(occupancyPricing.getTotals().getInclusive().getRequest_currency().getValue()).multiply(new BigDecimal("100")).intValue())
                                .stayPrice(buildStayPrice(occupancyPricing.getStay()))
                                .storePayPrice(null == occupancyPricing.getTotals().getProperty_fees() ? 0 :
                                        new BigDecimal(occupancyPricing.getTotals().getProperty_fees().getRequest_currency().getValue()).multiply(new BigDecimal("100")).intValue())
                                .roomTotalPrice(new BigDecimal(occupancyPricing.getTotals().getExclusive().getRequest_currency().getValue()).multiply(new BigDecimal(
                                        "100")).intValue())
                                .priceInfos(buildQueryPriceInfos(occupancyPricing.getNightly(), request.getCheckIn()))
                                .meal(convertMeal(request.getAdultNum(), rate.getAmenities()))
                                .cancelPolicy(CollectionUtils.isNotEmpty(rate.getNonrefundable_date_ranges()) ? List.of(CancelPolicy.builder().cancelType(0).build()) :
                                        convertCancelPolicy(request.getCheckIn(), cancelPolicies))
                                .maxOccupancy(request.getAdultNum())
                                .priceFlag(queryPriceRequest.getSales_environment())
                                .distribution(rate.getSale_scenario().getDistribution())
                                .bedCheckInfos(bedCheckInfos)
                                .build();
                        productRespDTO.setTotalTaxes(productRespDTO.getTotalPrice() - productRespDTO.getRoomTotalPrice());
                        return Arrays.asList(productRespDTO);
                    }
                }
            }
        }
        log.info("expedia查询价格失败,request:{},response:{}", JsonUtils.writeObject2Json(queryPriceRequest), JsonUtils.writeObject2Json(result));
        return null;
    }

    @Override
    public CheckPriceResponse checkPrices(CheckPriceReq request) {

        QueryPriceRequest queryPriceRequest = QueryPriceRequest.builder()
                .property_id(request.getSHotelId())
                .checkin(request.getCheckIn())
                .checkout(request.getCheckOut())
                .currency("USD")
                .billing_terms(billingTerms)
                .payment_terms(paymentTerms)
                .partner_point_of_sale(partnerPointOfSale)
                .build();
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
        queryPriceRequest.setOccupancies(occupancies);
        queryPriceRequest.setSales_environment(StringUtils.isBlank(request.getPriceFlag()) ? "hotel_package" : request.getPriceFlag());
        ResponseResult<QueryPriceResponse> result = new QueryProductAccess(host, StringUtils.isBlank(request.getLanguage()) ? "zh-CN" : request.getLanguage(),
                expediaUtils.signGeneration(), ownIp, sessionId, rateLimiter).access(queryPriceRequest);
        if (result != null && result.isSucc() && null != result.getData() && CollectionUtils.isNotEmpty(result.getData().getHotelPrices())) {
            QueryPriceResponse.HotelPrice hotelPrice = result.getData().getHotelPrices().get(0);
            for (QueryPriceResponse.Rooms room : hotelPrice.getRooms()) {
                for (QueryPriceResponse.Rates rate : room.getRates()) {
                    if (request.getSProductId().equals(rate.getId())) {
                        QueryPriceResponse.Bed_groups bedGroups = null;
                        if (StringUtils.isBlank(request.getBedId())) {
                            for (String key : rate.getBed_groups().keySet()) {
                                bedGroups = rate.getBed_groups().get(key);
                            }
                        } else {
                            bedGroups = rate.getBed_groups().get(request.getBedId());
                        }
                        if (null == bedGroups) {
                            log.info("expedia查价失败,request:{},response:{}", JsonUtils.writeObject2Json(request), JsonUtils.writeObject2Json(result));
                            return null;
                        }
                        ResponseResult<CheckPriceResponse> checkPriceResult = new CheckPriceAccess(host, StringUtils.isBlank(request.getLanguage()) ? "zh-CN" :
                                request.getLanguage(), expediaUtils.signGeneration(), ownIp, sessionId, rateLimiter).access(bedGroups.getLinks().getPrice_check().getHref());
                        if (!checkPriceResult.isSucc() && null == checkPriceResult.getData()) {
                            log.info("expedia验价失败,request:{},response:{}", JsonUtils.writeObject2Json(request), JsonUtils.writeObject2Json(result));
                            return null;
                        }
                        checkPriceResult.getData().setAdultCount(request.getAdultCount());
                        return checkPriceResult.getData();
                    }
                }
            }
        }
        return null;
    }

    public List<PriceInfo> buildCheckPriceInfos(List<List<QueryPriceResponse.Nightly>> nightlyLists, String checkIn) {
        List<PriceInfo> priceInfos = Lists.newArrayList();
        for (int i = 0; i < nightlyLists.size(); i++) {
            BigDecimal sumPrice = BigDecimal.ZERO; // 初始化累加器为0
            for (QueryPriceResponse.Nightly nightly : nightlyLists.get(i)) {
                sumPrice = sumPrice.add(new BigDecimal(nightly.getValue()));
            }
            PriceInfo priceInfo = PriceInfo.builder()
                    .date(DateUtil.getFutureDay(checkIn, i))
                    .price(sumPrice.multiply(BigDecimal.valueOf(100)).intValue())
                    .build();
            priceInfos.add(priceInfo);
        }
        return priceInfos;
    }

    public Meal convertMeal(Integer adultNum, Map<String, QueryPriceResponse.Amenity> amenities) {
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
                meal = Meal.builder()
                        .count(1)
                        .lunchCount(0)
                        .dinnerCount(0)
                        .mealDesc(amenities.get(mealId).getName())
                        .build();
                break;
            case "2102":  //三餐（早+中+晚）
            case "2207":  //全包
                meal = Meal.builder()
                        .count(adultNum)
                        .lunchCount(adultNum)
                        .dinnerCount(adultNum)
                        .mealDesc(amenities.get(mealId).getName())
                        .build();
                break;
            case "2103":
            case "2104":
            case "2105":
            case "2205":
            case "1073742786":
            case "1073744734":
            case "1073744735":  //免费早餐（份数=入住人数）
            case "1073744459":  //咖啡面包形式的早餐
                meal = Meal.builder()
                        .count(adultNum)
                        .lunchCount(0)
                        .dinnerCount(0)
                        .mealDesc(amenities.get(mealId).getName())
                        .build();
                break;
            case "2106":  //免费午餐
                meal = Meal.builder()
                        .count(0)
                        .lunchCount(adultNum)
                        .dinnerCount(0)
                        .mealDesc(amenities.get(mealId).getName())
                        .build();
                break;
            case "2107":  //免费晚餐
                meal = Meal.builder()
                        .count(0)
                        .lunchCount(0)
                        .dinnerCount(adultNum)
                        .mealDesc(amenities.get(mealId).getName())
                        .build();
                break;
            case "2193":
            case "2194":  //双早（当入住人数=1时，只有一份）
                meal = Meal.builder()
                        .count(Math.min(2, adultNum))
                        .lunchCount(0)
                        .dinnerCount(0)
                        .mealDesc(amenities.get(mealId).getName())
                        .build();
                break;
            case "2206":  //半包
                meal = Meal.builder()
                        .count(adultNum)
                        .lunchCount(0)
                        .dinnerCount(adultNum)
                        .mealDesc(amenities.get(mealId).getName())
                        .build();
                break;
            default:
                meal = Meal.builder()
                        .count(0)
                        .lunchCount(0)
                        .dinnerCount(0)
                        .mealDesc("")
                        .build();
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
        SimpleDateFormat sdfTime = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
        sdfTime.setTimeZone(TimeZone.getTimeZone("GMT"));
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
            cancelPolicyList.add(CancelPolicy.builder()
                    .cancelType(1)
                    .timeZone(subDateGMT(cancelPolicy.getStart()))
                    .before(Math.max(25, beforeStart))
                    .type(RefundType.NO_DEDUCTION)
                    .build());
            if (beforeStart > 25) {
                cancelPolicyList.add(CancelPolicy.builder()
                        .cancelType(1)
                        .timeZone(subDateGMT(cancelPolicy.getEnd()))
                        .before(Math.max(25, beforeEnd))
                        .type(RefundType.DEDUCT_BY_AMOUNT)
                        .value(Double.valueOf(cancelPolicy.getAmount()))
                        .build());
            }
        } else if (StringUtils.isNotBlank(cancelPolicy.getPercent())) {
            if ("100%".equals(cancelPolicy.getPercent())) {
                cancelPolicyList.add(CancelPolicy.builder().cancelType(0).build());
            } else {
                cancelPolicyList.add(CancelPolicy.builder()
                        .cancelType(1)
                        .timeZone(subDateGMT(cancelPolicy.getStart()))
                        .before(Math.max(25, beforeStart))
                        .type(RefundType.NO_DEDUCTION)
                        .build());
                if (beforeStart > 25) {
                    cancelPolicyList.add(CancelPolicy.builder()
                            .cancelType(1)
                            .timeZone(subDateGMT(cancelPolicy.getEnd()))
                            .before(Math.max(25, beforeEnd))
                            .type(RefundType.DEDUCT_BY_PERCENT)
                            .value(Double.valueOf(cancelPolicy.getPercent().replace("%", "")))
                            .build());
                }
            }
        } else if (StringUtils.isNotBlank(cancelPolicy.getNights())) {
            if ("0".equals(cancelPolicy.getNights())) {
                cancelPolicyList.add(CancelPolicy.builder()
                        .cancelType(1)
                        .timeZone(subDateGMT(cancelPolicy.getEnd()))
                        .before(Math.max(25, beforeEnd))
                        .type(RefundType.NO_DEDUCTION)
                        .build());
            } else {
                cancelPolicyList.add(CancelPolicy.builder()
                        .cancelType(1)
                        .timeZone(subDateGMT(cancelPolicy.getStart()))
                        .before(Math.max(25, beforeStart))
                        .type(RefundType.NO_DEDUCTION)
                        .build());
                if (beforeStart > 25) {
                    cancelPolicyList.add(CancelPolicy.builder()
                            .cancelType(1)
                            .timeZone(subDateGMT(cancelPolicy.getEnd()))
                            .before(Math.max(25, beforeEnd))
                            .type(RefundType.DEDUCT_DAY_NIGHT)
                            .value(Double.valueOf(cancelPolicy.getNights()))
                            .build());
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
