package com.bingo.hotel.spa.intl.core.api.expedia.service.impl;

import com.bingo.hotel.spa.intl.cli.dto.PriceInfo;
import com.bingo.hotel.spa.intl.cli.dto.ProductInfo;
import com.bingo.hotel.spa.intl.cli.dto.ProductRespDTO;
import com.bingo.hotel.spa.intl.cli.seq.CheckPriceReq;
import com.bingo.hotel.spa.intl.cli.seq.PriceReq;
import com.bingo.hotel.spa.intl.cli.seq.Supplier;
import com.bingo.hotel.spa.intl.core.api.common.asynchttp.ResponseResult;
import com.bingo.hotel.spa.intl.core.api.common.enums.SupplierSourceEnum;
import com.bingo.hotel.spa.intl.core.api.expedia.access.QueryProductAccess;
import com.bingo.hotel.spa.intl.core.api.expedia.bean.request.QueryPriceRequest;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    public QueryPriceResponse queryPrice(PriceReq request, Supplier supplier) {
        ResponseResult<QueryPriceResponse> resultOnly;
        ResponseResult<QueryPriceResponse> resultPackage;
        QueryPriceResponse.HotelPrice hotelPriceOnly = null;
        QueryPriceResponse.HotelPrice hotelPricePackage = null;

        QueryPriceRequest queryPriceRequest = QueryPriceRequest.builder()
                .property_id(supplier.getSHotelId())
                .checkin(request.getCheckIn())
                .checkout(request.getCheckout())
                .currency(request.getCurrency())
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
        if ("USD".equals(request.getCurrency())) {
            //先查询零售价
            resultOnly = new QueryProductAccess(host, "en-US", expediaUtils.signGeneration(), ownIp, sessionId, rateLimiter).access(queryPriceRequest);
            //查询打包价
            queryPriceRequest.setSales_environment("hotel_package");
            resultPackage = new QueryProductAccess(host, "en-US", expediaUtils.signGeneration(), ownIp, sessionId, rateLimiter).access(queryPriceRequest);
        } else {
            //先查询零售价
            resultOnly = new QueryProductAccess(host, "zh-CN", expediaUtils.signGeneration(), ownIp, sessionId, rateLimiter).access(queryPriceRequest);
            //查询打包价
            queryPriceRequest.setSales_environment("hotel_package");
            resultPackage = new QueryProductAccess(host, "zh-CN", expediaUtils.signGeneration(), ownIp, sessionId, rateLimiter).access(queryPriceRequest);
        }
        if (resultOnly.isSucc() && null != resultOnly.getData() && CollectionUtils.isNotEmpty(resultOnly.getData().getHotelPrices())) {
            hotelPriceOnly = resultOnly.getData().getHotelPrices().get(0);
        }
        if (resultPackage.isSucc() && null != resultPackage.getData() && CollectionUtils.isNotEmpty(resultPackage.getData().getHotelPrices())) {
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

    private QueryPriceResponse convertPriceResp(QueryPriceResponse.HotelPrice hotelPrice, String salesType, PriceReq request) {
        QueryPriceResponse queryPriceResponse = new QueryPriceResponse();
        List<ProductRespDTO> productRespDTOS = new ArrayList<>();

        hotelPrice.getRooms().forEach(room -> {
            convertRoomResp(hotelPrice.getProperty_id(), room, salesType, productRespDTOS, request);
        });
        queryPriceResponse.setProductRespDTOList(productRespDTOS);
        return queryPriceResponse;
    }

    private QueryPriceResponse convertPriceComparisonsResp(QueryPriceResponse.HotelPrice hotelPriceOnly, QueryPriceResponse.HotelPrice hotelPricePackage, PriceReq request) {
        QueryPriceResponse queryPriceResponse = new QueryPriceResponse();
        List<ProductRespDTO> productRespDTOS = new ArrayList<>();
        List<QueryPriceResponse.Rooms> rooms = hotelPricePackage.getRooms();
        List<QueryPriceResponse.Rooms> rooms1 = hotelPriceOnly.getRooms();
        List<String> roomIdList = new ArrayList<>();
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
                ArrayList<String> rateIdList = new ArrayList<>();
                if (CollectionUtils.isNotEmpty(roomOnly.getRates()) && CollectionUtils.isNotEmpty(roomPackage.getRates())) {
                    rateOnlyMap = roomOnly.getRates().stream().collect(Collectors.toMap(QueryPriceResponse.Rates::getId, rate -> rate));
                    rateIdList.addAll(rateOnlyMap.keySet());
                    ratePackageMap = roomPackage.getRates().stream().collect(Collectors.toMap(QueryPriceResponse.Rates::getId, rate -> rate));
                    rateIdList.addAll(ratePackageMap.keySet());
                }
                for (String rateId : rateIdList) {
                    if (roomOnlyMap.containsKey(rateId) && roomPackageMap.containsKey(rateId)) {
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
                    } else if (roomOnlyMap.containsKey(rateId)) {
                        convertRateResp(hotelPriceOnly.getProperty_id(), roomOnly.getRoom_name(), rateOnlyMap.get(rateId), "hotel_only", productRespDTOS, request);
                    } else if (roomPackageMap.containsKey(rateId)) {
                        convertRateResp(hotelPricePackage.getProperty_id(), roomPackage.getRoom_name(), ratePackageMap.get(rateId), "hotel_package", productRespDTOS, request);
                    }
                }
            } else if (roomOnlyMap.containsKey(roomId)) {
                convertRoomResp(hotelPriceOnly.getProperty_id(), roomOnlyMap.get(roomId), "hotel_only", productRespDTOS, request);
            } else if (roomPackageMap.containsKey(roomId)) {
                convertRoomResp(hotelPricePackage.getProperty_id(), roomPackageMap.get(roomId), "hotel_package", productRespDTOS, request);
            }
        });
        queryPriceResponse.setProductRespDTOList(productRespDTOS);
        return queryPriceResponse;
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
            productRespDTOS.add(ProductRespDTO.builder()
                    .hotelId(hotelId)
                    .productId(rate.getId())
                    .supplierId(SupplierSourceEnum.EXPEDIA.getCode())
                    .productInfo(ProductInfo.builder().inventory(1).productStatus(1).productName(roomName).build())
                    .currencyType(occupancyPricing.getTotals().getInclusive().getRequest_currency().getCurrency())
                    .totalPrice(new BigDecimal(occupancyPricing.getTotals().getInclusive().getRequest_currency().getValue()).multiply(new BigDecimal("100")).intValue())
                    .priceInfos(buildPriceInfos(occupancyPricing.getNightly(), request.getCheckIn()))
//                              .meal(Meal.builder().count(productVO.getBreakfast_count()).build())
//                              .cancelPolicy(List.of(CancelPolicy.builder().cancelType(0).build()))
                    .maxOccupancy(request.getAdultNum())
                    .priceFlag(salesType)
                    .build());
        }
    }

    public List<PriceInfo> buildPriceInfos(List<List<QueryPriceResponse.Nightly>> nightlyLists, String checkIn) {
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
    public QueryPriceResponse checkPrice(CheckPriceReq request) {
        return null;
    }
}
