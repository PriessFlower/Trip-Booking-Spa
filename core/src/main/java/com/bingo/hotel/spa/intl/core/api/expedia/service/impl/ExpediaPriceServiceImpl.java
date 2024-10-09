package com.bingo.hotel.spa.intl.core.api.expedia.service.impl;

import com.bingo.hotel.spa.intl.cli.dto.BedCheckInfo;
import com.bingo.hotel.spa.intl.cli.dto.PriceInfo;
import com.bingo.hotel.spa.intl.cli.dto.ProductInfo;
import com.bingo.hotel.spa.intl.cli.dto.ProductRespDTO;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
            reqExpediaQueryPrice(request, queryPriceRequest, resultOnly, resultPackage, "en-US");
        } else {
            reqExpediaQueryPrice(request, queryPriceRequest, resultOnly, resultPackage, "zh-CN");
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
            return convertPriceResp(hotelPricePackage, "hotel_only", request);
        } else {
            return convertPriceComparisonsResp(hotelPriceOnly, hotelPricePackage, request);
        }
    }

    private void reqExpediaQueryPrice(PriceReq request, QueryPriceRequest queryPriceRequest, ResponseResult<QueryPriceResponse> resultOnly,
                                      ResponseResult<QueryPriceResponse> resultPackage, String language) {
        if ("hotel_only".equals(request.getSalesType())) {
            //先查询零售价
            queryPriceRequest.setSales_environment("hotel_only");
            resultOnly = new QueryProductAccess(host, language, expediaUtils.signGeneration(), ownIp, sessionId, rateLimiter).access(queryPriceRequest);
        } else if ("hotel_package".equals(request.getSalesType())) {
            //查询打包价
            queryPriceRequest.setSales_environment("hotel_package");
            resultPackage = new QueryProductAccess(host, language, expediaUtils.signGeneration(), ownIp, sessionId, rateLimiter).access(queryPriceRequest);
        } else {
            //先查询零售价
            queryPriceRequest.setSales_environment("hotel_only");
            resultOnly = new QueryProductAccess(host, language, expediaUtils.signGeneration(), ownIp, sessionId, rateLimiter).access(queryPriceRequest);
            //查询打包价
            queryPriceRequest.setSales_environment("hotel_package");
            resultPackage = new QueryProductAccess(host, language, expediaUtils.signGeneration(), ownIp, sessionId, rateLimiter).access(queryPriceRequest);
        }
    }

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
            ProductRespDTO productRespDTO = ProductRespDTO.builder()
                    .hotelId(hotelId)
                    .productId(rate.getId())
                    .supplierId(SupplierSourceEnum.EXPEDIA.getCode())
                    .productInfo(ProductInfo.builder().inventory(1).productStatus(1).productName(roomName).build())
                    .currencyType(occupancyPricing.getTotals().getInclusive().getRequest_currency().getCurrency())
                    .totalPrice(new BigDecimal(occupancyPricing.getTotals().getInclusive().getRequest_currency().getValue()).multiply(new BigDecimal("100")).intValue())
                    .roomPrice(new BigDecimal(occupancyPricing.getTotals().getExclusive().getRequest_currency().getValue()).multiply(new BigDecimal("100")).intValue())
                    .priceInfos(buildQueryPriceInfos(occupancyPricing.getNightly(), request.getCheckIn()))
