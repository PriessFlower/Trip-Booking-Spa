package com.bingo.hotel.spa.intl.core.api.ratehawk.service.impl;


import com.bingo.hotel.info.intl.cli.client.HotelInfoIntlClient;
import com.bingo.hotel.info.intl.cli.request.QueryHotelRequest;
import com.bingo.hotel.info.intl.cli.request.SupplierHotelBaseRequest;
import com.bingo.hotel.info.intl.cli.response.PageResp;
import com.bingo.hotel.info.intl.cli.response.SupplierHotelBaseResponse;
import com.bingo.hotel.info.intl.cli.result.InfoResult;
import com.bingo.hotel.spa.intl.cli.dto.CancelPolicy;
import com.bingo.hotel.spa.intl.cli.dto.CheckPriceRespDTO;
import com.bingo.hotel.spa.intl.cli.dto.Meal;
import com.bingo.hotel.spa.intl.cli.dto.PriceInfo;
import com.bingo.hotel.spa.intl.cli.dto.ProductInfo;
import com.bingo.hotel.spa.intl.cli.dto.ProductRespDTO;
import com.bingo.hotel.spa.intl.cli.seq.CheckPriceReq;
import com.bingo.hotel.spa.intl.cli.seq.PriceReq;
import com.bingo.hotel.spa.intl.cli.seq.Supplier;
import com.bingo.hotel.spa.intl.core.api.common.asynchttp.ResponseResult;
import com.bingo.hotel.spa.intl.core.api.common.enums.SupplierSourceEnum;
import com.bingo.hotel.spa.intl.core.api.expedia.bean.response.QueryPriceResponse;
import com.bingo.hotel.spa.intl.core.api.expedia.utils.ThreadPoolUtils;
import com.bingo.hotel.spa.intl.core.api.ratehawk.access.CheckPriceAccess;
import com.bingo.hotel.spa.intl.core.api.ratehawk.access.HotelFileAccess;
import com.bingo.hotel.spa.intl.core.api.ratehawk.access.QueryProductAccess;
import com.bingo.hotel.spa.intl.core.api.ratehawk.adaptor.RateHawkStaticInfoAdaptor;
import com.bingo.hotel.spa.intl.core.api.ratehawk.bean.request.CheckPriceRequest;
import com.bingo.hotel.spa.intl.core.api.ratehawk.bean.request.HotelInfoRequest;
import com.bingo.hotel.spa.intl.core.api.ratehawk.bean.request.QueryProductRequest;
import com.bingo.hotel.spa.intl.core.api.ratehawk.bean.response.CheckPriceResponse;
import com.bingo.hotel.spa.intl.core.api.ratehawk.bean.response.HotelFileResponse;
import com.bingo.hotel.spa.intl.core.api.ratehawk.bean.response.HotelStaticInfo;
import com.bingo.hotel.spa.intl.core.api.ratehawk.bean.response.QueryProductResponse;
import com.bingo.hotel.spa.intl.core.api.ratehawk.service.RateHawkService;
import com.bingo.hotel.spa.intl.core.redis.DistributedRateLimiter;
import com.bingo.hotel.spa.intl.core.util.DateUtil;
import com.bingo.hotel.spa.intl.core.util.FileDealUtils;
import com.bingo.hotel.spa.intl.core.util.JsonUtils;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.RateLimiter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;


@Service
@Slf4j
public class RateHawkServiceImpl implements RateHawkService {

    @Value("${ratehawk.key_id}")
    private String keyId;
    @Value("${ratehawk.api_key}")
    private String apiKey;
    @Value("${ratehawk.url}")
    private String url;
    @Value("${system.localFilePath}")
    private String LOCAL_FILE_PATH;
    @Resource
    private HotelInfoIntlClient hotelInfoIntlClient;
    @Resource
    private DistributedRateLimiter redisRateLimiter;
    private static RateLimiter RATEHAWK_QUERY_HOTEL_LIMITER = RateLimiter.create(0.15);

