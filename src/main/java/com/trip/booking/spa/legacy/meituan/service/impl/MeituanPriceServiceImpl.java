package com.trip.booking.spa.legacy.meituan.service.impl;

import com.trip.booking.spa.legacy.placeholder.HotelBasePlaceholderClient;
import com.trip.booking.spa.legacy.placeholder.HotelInfoPlaceholderClient;
import com.trip.booking.spa.gateway.adapter.inbound.rest.dto.CancelPolicy;
import com.trip.booking.spa.gateway.domain.booking.CheckPriceOutcome;
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
import com.trip.booking.spa.gateway.domain.supplier.SupplierSourceEnum;
import com.trip.booking.spa.gateway.adapter.outbound.state.dao.mapper.SupplierHotelIdListMapper;
import com.trip.booking.spa.legacy.meituan.access.CheckPriceAccess;
import com.trip.booking.spa.legacy.meituan.access.ProductInfoAccess;
import com.trip.booking.spa.legacy.meituan.bean.request.CheckReqBody;
import com.trip.booking.spa.legacy.meituan.bean.request.ProductInfoReqBody;
import com.trip.booking.spa.legacy.meituan.bean.response.CheckPriceResponse;
import com.trip.booking.spa.legacy.meituan.bean.response.ProductInfoResponse;
import com.trip.booking.spa.legacy.meituan.service.ISupplierHotelIdListService;
import com.trip.booking.spa.legacy.meituan.service.MeituanPriceService;
import com.trip.booking.spa.platform.observability.Monitor;
import com.trip.booking.spa.platform.redis.DistributedRateLimiter;
import com.trip.booking.spa.platform.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


@Service
@Slf4j
public class MeituanPriceServiceImpl implements MeituanPriceService {

    @Value("${meituan.url}")
    String host;

    @Value("${meituan.partner-id}")
    Integer partnerId;

    @Value("${meituan.key.public}")
    String publicKey;

    @Value("${meituan.key.secret}")
    String secretKey;

    @Value("${meituan.test}")
    String test;

    @Value("${meituan.language}")
    String language;

    @Value("${meituan.method.goods.path}")
    String goodsPath;
    @Value("${meituan.method.goods.version}")
    String goodsVersion;

    @Value("${meituan.method.check.path}")
    String checkPath;
    @Value("${meituan.method.check.version}")
    String checkVersion;

    @Resource
    private HotelInfoPlaceholderClient hotelInfoPlaceholderClient;
    @Resource
    private HotelBasePlaceholderClient hotelBasePlaceholderClient;
    @Resource
    private DistributedRateLimiter rateLimiter;
    @Resource
    private ISupplierHotelIdListService iSupplierHotelIdListService;
    @Resource
    private SupplierHotelIdListMapper supplierHotelIdListMapper;


