package com.bingo.hotel.spa.intl.core.api.fastpay.service.impl;

import com.bingo.hotel.info.intl.cli.client.HotelInfoIntlClient;
import com.bingo.hotel.info.intl.cli.request.SupplierHotelBaseRequest;
import com.bingo.hotel.info.intl.cli.request.SupplierProductBaseRequest;
import com.bingo.hotel.spa.intl.cli.dto.CancelPolicy;
import com.bingo.hotel.spa.intl.cli.dto.CheckPriceRespDTO;
import com.bingo.hotel.spa.intl.cli.dto.Meal;
import com.bingo.hotel.spa.intl.cli.dto.PriceInfo;
import com.bingo.hotel.spa.intl.cli.dto.ProductInfo;
import com.bingo.hotel.spa.intl.cli.dto.ProductRespDTO;
import com.bingo.hotel.spa.intl.cli.dto.Room;
import com.bingo.hotel.spa.intl.cli.enums.RefundType;
import com.bingo.hotel.spa.intl.cli.seq.CheckPriceReq;
import com.bingo.hotel.spa.intl.cli.seq.PriceReq;
import com.bingo.hotel.spa.intl.cli.seq.Supplier;
import com.bingo.hotel.spa.intl.core.api.aichotels.utils.AichotelsProductConvertUtil;
import com.bingo.hotel.spa.intl.core.api.common.asynchttp.ResponseResult;
import com.bingo.hotel.spa.intl.core.api.common.enums.SupplierSourceEnum;
import com.bingo.hotel.spa.intl.core.api.expedia.utils.ThreadPoolUtils;
import com.bingo.hotel.spa.intl.core.api.fastpay.access.CheckProductAccess;
import com.bingo.hotel.spa.intl.core.api.fastpay.access.GetTokenAccess;
import com.bingo.hotel.spa.intl.core.api.fastpay.access.HotelDetailsAccess;
import com.bingo.hotel.spa.intl.core.api.fastpay.access.HotelListAccess;
import com.bingo.hotel.spa.intl.core.api.fastpay.access.QueryProductAccess;
import com.bingo.hotel.spa.intl.core.api.fastpay.adaptor.FastPayStaticInfoAdaptor;
import com.bingo.hotel.spa.intl.core.api.fastpay.bean.request.CheckPriceRequest;
import com.bingo.hotel.spa.intl.core.api.fastpay.bean.request.HotelInfoRequest;
import com.bingo.hotel.spa.intl.core.api.fastpay.bean.request.HotelListRequest;
import com.bingo.hotel.spa.intl.core.api.fastpay.bean.request.SearchRequest;
import com.bingo.hotel.spa.intl.core.api.fastpay.bean.response.CheckPriceResponse;
import com.bingo.hotel.spa.intl.core.api.fastpay.bean.response.HotelDetailResponse;
import com.bingo.hotel.spa.intl.core.api.fastpay.bean.response.HotelListResponse;
import com.bingo.hotel.spa.intl.core.api.fastpay.bean.response.HotelSummary;
import com.bingo.hotel.spa.intl.core.api.fastpay.bean.response.SearchResponse;
import com.bingo.hotel.spa.intl.core.api.fastpay.service.FastPayService;
import com.bingo.hotel.spa.intl.core.redis.DistributedRateLimiter;
import com.bingo.hotel.spa.intl.core.util.DateUtil;
import com.bingo.hotel.spa.intl.core.util.JsonUtils;
import com.google.common.util.concurrent.RateLimiter;
import com.google.errorprone.annotations.concurrent.LazyInit;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.compress.utils.Lists;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.ParseException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;


@Service
@Slf4j
public class FastPayServiceImpl implements FastPayService {

    @Value("${fastPayHotels.url.catalogue}")
    private String catalogueUrl;
    @Value("${fastPayHotels.url.availability}")
    private String availabilityUrl;
    @Value("${fastPayHotels.url.booking}")
    private String bookingUrl;
    @Value("${fastPayHotels.username}")
    private String username;
    @Value("${fastPayHotels.password}")
    private String password;
    @Value("${fastPayHotels.client_id}")
    private String clientId;
    @Value("${fastPayHotels.client_secret}")
    private String clientSecret;