    @Override
    public void queryAndSaveStaticInfo(boolean downloadFlag) {

        //1.获取酒店文件
        ResponseResult<HotelFileResponse> hotelFileResult = new HotelFileAccess(url, generateBasicAuth(), redisRateLimiter).access(HotelInfoRequest.builder()
                .inventory("all")
                .language("en")
                .build());

        if (null == hotelFileResult || null == hotelFileResult.getData() || StringUtils.isBlank(hotelFileResult.getData().getUrl())) {
            log.info("酒店文件查询异常:{}", JsonUtils.writeObject2Json(hotelFileResult));
        }

        //2.分片存储文件
        String fileUrl = hotelFileResult.getData().getUrl();
        String localFilePath = LOCAL_FILE_PATH + "rateHawk/" + fileUrl.substring(fileUrl.lastIndexOf("/") + 1);
        String fileName = localFilePath.replace(".jsonl.zst", "");
        if (downloadFlag) {
            FileDealUtils.downloadFile(fileUrl, localFilePath);
            FileDealUtils.zstdFiles(localFilePath, fileName);
        }

        //3.解析文件数据并推送保存静态数据
        int chunkCount = 1;
        while (Files.exists(Paths.get(fileName + "_" + chunkCount + ".jsonl"))) {
            parseFile(fileName + "_" + chunkCount + ".jsonl");
        }
    }