    @Override
    public List<ProductRespDTO> queryPrices(PriceReq request, Supplier supplier) {
        String childrenList = "";
        if (null != request.getChildNum() && 0 != request.getChildNum() && CollectionUtils.isNotEmpty(request.getChildAges())) {
            for (Integer childAge : request.getChildAges()) {
                if (StringUtils.isBlank(childrenList)) {
                    childrenList = childAge.toString();
                } else {
                    childrenList = childrenList + "," + childAge;
                }
            }
        }
        ProductInfoReqBody productInfoReqBody = ProductInfoReqBody.builder()
                .hotelIds(Arrays.asList(Long.valueOf(supplier.getSHotelId())))
                .checkinDate(request.getCheckIn())
                .checkoutDate(request.getCheckout())
                .numberOfAdults(request.getAdultNum())
                .numberOfChildren(request.getChildNum())
                .childrenAges(childrenList)
                .currencyCode("CNY")
                .clientNationality("CN")
                .build();

        log.info("getMeituanProductInfo roomReqBody:{}", JsonUtils.writeObject2Json(productInfoReqBody));

        ResponseResult<ProductInfoResponse> response = new ProductInfoAccess(host, partnerId, publicKey, secretKey,
                test, goodsPath, goodsVersion, rateLimiter).access(productInfoReqBody);

        if (null == response || !response.isSucc() || null == response.getData() || 0 != response.getData().getCode() || CollectionUtils.isEmpty(response.getData().getResult())) {
            log.info("MeiTuan查询报价失败,request:{},response:{}", JsonUtils.writeObject2Json(request), JsonUtils.writeObject2Json(response));
            Monitor.recordOne("MeiTuan_query_fail");
            return null;
        }

        ProductInfoResponse.Result pricesResponse = response.getData().getResult().get(0);
        List<ProductRespDTO> productRespDTOS = new ArrayList<>();
        pricesResponse.getGoodsList().forEach(priceInfo -> {
            int sumPrice = priceInfo.getPriceModelList().stream().mapToInt(ProductInfoResponse.PriceModelList::getPrice).sum();
            ProductRespDTO productRespDTO = ProductRespDTO.builder()
                    .hotelId(priceInfo.getHotelId().toString())
                    .productId(priceInfo.getGoodsId().toString())
                    .supplierId(SupplierSourceEnum.MEITUAN.getCode())
                    .room(Room.builder().roomId(priceInfo.getRealRoomId().toString()).build())
                    .productInfo(ProductInfo.builder().inventory(1).productStatus(1).productName(priceInfo.getGoodsName()).build())
                    .currencyType("CNY")
                    .totalPrice(sumPrice)
                    .brokerage(0)
                    .priceInfos(convertPriceInfo(priceInfo.getPriceModelList()))
                    .meal(convertMeal(priceInfo.getMealType()))
                    .cancelPolicy(convertCancelPolicy(request.getCheckIn(), priceInfo.getRefundable(), priceInfo.getCpApply()))
                    .maxOccupancy(priceInfo.getQuotedOccupancy())
                    .build();
            productRespDTOS.add(productRespDTO);
        });
        return productRespDTOS;
    }

    @Override
    public List<ProductRespDTO> queryProductPrice(PriceReq request, Supplier supplier) {
        String childrenList = "";
        if (null != request.getChildNum() && 0 != request.getChildNum() && CollectionUtils.isNotEmpty(request.getChildAges())) {
            for (Integer childAge : request.getChildAges()) {
                if (StringUtils.isBlank(childrenList)) {
                    childrenList = childAge.toString();
                } else {
                    childrenList = childrenList + "," + childAge;
                }
            }
        }
        CheckReqBody checkReqBody = CheckReqBody.builder()
                .hotelId(Long.valueOf(supplier.getSHotelId()))
                .goodsId(Long.valueOf(supplier.getSProductId()))
                .checkinDate(request.getCheckIn())
                .checkoutDate(request.getCheckout())
                .roomNum(request.getRoomNum())
                .numberOfAdults(request.getAdultNum())
                .numberOfChildren(request.getChildNum())
                .childrenAges(childrenList)
                .currencyCode("CNY")
                .clientNationality("CN")
                .build();

        log.info("queryProductPrice checkReqBody:{}", JsonUtils.writeObject2Json(checkReqBody));

        ResponseResult<CheckPriceResponse> response = new CheckPriceAccess(host, partnerId, publicKey, secretKey,
                test, checkPath, checkVersion, rateLimiter).access(checkReqBody);

        if (null == response || !response.isSucc() || null == response.getData() || 0 != response.getData().getCode()) {
            log.info("MeiTuan验价失败,request:{},response:{}", JsonUtils.writeObject2Json(request), JsonUtils.writeObject2Json(response));
            Monitor.recordOne("MeiTuan_query_fail");
            return null;
        }

        CheckPriceResponse.Result checkPriceResponse = response.getData().getResult();
        int sumPrice = checkPriceResponse.getPriceModelList().stream().mapToInt(ProductInfoResponse.PriceModelList::getPrice).sum();
        ProductRespDTO productRespDTO = ProductRespDTO.builder()
                .hotelId(supplier.getSHotelId())
                .productId(supplier.getSProductId())
                .supplierId(SupplierSourceEnum.MEITUAN.getCode())
                .productInfo(ProductInfo.builder().inventory(1).productStatus(1).productName(checkPriceResponse.getGoodsName()).build())
                .currencyType("CNY")
                .totalPrice(sumPrice)
                .brokerage(0)
                .priceInfos(convertPriceInfo(checkPriceResponse.getPriceModelList()))
                .meal(convertMeal(checkPriceResponse.getMealType()))
                .cancelPolicy(convertCancelPolicy(request.getCheckIn(), checkPriceResponse.getRefundable(), checkPriceResponse.getCpApply()))
                .build();

        return Arrays.asList(productRespDTO);
    }