    @Resource
    private HotelInfoIntlClient hotelInfoIntlClient;
    @Resource
    private GetTokenAccess getTokenAccess;
    @Resource
    private DistributedRateLimiter rateLimiter;
    private static String token = "";

    private static RateLimiter FASTPAY_QUERY_HOTEL_LIMITER = RateLimiter.create(14);

    public String getToken() {
        if (StringUtils.isBlank(token)) {
            synchronized (LazyInit.class) {
                if (StringUtils.isBlank(token)) {
                    token = getTokenAccess.request("https://avail-baosheng.fastpayhotels.net/security/token", username, password, clientId, clientSecret);
                }
            }
        }
        return token;
    }

    @Override
    public void saveHotelList(int days, String type) {
        List<String> hotelCodes = new ArrayList<>();
        HotelListRequest hotelListRequest = HotelListRequest.builder()
                .messageID(UUID.randomUUID().toString())
                .fromLastUpdateDate(getHotel(days))
                .toLastUpdateDate(getHotel(0))
                .build();
        ResponseResult<HotelListResponse> hotelListResponse = new HotelListAccess(catalogueUrl, getToken(), rateLimiter).access(hotelListRequest);
        if (!hotelListResponse.isSucc() || null == hotelListResponse.getData() || CollectionUtils.isEmpty(hotelListResponse.getData().getHotelSummary())) {
            log.info("未查询到酒店列表");
            return;
        }
        hotelCodes = hotelListResponse.getData().getHotelSummary().stream().map(HotelSummary::getCode).collect(Collectors.toList());
        log.info("酒店总数：{}", hotelCodes.size());
        for (String hotelCode : hotelCodes) {
            try {
                ThreadPoolUtils.execute(() -> {
                    if ("HOTEL".equals(type)) {
                        FASTPAY_QUERY_HOTEL_LIMITER.acquire();
                        HotelInfoRequest hotelInfoRequest = HotelInfoRequest.builder()
                                .messageID(UUID.randomUUID().toString())
                                .code(hotelCode)
                                .languages(Arrays.asList("zh", "en"))
                                .build();
                        ResponseResult<HotelDetailResponse> hotelDetailResult = new HotelDetailsAccess(catalogueUrl, getToken(), rateLimiter).access(hotelInfoRequest);
                        if (null == hotelDetailResult || null == hotelDetailResult.getData() || null == hotelDetailResult.getData().getHotelDetail()) {
                            log.info("未查询到酒店详情");
                            return;
                        }

                        //组装参数
                        SupplierHotelBaseRequest supplierHotelBaseRequest =
                                FastPayStaticInfoAdaptor.transformInfoHotelReq(hotelInfoRequest.getCode(), hotelDetailResult.getData().getHotelDetail());

                        //推送酒店房型静态信息落库
                        pushHotelInfo(supplierHotelBaseRequest);
                    } else if ("PRODUCT".equals(type)) {

                        SearchRequest searchRequest = SearchRequest.builder()
                                .messageID(UUID.randomUUID().toString())
                                .currency("USD")
                                .checkIn(DateUtil.getFutureDay("", days))
                                .checkOut(DateUtil.getFutureDay("", days + 1))
                                .occupancies(Arrays.asList(SearchRequest.Occupancy.builder()
                                        .adults(new BigDecimal("1"))
                                        .build()))
                                .hotelCodes(Arrays.asList(hotelCode))
                                .build();
                        ResponseResult<SearchResponse> searchResult = new QueryProductAccess(availabilityUrl, getToken(), rateLimiter).access(searchRequest);
                        if (null == searchResult || null == searchResult.getData() || CollectionUtils.isEmpty(searchResult.getData().getHotelAvails())) {
                            log.info("未查询到产品详情");
                            return;
                        }

                        //组装参数
                        List<SupplierProductBaseRequest> supplierProductBaseRequests = FastPayStaticInfoAdaptor.transformInfoProductReq(searchResult.getData().getHotelAvails());

                        //推送产品静态信息落库
                        pushProductInfo(supplierProductBaseRequests);
                    }
                });
            } catch (Exception e) {
                log.error("异步推送静态数据异常:", e);
            }

        }
    }

