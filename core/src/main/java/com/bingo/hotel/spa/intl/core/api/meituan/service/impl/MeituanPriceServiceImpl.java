package com.bingo.hotel.spa.intl.core.api.meituan.service.impl;

import com.bingo.hotel.base.intl.cli.client.HotelBaseIntlClient;
import com.bingo.hotel.info.intl.cli.client.HotelInfoIntlClient;
import com.bingo.hotel.spa.intl.cli.dto.CancelPolicy;
import com.bingo.hotel.spa.intl.cli.dto.CheckPriceRespDTO;
import com.bingo.hotel.spa.intl.cli.dto.Meal;
import com.bingo.hotel.spa.intl.cli.dto.PriceInfo;
import com.bingo.hotel.spa.intl.cli.dto.ProductInfo;
import com.bingo.hotel.spa.intl.cli.dto.ProductRespDTO;
import com.bingo.hotel.spa.intl.cli.dto.Room;
import com.bingo.hotel.spa.intl.cli.seq.CheckPriceReq;
import com.bingo.hotel.spa.intl.cli.seq.PriceReq;
import com.bingo.hotel.spa.intl.cli.seq.Supplier;
import com.bingo.hotel.spa.intl.core.api.common.asynchttp.ResponseResult;
import com.bingo.hotel.spa.intl.core.api.common.enums.SupplierSourceEnum;
import com.bingo.hotel.spa.intl.core.api.common.mapper.SupplierHotelIdListMapper;
import com.bingo.hotel.spa.intl.core.api.meituan.access.CheckPriceAccess;
import com.bingo.hotel.spa.intl.core.api.meituan.access.ProductInfoAccess;
import com.bingo.hotel.spa.intl.core.api.meituan.bean.request.CheckReqBody;
import com.bingo.hotel.spa.intl.core.api.meituan.bean.request.ProductInfoReqBody;
import com.bingo.hotel.spa.intl.core.api.meituan.bean.response.CheckPriceResponse;
import com.bingo.hotel.spa.intl.core.api.meituan.bean.response.ProductInfoResponse;
import com.bingo.hotel.spa.intl.core.api.meituan.service.ISupplierHotelIdListService;
import com.bingo.hotel.spa.intl.core.api.meituan.service.MeituanPriceService;
import com.bingo.hotel.spa.intl.core.monitor.Monitor;
import com.bingo.hotel.spa.intl.core.redis.DistributedRateLimiter;
import com.bingo.hotel.spa.intl.core.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
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
    private HotelInfoIntlClient hotelInfoIntlClient;
    @Resource
    private HotelBaseIntlClient hotelBaseIntlClient;
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
                    .cancelPolicy(convertCancelPolicy(priceInfo.getRefundable(), priceInfo.getCpApply()))
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
                .cancelPolicy(convertCancelPolicy(checkPriceResponse.getRefundable(), checkPriceResponse.getCpApply()))
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
                .checkStatus(true)
                .salePrice(sumPrice)
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

    private List<CancelPolicy> convertCancelPolicy(Integer refundable, List<ProductInfoResponse.CpApply> cpApply) {
        if (1 == refundable || CollectionUtils.isEmpty(cpApply)) {
            return Arrays.asList(CancelPolicy.builder().cancelType(0).build());
        }
        List<CancelPolicy> cancelPolicies = new ArrayList<>();
        cpApply.forEach(cancelInfo -> {
            //
            CancelPolicy cancelPolicy = CancelPolicy.builder()
                    .build();
            cancelPolicies.add(cancelPolicy);
        });
        return cancelPolicies;
    }
}