    private void parseFile(String localFilePath) {
        try (BufferedReader reader = new BufferedReader(new FileReader(localFilePath))) {
            String line;
            log.info("开始推送酒店信息");
            int sumHotel = 0;
            while ((line = reader.readLine()) != null) {
                sumHotel += 1;

                HotelStaticInfo hotelStaticInfo = JsonUtils.readValue(line, HotelStaticInfo.class);

                if (sumHotel % 1000 == 0) {
                    log.info("已经推送酒店总数：{}", sumHotel);
                }
                if (null == hotelStaticInfo) {
                    continue;
                }
                ThreadPoolUtils.execute(() -> {
                    pushHotelAndRoomList(hotelStaticInfo);
                });
            }
            log.info("酒店静态信息推送完毕,共：{}", sumHotel);
            //删除文件
            Files.deleteIfExists(Paths.get(localFilePath));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void pushHotelAndRoomList(HotelStaticInfo hotelStaticInfo) {
        try {
            SupplierHotelBaseRequest supplierHotelBaseRequest = RateHawkStaticInfoAdaptor.transformInfoHotelReq(hotelStaticInfo);
            //推送酒店
            hotelInfoIntlClient.saveHotelInfo(Arrays.asList(supplierHotelBaseRequest));
            //推送房型
            hotelInfoIntlClient.saveRoomInfo(supplierHotelBaseRequest.getRoomList());
        } catch (Exception e) {
            log.error("酒店推送异常，request:{}", JsonUtils.writeObject2Json(hotelStaticInfo), e);
        }
    }


    @Override
    public void queryAndSaveProductInfo(String checkInDate, String checkOutDate, List<String> supplierHotelIds, Integer startNum) {
        if (StringUtils.isBlank(checkInDate) || StringUtils.isBlank(checkOutDate)) {
            checkInDate = DateUtil.getFutureDay(null, 9);
            checkOutDate = DateUtil.getFutureDay(null, 10);
        }
        if (CollectionUtils.isNotEmpty(supplierHotelIds)) {
            pushProductInfo(checkInDate, checkOutDate, supplierHotelIds);
            return;
        }
        int pageNum = null == startNum ? 0 : startNum;
        QueryHotelRequest queryHotelRequest = new QueryHotelRequest().setSupplierId(10007);
        while (true) {
            queryHotelRequest.setPageNum(pageNum).setPageSize(100);
            InfoResult<PageResp<SupplierHotelBaseResponse>> hotelInfoPageListResult = hotelInfoIntlClient.queryHotelPageList(queryHotelRequest);
            if (!hotelInfoPageListResult.isSUCCESS() || null == hotelInfoPageListResult.getData() || CollectionUtils.isEmpty(hotelInfoPageListResult.getData().getList())) {
                log.info("酒店展示集合查询未果，入参：{}，反参：{}", JsonUtils.writeObject2Json(queryHotelRequest), JsonUtils.writeObject2Json(hotelInfoPageListResult));
                return;
            }
            supplierHotelIds = hotelInfoPageListResult.getData().getList().stream().map(SupplierHotelBaseResponse::getSupplierHotelId).collect(Collectors.toList());
            pushProductInfo(checkInDate, checkOutDate, supplierHotelIds);
            pageNum++;
        }
    }

    private void pushProductInfo(String checkInDate, String checkOutDate, List<String> supplierHotelIds) {
        supplierHotelIds.forEach(supplierHotelId -> {
            RATEHAWK_QUERY_HOTEL_LIMITER.acquire();
            ThreadPoolUtils.execute(() -> {
                QueryProductRequest.Guests guests = QueryProductRequest.Guests.builder()
                        .adults(1)
                        .children(new ArrayList<>())
                        .build();
                QueryProductRequest queryProductRequest = QueryProductRequest.builder()
                        .hid(Integer.parseInt(supplierHotelId))
                        .checkin(checkInDate)
                        .checkout(checkOutDate)
                        .currency("USD")
                        .guests(Arrays.asList(guests))
                        .language("en")
                        .build();
                try {
                    ResponseResult<QueryProductResponse> queryProductResult =
                            new QueryProductAccess(url, generateBasicAuth(), redisRateLimiter).access(queryProductRequest);
                    if (null != queryProductResult.getData() && CollectionUtils.isNotEmpty(queryProductResult.getData().getHotels())) {
                        //推送info
                        hotelInfoIntlClient.saveProductInfo(RateHawkStaticInfoAdaptor.transformInfoProductReq(queryProductResult.getData()));
                    } else {
                        log.info("请求ratehawk查询产品信息异常：request:{},response:{}", JsonUtils.writeObject2Json(queryProductRequest),
                                JsonUtils.writeObject2Json(queryProductResult));
                    }

                } catch (Exception e) {
                    log.error("推送产品信息异常 request:{} ", JsonUtils.writeObject2Json(queryProductRequest), e);
                }
            });
        });
    }

    @Override
    public List<ProductRespDTO> queryPrices(PriceReq request, Supplier supplier) {
        ArrayList<ProductRespDTO> productRespList = new ArrayList<>();

        List<QueryProductRequest.Guests> guestsList = new ArrayList<>();
        for (Integer integer = 0; integer < request.getRoomNum(); integer++) {
            QueryProductRequest.Guests guests = QueryProductRequest.Guests.builder()
                    .adults(request.getAdultNum())
                    .children(0 == request.getChildNum() ? new ArrayList<>() :
                            request.getChildAges().stream().map(a -> String.valueOf(a)).collect(Collectors.toList()))
                    .build();
            guestsList.add(guests);
        }
        QueryProductRequest queryProductRequest = QueryProductRequest.builder()
                .hid(Integer.parseInt(supplier.getSHotelId()))
                .checkin(request.getCheckIn())
                .checkout(request.getCheckout())
                .currency("USD")
                .guests(guestsList)
                .language("en")
                .build();
        try {
            ResponseResult<QueryProductResponse> queryProductResult =
                    new QueryProductAccess(url, generateBasicAuth(), redisRateLimiter).access(queryProductRequest);
            if (null != queryProductResult.getData() && CollectionUtils.isNotEmpty(queryProductResult.getData().getHotels())) {
                List<QueryProductResponse.Hotels> hotels = queryProductResult.getData().getHotels();
                for (QueryProductResponse.Hotels hotel : hotels) {
                    if (CollectionUtils.isEmpty(hotel.getRates())) {
                        continue;
                    }
                    for (QueryProductResponse.Rates rate : hotel.getRates()) {
                        QueryProductResponse.Payment_types paymentTypes = rate.getPayment_options().getPayment_types().get(0);
                        ProductRespDTO productRespDTO = ProductRespDTO.builder()
                                .hotelId(String.valueOf(hotel.getHid()))
                                .productId(hotel.getHid() + "_" + rate.getRoom_name() + "_" + rate.getMeal() + "_" + (StringUtils.isBlank(paymentTypes.getCancellation_penalties().getFree_cancellation_before()) ? "1" : "0"))
                                .supplierId(SupplierSourceEnum.RATEHAWK.getCode())
                                .productInfo(ProductInfo.builder().inventory(1).productStatus(1).productName(rate.getRoom_name()).build())
                                .currencyType(queryProductRequest.getCurrency())
                                .totalPrice(new BigDecimal(paymentTypes.getAmount()).multiply(BigDecimal.valueOf(100)).intValue())
                                .brokerage(new BigDecimal(paymentTypes.getCommission_info().getShow().getAmount_commission()).multiply(BigDecimal.valueOf(100)).intValue())
                                .priceInfos(buildQueryPriceInfos(rate.getDaily_prices(), request.getCheckIn()))
                                .meal(null)
                                .cancelPolicy(List.of(CancelPolicy.builder().cancelType(0).build()))
                                .maxOccupancy(request.getAdultNum())
                                .build();
                        productRespList.add(productRespDTO);
                    }
                }
            } else {
                log.info("请求ratehawk查询产品信息异常：request:{},response:{}", JsonUtils.writeObject2Json(queryProductRequest),
                        JsonUtils.writeObject2Json(queryProductResult));
            }
        } catch (Exception e) {
            log.error("推送产品信息异常：request:{} ", JsonUtils.writeObject2Json(queryProductRequest), e);
        }
        return productRespList;
    }


    @Override
    public List<ProductRespDTO> queryProductPrice(PriceReq request, Supplier supplier) {

        ArrayList<ProductRespDTO> productRespList = new ArrayList<>();

        List<QueryProductRequest.Guests> guestsList = new ArrayList<>();
        for (Integer integer = 0; integer < request.getRoomNum(); integer++) {
            QueryProductRequest.Guests guests = QueryProductRequest.Guests.builder()
                    .adults(request.getAdultNum())
                    .children(0 == request.getChildNum() ? new ArrayList<>() :
                            request.getChildAges().stream().map(a -> String.valueOf(a)).collect(Collectors.toList()))
                    .build();
            guestsList.add(guests);
        }
        QueryProductRequest queryProductRequest = QueryProductRequest.builder()
                .hid(Integer.parseInt(supplier.getSHotelId()))
                .checkin(request.getCheckIn())
                .checkout(request.getCheckout())
                .currency("USD")
                .guests(guestsList)
                .language("en")
                .build();
        try {
            ResponseResult<QueryProductResponse> queryProductResult =
                    new QueryProductAccess(url, generateBasicAuth(), redisRateLimiter).access(queryProductRequest);
            if (null != queryProductResult.getData() && CollectionUtils.isNotEmpty(queryProductResult.getData().getHotels())) {
                List<QueryProductResponse.Hotels> hotels = queryProductResult.getData().getHotels();
                for (QueryProductResponse.Hotels hotel : hotels) {
                    if (CollectionUtils.isEmpty(hotel.getRates())) {
                        continue;
                    }
                    for (QueryProductResponse.Rates rate : hotel.getRates()) {
                        String[] productInfo = supplier.getSProductId().split("_");
                        QueryProductResponse.Payment_types paymentTypes = rate.getPayment_options().getPayment_types().get(0);
                        if (productInfo[0].equals(String.valueOf(hotel.getHid())) && productInfo[1].equals(rate.getRoom_name()) && productInfo[2].equals(rate.getMeal()) &&
                                productInfo[3].equals(StringUtils.isBlank(paymentTypes.getCancellation_penalties().getFree_cancellation_before()) ? "1" : "0")) {
                            //有产品信息去做验价
                            CheckPriceRequest checkPriceRequest = CheckPriceRequest.builder().book_hash(rate.getBook_hash()).language("en").build();
                            ResponseResult<CheckPriceResponse> checkPriceResponse = new CheckPriceAccess(url, generateBasicAuth(), redisRateLimiter).access(checkPriceRequest);
                            if (null != checkPriceResponse.getData() && CollectionUtils.isNotEmpty(checkPriceResponse.getData().getHotels())) {
                                ProductRespDTO productRespDTO = ProductRespDTO.builder()
                                        .hotelId(String.valueOf(hotel.getHid()))
                                        .productId(hotel.getHid() + "_" + rate.getRoom_name() + "_" + rate.getMeal() + "_" + (StringUtils.isBlank(paymentTypes.getCancellation_penalties().getFree_cancellation_before()) ? "1" : "0"))
                                        .supplierId(SupplierSourceEnum.RATEHAWK.getCode())
                                        .productInfo(ProductInfo.builder().inventory(1).productStatus(1).productName(rate.getRoom_name()).build())
                                        .currencyType(queryProductRequest.getCurrency())
                                        .totalPrice(new BigDecimal(paymentTypes.getAmount()).multiply(BigDecimal.valueOf(100)).intValue())
                                        .brokerage(new BigDecimal(paymentTypes.getCommission_info().getShow().getAmount_commission()).multiply(BigDecimal.valueOf(100)).intValue())
                                        .priceInfos(buildQueryPriceInfos(rate.getDaily_prices(), request.getCheckIn()))
                                        .meal(null)
                                        .cancelPolicy(List.of(CancelPolicy.builder().cancelType(0).build()))
                                        .maxOccupancy(request.getAdultNum())
                                        .build();
                                productRespList.add(productRespDTO);
                            }
                        }
                    }
                }
            } else {
                log.info("请求ratehawk查询产品信息异常：request:{},response:{}", JsonUtils.writeObject2Json(queryProductRequest),
                        JsonUtils.writeObject2Json(queryProductResult));
            }
        } catch (Exception e) {
            log.error("验价信息异常：request:{} ", JsonUtils.writeObject2Json(queryProductRequest), e);
        }
        return productRespList;
    }

    public List<PriceInfo> buildQueryPriceInfos(List<String> priceList, String checkIn) {
        List<PriceInfo> priceInfos = Lists.newArrayList();
        for (int i = 0; i < priceList.size(); i++) {
            PriceInfo priceInfo = PriceInfo.builder()
                    .date(DateUtil.getFutureDay(checkIn, i))
                    .price(new BigDecimal(priceList.get(i)).multiply(BigDecimal.valueOf(100)).intValue())
                    .build();
            priceInfos.add(priceInfo);
        }
        return priceInfos;
    }

    public Meal convertMeal(Integer adultNum) {
        return null;
    }

    public List<CancelPolicy> convertCancelPolicy(String checkIn, List<QueryPriceResponse.CancelPolicy> cancelPolicies) {
        return null;
    }

    @Override
    public CheckPriceRespDTO checkPrices(CheckPriceReq request) {

        List<QueryProductRequest.Guests> guestsList = new ArrayList<>();
        for (Integer integer = 0; integer < request.getRoomNum(); integer++) {
            QueryProductRequest.Guests guests = QueryProductRequest.Guests.builder()
                    .adults(request.getAdultCount())
                    .children(0 == request.getChildNum() ? new ArrayList<>() :
                            request.getChildAges().stream().map(a -> String.valueOf(a)).collect(Collectors.toList()))
                    .build();
            guestsList.add(guests);
        }
        QueryProductRequest queryProductRequest = QueryProductRequest.builder()
                .hid(Integer.parseInt(request.getSHotelId()))
                .checkin(request.getCheckIn())
                .checkout(request.getCheckOut())
                .currency("USD")
                .guests(guestsList)
                .language("en")
                .build();
        try {
            ResponseResult<QueryProductResponse> queryProductResult =
                    new QueryProductAccess(url, generateBasicAuth(), redisRateLimiter).access(queryProductRequest);
            if (null != queryProductResult.getData() && CollectionUtils.isNotEmpty(queryProductResult.getData().getHotels())) {
                List<QueryProductResponse.Hotels> hotels = queryProductResult.getData().getHotels();
                for (QueryProductResponse.Hotels hotel : hotels) {
                    if (CollectionUtils.isEmpty(hotel.getRates())) {
                        continue;
                    }
                    for (QueryProductResponse.Rates rate : hotel.getRates()) {
                        String[] productInfo = request.getSProductId().split("_");
                        QueryProductResponse.Payment_types paymentTypes = rate.getPayment_options().getPayment_types().get(0);
                        if (productInfo[0].equals(String.valueOf(hotel.getHid())) && productInfo[1].equals(rate.getRoom_name()) && productInfo[2].equals(rate.getMeal()) &&
                                productInfo[3].equals(StringUtils.isBlank(paymentTypes.getCancellation_penalties().getFree_cancellation_before()) ? "1" : "0")) {
                            //有产品信息去做验价
                            CheckPriceRequest checkPriceRequest = CheckPriceRequest.builder().book_hash(rate.getBook_hash()).language("en").build();
                            ResponseResult<CheckPriceResponse> checkPriceResponse = new CheckPriceAccess(url, generateBasicAuth(), redisRateLimiter).access(checkPriceRequest);
                            if (null != checkPriceResponse.getData() && CollectionUtils.isNotEmpty(checkPriceResponse.getData().getHotels())) {
                                QueryProductResponse.Hotels hotelCheckInfo = checkPriceResponse.getData().getHotels().get(0);
                                QueryProductResponse.Rates rateCheckInfo = hotelCheckInfo.getRates().get(0);
                                QueryProductResponse.Payment_types checkPriceInfo = rateCheckInfo.getPayment_options().getPayment_types().get(0);
                                return CheckPriceRespDTO.builder()
                                        .checkStatus(true)
                                        .prebookToken(rateCheckInfo.getBook_hash())
                                        .salePrice(new BigDecimal(checkPriceInfo.getAmount()).multiply(BigDecimal.valueOf(100)).intValue())
                                        .totalPriceAfter(new BigDecimal(checkPriceInfo.getAmount()).multiply(BigDecimal.valueOf(100)).intValue())
                                        .totalPriceBefore(new BigDecimal(checkPriceInfo.getAmount()).multiply(BigDecimal.valueOf(100)).intValue())
                                        .message("USD")
                                        .build();
                            }
                        }
                    }
                }
            } else {
                log.info("请求ratehawk查询产品信息异常：request:{},response:{}", JsonUtils.writeObject2Json(queryProductRequest),
                        JsonUtils.writeObject2Json(queryProductResult));
            }
        } catch (Exception e) {
            log.error("验价信息异常：request:{} ", JsonUtils.writeObject2Json(queryProductRequest), e);
        }
        return null;
    }

    public String generateBasicAuth() {
        String credentials = keyId + ":" + apiKey;
        String encodedCredentials = Base64.getEncoder().encodeToString(credentials.getBytes());
        return "Basic " + encodedCredentials;
    }

    public static void main(String[] args) {
        String fileUrl = "https://partner-feedora.s3.eu-central-1.amazonaws.com/feed/partner_feed_en_v3.jsonl.zst";
        String localFilePath = "D:\\working\\file\\导出文件\\hotel_info/" + fileUrl.substring(fileUrl.lastIndexOf("/") + 1);
//        FileDealUtils.downloadFile(fileUrl, localFilePath);
        FileDealUtils.zstdFiles(localFilePath, localFilePath.replace(".jsonl.zst", ""));
    }
}