    private void pushHotelInfo(SupplierHotelBaseRequest supplierHotelBaseRequest) {
        //推送酒店
        hotelInfoIntlClient.saveHotelInfo(Arrays.asList(supplierHotelBaseRequest));
        //推送房型
        hotelInfoIntlClient.saveRoomInfo(supplierHotelBaseRequest.getRoomList());
    }

    private void pushProductInfo(List<SupplierProductBaseRequest> supplierProductBaseRequests) {
        //推送产品
        hotelInfoIntlClient.saveProductInfo(supplierProductBaseRequests);
    }

    private OffsetDateTime getHotel(int days) {
        // 构造一个表示当天午夜的LocalDateTime，即0点0分0秒
        LocalDateTime midnightToday = LocalDate.now().minusDays(days).atStartOfDay();
        // 获取系统默认的ZoneId（时区）
        ZoneId zoneId = ZoneId.systemDefault();
        // 创建OffsetDateTime，即具有偏移量的日期时间，以当前时区为例
        OffsetDateTime offsetDateTime = midnightToday.atZone(zoneId).toOffsetDateTime();
        return offsetDateTime;
    }


    @Override
    public List<ProductRespDTO> queryPrices(PriceReq request, Supplier supplier) {

        List<SearchRequest.Occupancy> occupancies = new ArrayList<>();
        for (Integer integer = 0; integer < request.getRoomNum(); integer++) {
            occupancies.add(SearchRequest.Occupancy.builder()
                    .adults(new BigDecimal(request.getAdultNum().toString()))
                    .children(new BigDecimal(request.getChildNum().toString()))
                    .childrenAges(request.getChildAges().stream().map(a -> new BigDecimal(a.toString())).collect(Collectors.toList()))
                    .build());
        }
        SearchRequest searchRequest = SearchRequest.builder()
                .messageID(UUID.randomUUID().toString())
                .currency("USD")
                .checkIn(request.getCheckIn())
                .checkOut(request.getCheckout())
                .occupancies(occupancies)
                .hotelCodes(Arrays.asList(supplier.getSHotelId()))
                .build();
        ResponseResult<SearchResponse> searchResult = new QueryProductAccess(availabilityUrl, getToken(), rateLimiter).access(searchRequest);
        if (null == searchResult || null == searchResult.getData() || CollectionUtils.isEmpty(searchResult.getData().getHotelAvails())) {
            log.info("未查询到产品详情,request:{}", JsonUtils.writeObject2Json(searchRequest));
            return null;
        }
        List<SearchResponse.HotelAvail> hotelAvails = searchResult.getData().getHotelAvails();
        ArrayList<ProductRespDTO> productRespList = new ArrayList<>();
        for (SearchResponse.HotelAvail hotelAvail : hotelAvails) {
            for (SearchResponse.AvailRoomRate availRoomRate : hotelAvail.getAvailRoomRates()) {
                BigDecimal totalPrice = (null == availRoomRate.getPriceBinding() || true != availRoomRate.getPriceBinding()) ? availRoomRate.getTotalPrice() :
                        availRoomRate.getPublicPrice();
                ProductRespDTO productRespDTO = ProductRespDTO.builder()
                        .hotelId(hotelAvail.getHotelInfo().getCode())
                        .productId(hotelAvail.getHotelInfo().getCode() + "_" + availRoomRate.getRoomCode() + "_" + availRoomRate.getRatePlanCode())
                        .supplierId(SupplierSourceEnum.FASTPAYHOTELS.getCode())
                        .room(Room.builder().roomId(availRoomRate.getRoomCode()).roomName(availRoomRate.getRoomName()).build())
                        .productInfo(ProductInfo.builder().inventory(1).productStatus(1).productName(availRoomRate.getRoomName()).build())
                        .currencyType(availRoomRate.getCurrency())
                        .totalPrice(totalPrice.multiply(new BigDecimal("100")).intValue())
                        .brokerage(null == availRoomRate.getCommission() ? 0 : availRoomRate.getCommission().multiply(new BigDecimal("100")).intValue())
                        .priceInfos(buildQueryPriceInfos(totalPrice, request.getCheckIn(), request.getCheckout()))
                        .meal(convertMeal(request.getAdultNum(), availRoomRate.getMealPlanName()))
                        .cancelPolicy(convertCancelPolicy(supplier.getSHotelId(), availRoomRate.getCancellationPolicy()))
                        .build();
                productRespList.add(productRespDTO);
            }
        }
        return productRespList;
    }