    @Override
    public CheckPriceRespDTO checkPrices(CheckPriceReq request) {
        String childrenList = "";
        if (null != request.getChildNum() && 0 != request.getChildNum() && CollectionUtils.isNotEmpty(request.getChildAges())) {
            for (Integer childAge : request.getChildAges()) {
                if (StringUtils.isBlank(childrenList)) {
                    childrenList = childAge.toString();
                } else {
                    childrenList = childrenList + "," + childAge;
                }
            }
        }
        CheckReqBody checkReqBody = CheckReqBody.builder()
                .hotelId(Long.valueOf(request.getSHotelId()))
                .goodsId(Long.valueOf(request.getSProductId()))
                .checkinDate(request.getCheckIn())
                .checkoutDate(request.getCheckOut())
                .roomNum(request.getRoomNum())
                .numberOfAdults(request.getAdultCount())
                .numberOfChildren(request.getChildNum())
                .childrenAges(childrenList)
                .currencyCode("CNY")
                .clientNationality("CN")
                .build();

        log.info("queryProductPrice checkReqBody:{}", JsonUtils.writeObject2Json(checkReqBody));

        ResponseResult<CheckPriceResponse> response = new CheckPriceAccess(host, partnerId, publicKey, secretKey,
                test, checkPath, checkVersion, rateLimiter).access(checkReqBody);

        if (null == response || !response.isSucc() || null == response.getData() || 0 != response.getData().getCode()) {
            log.info("MeiTuan验价失败,request:{},response:{}", JsonUtils.writeObject2Json(request), JsonUtils.writeObject2Json(response));
            Monitor.recordOne("MeiTuan_query_fail");
            return null;
        }

        CheckPriceResponse.Result checkPriceResponse = response.getData().getResult();
        int sumPrice = checkPriceResponse.getPriceModelList().stream().mapToInt(ProductInfoResponse.PriceModelList::getPrice).sum();
        return CheckPriceRespDTO.builder()
                .outcome(CheckPriceOutcome.BOOKABLE)
                .salePrice(sumPrice)
                .subPrice(sumPrice)
                .totalPriceAfter(sumPrice)
                .totalPriceBefore(sumPrice)
                .build();

    }

    private List<PriceInfo> convertPriceInfo(List<ProductInfoResponse.PriceModelList> priceModelList) {
        ArrayList<PriceInfo> priceInfos = new ArrayList<>();
        priceModelList.forEach(priceModel -> {
            priceInfos.add(PriceInfo.builder()
                    .date(priceModel.getDate())
                    .price(priceModel.getPrice())
                    .build());
        });
        return priceInfos;
    }

    private Meal convertMeal(ProductInfoResponse.MealType mealType) {

        if (null == mealType || StringUtils.isBlank(mealType.getDesc())) {
            return Meal.builder()
                    .count(0)
                    .lunchCount(0)
                    .dinnerCount(0)
                    .build();
        }
        Meal meal = new Meal();
        switch (mealType.getDesc()) {
            case "早餐":
                meal = Meal.builder()
                        .count(mealType.getCount())
                        .lunchCount(0)
                        .dinnerCount(0)
                        .mealDesc(mealType.getDesc())
                        .build();
                break;
            case "早餐+晚餐":
                meal = Meal.builder()
                        .count(mealType.getCount())
                        .lunchCount(0)
                        .dinnerCount(mealType.getCount())
                        .mealDesc(mealType.getDesc())
                        .build();
                break;
            case "早餐+午餐+晚餐":
                meal = Meal.builder()
                        .count(mealType.getCount())
                        .lunchCount(mealType.getCount())
                        .dinnerCount(mealType.getCount())
                        .mealDesc(mealType.getDesc())
                        .build();
                break;
            case "午餐":
                meal = Meal.builder()
                        .count(0)
                        .lunchCount(mealType.getCount())
                        .dinnerCount(0)
                        .mealDesc(mealType.getDesc())
                        .build();
                break;
            case "晚餐":
                meal = Meal.builder()
                        .count(0)
                        .lunchCount(0)
                        .dinnerCount(mealType.getCount())
                        .mealDesc(mealType.getDesc())
                        .build();
                break;
            case "早餐+午餐":
                meal = Meal.builder()
                        .count(mealType.getCount())
                        .lunchCount(mealType.getCount())
                        .dinnerCount(0)
                        .mealDesc(mealType.getDesc())
                        .build();
                break;
            case "午餐+晚餐":
                meal = Meal.builder()
                        .count(0)
                        .lunchCount(mealType.getCount())
                        .dinnerCount(mealType.getCount())
                        .mealDesc(mealType.getDesc())
                        .build();
                break;
            default:
                meal = Meal.builder()
                        .count(0)
                        .lunchCount(0)
                        .dinnerCount(0)
                        .mealDesc(mealType.getDesc())
                        .build();
        }
        return meal;
    }