//                              .meal(Meal.builder().count(productVO.getBreakfast_count()).build())
//                              .cancelPolicy(List.of(CancelPolicy.builder().cancelType(0).build()))
                    .maxOccupancy(request.getAdultNum())
                    .priceFlag(salesType)
                    .build();
            productRespDTO.setTaxes(productRespDTO.getTotalPrice() - productRespDTO.getRoomPrice());
            productRespDTOS.add(productRespDTO);
        }
    }

    public List<PriceInfo> buildQueryPriceInfos(List<List<QueryPriceResponse.Nightly>> nightlyLists, String checkIn) {
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

    @Override
    public List<ProductRespDTO> queryProductPrice(PriceReq request, Supplier supplier) {

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
        queryPriceRequest.setSales_environment(StringUtils.isBlank(request.getSalesType()) ? "hotel_only" : request.getSalesType());
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
                        QueryPriceResponse.Occupancy_pricing occupancyPricing = rate.getOccupancy_pricing().get(request.getAdultNum().toString());
                        ProductRespDTO productRespDTO = ProductRespDTO.builder()
                                .hotelId(hotelPrice.getProperty_id())
                                .productId(rate.getId())
                                .supplierId(SupplierSourceEnum.EXPEDIA.getCode())
                                .productInfo(ProductInfo.builder().inventory(1).productStatus(1).productName(room.getRoom_name()).build())
                                .currencyType(occupancyPricing.getTotals().getInclusive().getRequest_currency().getCurrency())
                                .totalPrice(new BigDecimal(occupancyPricing.getTotals().getInclusive().getRequest_currency().getValue()).multiply(new BigDecimal("100")).intValue())
                                .roomPrice(new BigDecimal(occupancyPricing.getTotals().getExclusive().getRequest_currency().getValue()).multiply(new BigDecimal("100")).intValue())
                                .priceInfos(buildQueryPriceInfos(occupancyPricing.getNightly(), request.getCheckIn()))
//                              .meal(Meal.builder().count(productVO.getBreakfast_count()).build())
//                              .cancelPolicy(List.of(CancelPolicy.builder().cancelType(0).build()))
                                .maxOccupancy(request.getAdultNum())
                                .priceFlag(queryPriceRequest.getSales_environment())
                                .bedInfoList(bedCheckInfos)
                                .build();
                        productRespDTO.setTaxes(productRespDTO.getTotalPrice() - productRespDTO.getRoomPrice());
                        return Arrays.asList(productRespDTO);
                    }
                }
            }
        }
        log.info("expedia查询价格失败,request:{},response:{}", JsonUtils.writeObject2Json(queryPriceRequest), JsonUtils.writeObject2Json(result));
        return new ArrayList<ProductRespDTO>();
    }

    @Override
    public CheckPriceResponse checkPrices(CheckPriceReq request) {
        ResponseResult<CheckPriceResponse> result = new CheckPriceAccess(host, StringUtils.isBlank(request.getLanguage()) ? "zh-CN" : request.getLanguage(),
                expediaUtils.signGeneration(), ownIp, sessionId, rateLimiter).access(request.getExpediaCheckUrl());
        if (!result.isSucc() && null == result.getData()) {
            log.info("expedia验价失败,request:{},response:{}", JsonUtils.writeObject2Json(request), JsonUtils.writeObject2Json(result));
            return null;
        }
        return result.getData();
    }

    private void packageCheckPrice(CheckPriceReq request, ResponseResult<CheckPriceResponse> result) {
        CheckPriceResponse.Occupancy_pricing occupancyPricing = result.getData().getOccupancy_pricing();
        ProductRespDTO.builder()
                .hotelId(request.getSHotelId())
                .productId(request.getSProductId())
                .supplierId(SupplierSourceEnum.EXPEDIA.getCode())
//                .productInfo(ProductInfo.builder().inventory(1).productStatus(1).productName(roomName).build())
                .currencyType(occupancyPricing.getTotals().getInclusive().getRequest_currency().getCurrency())
                .totalPrice(new BigDecimal(occupancyPricing.getTotals().getInclusive().getRequest_currency().getValue()).multiply(new BigDecimal("100")).intValue())
                .priceInfos(buildCheckPriceInfos(occupancyPricing.getNightly(), request.getCheckIn()))
//                              .meal(Meal.builder().count(productVO.getBreakfast_count()).build())
//                              .cancelPolicy(List.of(CancelPolicy.builder().cancelType(0).build()))
                .maxOccupancy(request.getAdultCount())
//                .priceFlag(salesType)
                .build();
    }

    public List<PriceInfo> buildCheckPriceInfos(List<List<CheckPriceResponse.Nightly>> nightlyLists, String checkIn) {
        List<PriceInfo> priceInfos = Lists.newArrayList();
        for (int i = 0; i < nightlyLists.size(); i++) {
            BigDecimal sumPrice = BigDecimal.ZERO; // 初始化累加器为0
            for (CheckPriceResponse.Nightly nightly : nightlyLists.get(i)) {
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

}