    public List<PriceInfo> buildQueryPriceInfos(BigDecimal totalPrice, String checkIn, String checkOut) {
        List<PriceInfo> priceInfos = Lists.newArrayList();

        try {
            Integer nights = DateUtil.getNights(checkIn, checkOut);
            BigDecimal price = totalPrice.divide(new BigDecimal(nights.toString()));
            for (int i = 0; i < nights; i++) {
                PriceInfo priceInfo = PriceInfo.builder()
                        .date(DateUtil.getFutureDay(checkIn, i))
                        .price(price.multiply(new BigDecimal("100")).setScale(0, RoundingMode.HALF_UP).intValue())
                        .build();
                priceInfos.add(priceInfo);
            }
        } catch (ParseException e) {
            log.error("价格日历计算失败：{},{},{}", totalPrice, checkIn, checkOut, e);
        }
        return priceInfos;
    }

    public Meal convertMeal(Integer adultNum, String mealName) {

        Meal meal = new Meal();
        switch (mealName) {
            case "All Inclusive":
            case "All Inclusive Plus":
            case "All Inclusive Soft":
            case "American Breakfast":
            case "Bed & Breakfast":
            case "Buffet Breakfast":
            case "Continental Breakfast":
            case "Half Board":
            case "Half Board Premium":
                meal = Meal.builder()
                        .count(adultNum)
                        .lunchCount(0)
                        .dinnerCount(0)
                        .mealDesc(mealName)
                        .build();
                break;
            case "Brunch":
            case "Half Board (BB  & Lunch)":
                meal = Meal.builder()
                        .count(adultNum)
                        .lunchCount(adultNum)
                        .dinnerCount(0)
                        .mealDesc(mealName)
                        .build();
                break;
            case "Dinner Only":
            case "Half Board (Dinner Adults Only) ":
                meal = Meal.builder()
                        .count(0)
                        .lunchCount(0)
                        .dinnerCount(adultNum)
                        .mealDesc(mealName)
                        .build();
                break;
            case "Full Board":
            case "Full Board Plus":
                meal = Meal.builder()
                        .count(adultNum)
                        .lunchCount(adultNum)
                        .dinnerCount(adultNum)
                        .mealDesc(mealName)
                        .build();
                break;
            case "Half Board (BB  & Dinner)":
                meal = Meal.builder()
                        .count(adultNum)
                        .lunchCount(0)
                        .dinnerCount(adultNum)
                        .mealDesc(mealName)
                        .build();
                break;
            default:
                meal = Meal.builder()
                        .count(0)
                        .lunchCount(0)
                        .dinnerCount(0)
                        .mealDesc(mealName)
                        .build();
        }
        return meal;
    }