    private List<CancelPolicy> convertCancelPolicy(String checkInDate, Integer refundable, List<ProductInfoResponse.CpApply> cpApply) {
        if (1 == refundable || CollectionUtils.isEmpty(cpApply)) {
            return Arrays.asList(CancelPolicy.builder().cancelType(0).build());
        }
        List<CancelPolicy> cancelPolicies = new ArrayList<>();
        cpApply.forEach(cancelInfo -> {
            CancelPolicy cancelPolicy = CancelPolicy.builder()
                    .cancelType(1)
                    .timeZone(timeZoneConversion(cancelInfo.getEndDate(), cancelInfo.getEndDateLocal()))
                    .before(Math.max(25, getHours(checkInDate, cancelInfo.getEndDateLocal())))
                    .type((null == cancelInfo.getPenalty() || 0 == cancelInfo.getPenalty()) && 25 < getHours(checkInDate, cancelInfo.getEndDateLocal()) ?
                            RefundType.NO_DEDUCTION : RefundType.DEDUCT_BY_AMOUNT)
                    .value(null == cancelInfo.getPenalty() ? 0 : new BigDecimal(cancelInfo.getPenalty().toString()).divide(new BigDecimal("100")).doubleValue())
                    .build();
            cancelPolicies.add(cancelPolicy);
        });
        return cancelPolicies;
    }

    public int getHours(String checkInDate, String endDateLocal) {
        // 定义格式化模式
        DateTimeFormatter endDateFormatter = DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm");
        DateTimeFormatter checkInFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

        // 定义UTC
        ZoneId utcZone = ZoneId.of("UTC");

        LocalDateTime checkInDateTime = LocalDateTime.parse(checkInDate + " 24:00", checkInFormatter);
        ZonedDateTime checkInZonedDateTime = ZonedDateTime.of(checkInDateTime, utcZone);
        // 定义某地时间
        LocalDateTime targetDateTime = LocalDateTime.parse(endDateLocal, endDateFormatter);
        ZonedDateTime targetZonedDateTime = ZonedDateTime.of(targetDateTime, utcZone);

        // 计算时差（以小时为单位）
        Duration duration = Duration.between(targetZonedDateTime.toInstant(), checkInZonedDateTime.toInstant());
        return (int) duration.toHours();
    }

    public String timeZoneConversion(String endDate, String endDateLocal) {
        // 定义格式化模式
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm");
        // 定义北京时间
        LocalDateTime beijingDateTime = LocalDateTime.parse(endDate, formatter);
        ZoneId beijingZone = ZoneId.of("Asia/Shanghai");
        ZonedDateTime beijingZonedDateTime = ZonedDateTime.of(beijingDateTime, beijingZone);

        // 定义某地时间
        LocalDateTime targetDateTime = LocalDateTime.parse(endDateLocal, formatter);
        ZoneId utcZone = ZoneId.of("UTC");
        ZonedDateTime targetZonedDateTime = ZonedDateTime.of(targetDateTime, utcZone);

        // 计算时差（以小时为单位）
        Duration duration = Duration.between(beijingZonedDateTime.toInstant(), targetZonedDateTime.toInstant());
        long hoursDifference = duration.toHours();

        // 确定时区偏移量
        int offsetHours = (int) hoursDifference;
        return "GMT" + (offsetHours >= 0 ? "+" : "") + offsetHours;
    }

    public static void main(String[] args) {
        MeituanPriceServiceImpl meituanPriceService = new MeituanPriceServiceImpl();
        meituanPriceService.timeZoneConversion("02/10/2025 20:30", "02/10/2025 14:30");
    }
}