    @Override
    public List<ProductRespDTO> queryProductPrice(PriceReq request, Supplier supplier) {
        List<SearchRequest.Occupancy> occupancies = new ArrayList<>();
        for (Integer integer = 0; integer < request.getRoomNum(); integer++) {
            occupancies.add(SearchRequest.Occupancy.builder()
                    .adults(new BigDecimal(request.getAdultNum().toString()))
                    .children(new BigDecimal(request.getChildNum().toString()))
                    .childrenAges(request.getChildAges().stream().map(a -> new BigDecimal(a.toString())).collect(Collectors.toList()))
                    .build());
        }
        SearchRequest searchRequest = SearchRequest.builder()
                .messageID(UUID.randomUUID().toString())
                .currency("USD")
                .checkIn(request.getCheckIn())
                .checkOut(request.getCheckout())
                .occupancies(occupancies)
                .hotelCodes(Arrays.asList(supplier.getSHotelId()))
                .build();
        ResponseResult<SearchResponse> searchResult = new QueryProductAccess(availabilityUrl, getToken(), rateLimiter).access(searchRequest);
        if (null == searchResult || null == searchResult.getData() || CollectionUtils.isEmpty(searchResult.getData().getHotelAvails())) {
            log.info("未查询到产品报价详情,request:{}", JsonUtils.writeObject2Json(searchRequest));
            return null;
        }
        List<SearchResponse.HotelAvail> hotelAvails = searchResult.getData().getHotelAvails();
        for (SearchResponse.HotelAvail hotelAvail : hotelAvails) {
            for (SearchResponse.AvailRoomRate availRoomRate : hotelAvail.getAvailRoomRates()) {
                String productId = supplier.getSHotelId() + "_" + availRoomRate.getRoomCode() + "_" + availRoomRate.getRatePlanCode();
                if (supplier.getSProductId().equals(productId)) {
                    CheckPriceRequest checkPriceRequest = CheckPriceRequest.builder()
                            .messageID(UUID.randomUUID().toString())
                            .currency("USD")
                            .checkIn(request.getCheckIn())
                            .checkOut(request.getCheckout())
                            .occupancy(occupancies.get(0))
                            .hotelCode(supplier.getSHotelId())
                            .productCode(availRoomRate.getProductCode())
                            .quantity(new BigDecimal(request.getRoomNum().toString()))
                            .build();
                    ResponseResult<CheckPriceResponse> checkPriceResult = new CheckProductAccess(bookingUrl, getToken(), rateLimiter).access(checkPriceRequest);
                    if (null == checkPriceResult || null == checkPriceResult.getData() || CollectionUtils.isEmpty(checkPriceResult.getData().getHotelAvails()) || CollectionUtils.isEmpty(checkPriceResult.getData().getHotelAvails().get(0).getAvailRoomRates())) {
                        log.info("未查询到产品验价详情,request:{}", JsonUtils.writeObject2Json(checkPriceRequest));
                        return null;
                    }
                    SearchResponse.HotelAvail hotelInfo = checkPriceResult.getData().getHotelAvails().get(0);
                    SearchResponse.AvailRoomRate roomInfo = hotelInfo.getAvailRoomRates().get(0);
                    BigDecimal totalPrice = (null == roomInfo.getPriceBinding() || true != roomInfo.getPriceBinding()) ? roomInfo.getTotalPrice() :
                            roomInfo.getPublicPrice();
                    ProductRespDTO productRespDTO = ProductRespDTO.builder()
                            .hotelId(hotelInfo.getHotelInfo().getCode())
                            .productId(productId)
                            .room(Room.builder().roomId(availRoomRate.getRoomCode()).roomName(availRoomRate.getRoomName()).build())
                            .supplierId(SupplierSourceEnum.FASTPAYHOTELS.getCode())
                            .productInfo(ProductInfo.builder().inventory(1).productStatus(1).productName(roomInfo.getRoomName()).build())
                            .currencyType(roomInfo.getCurrency())
                            .totalPrice(totalPrice.multiply(new BigDecimal("100")).intValue())
                            .brokerage(null == availRoomRate.getCommission() ? 0 : availRoomRate.getCommission().multiply(new BigDecimal("100")).intValue())
                            .priceInfos(buildQueryPriceInfos(totalPrice, request.getCheckIn(), request.getCheckout()))
                            .meal(convertMeal(request.getAdultNum(), availRoomRate.getMealPlanName()))
                            .cancelPolicy(convertCancelPolicy(supplier.getSHotelId(), availRoomRate.getCancellationPolicy()))
                            .build();
                    return Arrays.asList(productRespDTO);
                }
            }
        }
        return null;
    }

    @Override
    public CheckPriceRespDTO checkPrices(CheckPriceReq request) {
        List<SearchRequest.Occupancy> occupancies = new ArrayList<>();
        for (Integer integer = 0; integer < request.getRoomNum(); integer++) {
            occupancies.add(SearchRequest.Occupancy.builder()
                    .adults(new BigDecimal(request.getAdultCount().toString()))
                    .children(new BigDecimal(request.getChildNum().toString()))
                    .childrenAges(request.getChildAges().stream().map(a -> new BigDecimal(a.toString())).collect(Collectors.toList()))
                    .build());
        }
        SearchRequest searchRequest = SearchRequest.builder()
                .messageID(UUID.randomUUID().toString())
                .currency("USD")
                .checkIn(request.getCheckIn())
                .checkOut(request.getCheckOut())
                .occupancies(occupancies)
                .hotelCodes(Arrays.asList(request.getSHotelId()))
                .build();
        ResponseResult<SearchResponse> searchResult = new QueryProductAccess(availabilityUrl, getToken(), rateLimiter).access(searchRequest);
        if (null == searchResult || null == searchResult.getData() || CollectionUtils.isEmpty(searchResult.getData().getHotelAvails())) {
            log.info("未查询到产品报价详情,request:{}", JsonUtils.writeObject2Json(searchRequest));
            return null;
        }
        List<SearchResponse.HotelAvail> hotelAvails = searchResult.getData().getHotelAvails();
        for (SearchResponse.HotelAvail hotelAvail : hotelAvails) {
            for (SearchResponse.AvailRoomRate availRoomRate : hotelAvail.getAvailRoomRates()) {
                String productId = request.getSHotelId() + "_" + availRoomRate.getRoomCode() + "_" + availRoomRate.getRatePlanCode();
                if (request.getSProductId().equals(productId)) {
                    CheckPriceRequest checkPriceRequest = CheckPriceRequest.builder()
                            .messageID(UUID.randomUUID().toString())
                            .currency("USD")
                            .checkIn(request.getCheckIn())
                            .checkOut(request.getCheckOut())
                            .occupancy(occupancies.get(0))
                            .hotelCode(request.getSHotelId())
                            .productCode(availRoomRate.getProductCode())
                            .quantity(new BigDecimal(request.getRoomNum().toString()))
                            .build();
                    ResponseResult<CheckPriceResponse> checkPriceResult = new CheckProductAccess(bookingUrl, getToken(), rateLimiter).access(checkPriceRequest);
                    if (null == checkPriceResult || null == checkPriceResult.getData() || CollectionUtils.isEmpty(checkPriceResult.getData().getHotelAvails()) || CollectionUtils.isEmpty(checkPriceResult.getData().getHotelAvails().get(0).getAvailRoomRates())) {
                        log.info("未查询到产品详情,request:{}", JsonUtils.writeObject2Json(searchRequest));
                        return null;
                    }
                    SearchResponse.HotelAvail hotelInfo = checkPriceResult.getData().getHotelAvails().get(0);
                    SearchResponse.AvailRoomRate roomInfo = hotelInfo.getAvailRoomRates().get(0);
                    BigDecimal totalPrice = (null == roomInfo.getPriceBinding() || true != roomInfo.getPriceBinding()) ? roomInfo.getTotalPrice() :
                            roomInfo.getPublicPrice();
                    return CheckPriceRespDTO.builder()
                            .checkStatus(true)
                            .prebookToken(roomInfo.getReservationToken())
                            .salePrice(totalPrice.multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP).intValue())
                            .totalPriceAfter(totalPrice.multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP).intValue())
                            .totalPriceBefore(totalPrice.multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP).intValue())
                            .message(roomInfo.getCurrency())
                            .build();
                }
            }
        }
        return null;
    }

    public static List<CancelPolicy> convertCancelPolicy(String hotelCode, SearchResponse.CancellationPolicy cancelPolicy) {
        List<CancelPolicy> cancelPolicyList = new ArrayList<>();
        try {
            AichotelsProductConvertUtil aichotelsProductConvertUtil = new AichotelsProductConvertUtil();
            String timeZone = aichotelsProductConvertUtil.getTimeZone(null, hotelCode, SupplierSourceEnum.FASTPAYHOTELS.getCode());
            if (StringUtils.isBlank(cancelPolicy.getCode())) {
                cancelPolicyList.add(CancelPolicy.builder().cancelType(0).build());
                return cancelPolicyList;
            }
            if (isInCorrectFormat(cancelPolicy.getCode(), "^(100P|100P_0D1N|1N_0D1N|1N)_([0-9]+)D([0-9]+)N$")) {
                Pattern pattern = Pattern.compile("^(100P|100P_0D1N|1N_0D1N|1N)_([0-9]+)D([0-9]+)N$"); // 注意这里简化了正则以只匹配一个数字
                Matcher matcher = pattern.matcher(cancelPolicy.getCode());
                Integer hours = 0;
                Integer days = 1;
                if (matcher.find()) {
                    // 获取捕获组中的第二个组，即第二个括号里的内容，它应该是我们需要的数字。
                    hours = Integer.parseInt(matcher.group(2)) * 24;
                    days = Integer.parseInt(matcher.group(3));
                }
                cancelPolicyList.add(CancelPolicy.builder()
                        .cancelType(1)
                        .timeZone(timeZone)
                        .before(Math.max(25, hours))
                        .type(RefundType.NO_DEDUCTION)
                        .build());
                if (hours > 25) {
                    cancelPolicyList.add(CancelPolicy.builder()
                            .cancelType(1)
                            .timeZone(timeZone)
                            .before(25)
                            .type(RefundType.DEDUCT_DAY_NIGHT)
                            .value(Double.valueOf(days))
                            .build());
                }
            } else if (isInCorrectFormat(cancelPolicy.getCode(), "^(100P|100P_0D1N|1N_0D1N|1N)_([0-9]+)D([0-9]+)N#([0-9]{2})(AM|PM)$")) {
                Pattern pattern = Pattern.compile("^(100P|100P_0D1N|1N_0D1N|1N)_([0-9]+)D([0-9]+)N#([0-9]{2})(AM|PM)$"); // 注意这里简化了正则以只匹配一个数字
                Matcher matcher = pattern.matcher(cancelPolicy.getCode());
                if (matcher.find()) {
                    // 获取捕获组中的第二个组，即第二个括号里的内容，它应该是我们需要的数字。
                    Integer hours = Integer.parseInt(matcher.group(2)) * 24 + ("AM".equals(matcher.group(5)) ? 24 - Integer.parseInt(matcher.group(4)) :
                            12 - Integer.parseInt(matcher.group(4)));
                    Integer days = Integer.parseInt(matcher.group(3));
                    cancelPolicyList.add(CancelPolicy.builder()
                            .cancelType(1)
                            .timeZone(timeZone)
                            .before(Math.max(25, hours))
                            .type(RefundType.NO_DEDUCTION)
                            .build());
                    if (hours > 25) {
                        cancelPolicyList.add(CancelPolicy.builder()
                                .cancelType(1)
                                .timeZone(timeZone)
                                .before(25)
                                .type(RefundType.DEDUCT_DAY_NIGHT)
                                .value(Double.valueOf(days))
                                .build());
                    }
                }
            } else if (isInCorrectFormat(cancelPolicy.getCode(), "^(100P|100P_0D1N|1N_0D1N|1N)_([0-9]+)D(\\d+)P#([0-9]{2})(AM|PM)$")) {
                Pattern pattern = Pattern.compile("^(100P|100P_0D1N|1N_0D1N|1N)_([0-9]+)D(\\d+)P#([0-9]{2})(AM|PM)$");
                Matcher matcher = pattern.matcher(cancelPolicy.getCode());
                if (matcher.find()) {
                    // 获取捕获组中的第二个组，即第二个括号里的内容，它应该是我们需要的数字。
                    Integer hours = Integer.parseInt(matcher.group(2)) * 24 + ("AM".equals(matcher.group(4)) ? 24 - Integer.parseInt(matcher.group(4)) :
                            12 - Integer.parseInt(matcher.group(3)));
                    cancelPolicyList.add(CancelPolicy.builder()
                            .cancelType(1)
                            .timeZone(timeZone)
                            .before(Math.max(25, hours))
                            .type(RefundType.NO_DEDUCTION)
                            .build());
                }
            } else if (isInCorrectFormat(cancelPolicy.getCode(), "^(100P|100P_0D1N|1N_0D1N|1N)_([0-9]+)D(\\d+)P$")) {
                Pattern pattern = Pattern.compile("^(100P|100P_0D1N|1N_0D1N|1N)_([0-9]+)D(\\d+)P$");
                Matcher matcher = pattern.matcher(cancelPolicy.getCode());
                if (matcher.find()) {
                    // 获取捕获组中的第二个组，即第二个括号里的内容，它应该是我们需要的数字。
                    Integer hours = Integer.parseInt(matcher.group(2)) * 24;
                    cancelPolicyList.add(CancelPolicy.builder()
                            .cancelType(1)
                            .timeZone(timeZone)
                            .before(Math.max(25, hours))
                            .type(RefundType.NO_DEDUCTION)
                            .build());
                }
            } else {
                cancelPolicyList.add(CancelPolicy.builder().cancelType(0).build());
            }
        } catch (Exception e) {
            log.error("取消规则计算异常 info:{}", JsonUtils.writeObject2Json(cancelPolicy), e);
            cancelPolicyList.add(CancelPolicy.builder().cancelType(0).build());
        }
        return cancelPolicyList;
    }

    public static void main(String[] args) {
        // 定义你想要读取的文件的名称（假设是"example.txt"）
//        String fileName = "D:\\working\\idea\\international-hotel\\hotel-spa-intl\\core\\src\\main\\java\\com\\bingo\\hotel\\spa\\intl\\core\\api\\fastpay\\service\\impl\\cancelFile";
        String filePath = "D:\\GPT浏览器下载/partner_feed_en_v3.jsonl";

        try {
            // 使用BufferedReader读取文件
            BufferedReader br = new BufferedReader(new FileReader(filePath));
            String line;
            while ((line = br.readLine()) != null) {
                System.out.println(JsonUtils.writeObject2Json(line));

//                // 处理每一行的内容
//                SearchResponse.CancellationPolicy cancellationPolicy = JsonUtils.readValue(line, SearchResponse.CancellationPolicy.class);
//                System.out.println(JsonUtils.writeObject2Json(convertCancelPolicy("", cancellationPolicy)));
            }
            br.close(); // 关闭流
        } catch (IOException e) {
            e.printStackTrace(); // 如果出现错误，打印堆栈信息
        }
    }

    public static boolean isInCorrectFormat(String str, String patternStr) {
        // 正则表达式匹配格式：数字N_数字D数字N
        // 例如：1N_2D1N 这样的格式
        Pattern pattern = Pattern.compile(patternStr);
        Matcher matcher = pattern.matcher(str);
        return matcher.matches(); // 如果字符串符合正则表达式的格式，则返回true
    }

    public static int getNumberBeforeD(String str, String patternStr) {
        // 提取D前面的数字，假设D前面只有一个数字且格式正确
        Pattern pattern = Pattern.compile(patternStr); // 注意这里简化了正则以只匹配一个数字
        Matcher matcher = pattern.matcher(str);
        if (matcher.find()) {
            // 获取捕获组中的第二个组，即第二个括号里的内容，它应该是我们需要的数字。
            String beforeDStr = matcher.group(1); // 注意，正则表达式中组的索引是从1开始的。
            return Integer.parseInt(beforeDStr); // 将字符串转换为整数。如果可能抛出NumberFormatException，需处理异常。
        } else {
            return -1; // 如果没有找到匹配项，返回-1或其他错误值。
        }
    }

}
