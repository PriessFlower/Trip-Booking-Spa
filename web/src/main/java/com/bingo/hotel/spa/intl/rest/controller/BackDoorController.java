package com.bingo.hotel.spa.intl.rest.controller;


import com.bingo.hotel.spa.intl.core.api.aichotels.service.AichotelsHotelService;
import com.bingo.hotel.spa.intl.core.api.didatravel.service.DidatravelHotelService;
import com.bingo.hotel.spa.intl.core.api.expedia.service.ExpediaStaticInfoService;
import com.bingo.hotel.spa.intl.core.api.fastpay.service.FastPayService;
import com.bingo.hotel.spa.intl.core.api.huitravel.service.HuiTravelService;
import com.bingo.hotel.spa.intl.core.api.meituan.service.MeituanStaticInfoService;
import com.bingo.hotel.spa.intl.core.api.ratehawk.service.RateHawkCPSQueryPriceService;
import com.bingo.hotel.spa.intl.core.api.ratehawk.service.RateHawkService;
import com.bingo.hotel.spa.intl.core.api.travelconnect.service.TravelconnectHotelService;
import com.bingo.hotel.spa.intl.core.push.fliggy.service.FliggyPushService;
import com.bingo.hotel.spa.intl.rest.common.HttpResponse;
import com.google.common.util.concurrent.RateLimiter;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;


@Slf4j
@RestController
@RequestMapping("/hotel")
public class BackDoorController {
    @Autowired
    private TravelconnectHotelService travelconnectHotelService;
    @Autowired
    private AichotelsHotelService aichotelsHotelService;
    @Resource
    private DidatravelHotelService didatravelHotelService;
    @Autowired
    private HuiTravelService huiTravelService;
    @Autowired
    private ExpediaStaticInfoService expediaStaticInfoService;
    @Autowired
    private FliggyPushService fliggyPushService;
    @Resource
    private FastPayService fastPayStaticInfoService;
    @Resource
    private RateHawkService rateHawkService;
    @Resource
    private MeituanStaticInfoService meituanStaticInfoService;
    @Resource
    private RateHawkCPSQueryPriceService rateHawkCPSQueryPriceService;

//    //qps限流 生产环境2.5  测试环境约0.16（1分钟10次）
//    @Value("${ratehawk.query.price.cache.qps}")
//    private Double cacheQps;

    @GetMapping("/push")
    @ApiOperation("HotelList查询")
    public HttpResponse meituanHotelList(@RequestParam("cityId") String cityId,
                                         @RequestParam("checkIn") String checkIn,
                                         @RequestParam("checkOut") String checkOut
    ) {
        System.out.println("push");
        travelconnectHotelService.getHotelCodeListByCity(cityId, checkIn, checkOut);
        return HttpResponse.getSuccessInstance();
    }

    @GetMapping("/push/AIC")
    @ApiOperation("HotelList查询")
    public HttpResponse aicHotelList(@RequestParam("cityId") String cityId) {
        System.out.println("push");
        aichotelsHotelService.getHotelCodeListByCity(cityId);
        return HttpResponse.getSuccessInstance();
    }

    @GetMapping("/push/DL")
    @ApiOperation("酒店静态数据查询-道旅")
    public HttpResponse hotelListDL(@RequestParam("staticType") String staticType,
                                    @RequestParam("startDate") String startDate,
                                    @RequestParam("endDate") String endDate,
                                    @RequestParam("startNum") int startNum,
                                    @RequestParam("endNum") int endNum,
                                    @RequestParam("downloadFlag") boolean downloadFlag) {
        didatravelHotelService.queryAndSaveStaticInfo(staticType, startDate, endDate, startNum, endNum, downloadFlag);
        return HttpResponse.getSuccessInstance();
    }

    @GetMapping("/push/hui")
    @ApiOperation("HotelList查询")
    public HttpResponse huiTravelHotelList(@RequestParam("countryCode") String countryCode, @RequestParam("cityId") String cityId) {
        System.out.println("push");
        huiTravelService.getHotelCodeListByCity(countryCode, cityId);
        return HttpResponse.getSuccessInstance();
    }

    @GetMapping("/save/expedia/country")
    @ApiOperation("国家静态数据查询-expedia")
    public HttpResponse expediaSaveCountryInfo() {
        expediaStaticInfoService.saveCountryInfo();
        return HttpResponse.getSuccessInstance();
    }

    @GetMapping("/save/expedia/city")
    @ApiOperation("城市静态数据查询-expedia")
    public HttpResponse expediaSaveCityInfo(@RequestParam(value = "countryIds", required = false) List<String> countryIds) {
        expediaStaticInfoService.saveCityInfo(countryIds);
        return HttpResponse.getSuccessInstance();
    }

    @GetMapping("/save/expedia/hotel")
    @ApiOperation("酒店静态数据保存-expedia")
    public HttpResponse expediaSaveHotelInfo(@RequestParam("downloadFlag") boolean downloadFlag,
                                             @RequestParam("allPushFlag") boolean allPushFlag,
                                             @RequestParam("updateDays") Integer updateDays,
                                             @RequestParam(value = "supplierHotelIds", required = false) List<String> supplierHotelIds,
                                             @RequestParam(value = "startLine", required = false) Integer startLine
    ) {
        expediaStaticInfoService.saveOrUpdateHotelInfo(downloadFlag, allPushFlag, updateDays, supplierHotelIds, startLine);
        return HttpResponse.getSuccessInstance();
    }

    @GetMapping("/delete/expedia/hotel")
    @ApiOperation("下架酒店删除-expedia")
    public HttpResponse expediaDeleteHotelInfo(@RequestParam("deleteDate") String deleteDate) {
        expediaStaticInfoService.deleteHotelInfo(deleteDate);
        return HttpResponse.getSuccessInstance();
    }

    @GetMapping("/save/expedia/product")
    @ApiOperation("产品静态数据保存-expedia")
    public HttpResponse expediaSaveProductInfo(@RequestParam("checkInDate") String checkInDate,
                                               @RequestParam("checkOutDate") String checkOutDate,
                                               @RequestParam(value = "supplierHotelIds", required = false) List<String> supplierHotelIds,
                                               @RequestParam(value = "startNum", required = false) Integer startNum) {
        expediaStaticInfoService.saveOrUpdateProductInfo(checkInDate, checkOutDate, supplierHotelIds, startNum);
        return HttpResponse.getSuccessInstance();
    }

    @GetMapping("/fliggy/pushFliggyHotel")
    @ApiOperation("获取飞猪RPId")
    public HttpResponse pushFliggyHotel(@RequestParam("hotelId") String hotelId) throws Exception {
        fliggyPushService.pushFliggyHotel(hotelId);
        return HttpResponse.getSuccessInstance(null);
    }

    @GetMapping("/fliggy/pushFliggyRoom")
    @ApiOperation("获取飞猪RPId")
    public HttpResponse pushFliggyRoom(@RequestParam("hotelId") String hotelId) throws Exception {
        fliggyPushService.pushFliggyRoom(hotelId);
        return HttpResponse.getSuccessInstance(null);
    }

    @GetMapping("/save/fastPayHotels/hotel")
    @ApiOperation("酒店静态数据保存-fastPayHotels")
    public HttpResponse expediaSaveHotelInfo(@RequestParam(name = "days") int days,
                                             @RequestParam("type") String type) {
        fastPayStaticInfoService.saveHotelList(days, type);
        return HttpResponse.getSuccessInstance();
    }

    @GetMapping(value = "/save/rateHawk/hotel")
    @ApiOperation("酒店静态数据保存-rateHawk")
    public HttpResponse rateHawkSaveHotelInfo(@RequestParam(value = "downloadFlag", required = false) Boolean downloadFlag) {
        rateHawkService.queryAndSaveStaticInfo(downloadFlag);
        return HttpResponse.getSuccessInstance();
    }

    @GetMapping(value = "/save/rateHawk/product")
    @ApiOperation("产品静态数据保存-rateHawk")
    public HttpResponse rateHawkSaveProductInfo(@RequestParam(value = "checkInDate", required = false) String checkInDate,
                                                @RequestParam(value = "checkOutDate", required = false) String checkOutDate,
                                                @RequestParam(value = "supplierHotelIds", required = false) List<String> supplierHotelIds,
                                                @RequestParam(value = "startNum", required = false) Integer startNum) {
        rateHawkService.queryAndSaveProductInfo(checkInDate, checkOutDate, supplierHotelIds, startNum);
        return HttpResponse.getSuccessInstance();
    }

    @GetMapping("/meituan/hotel/list")
    @ApiOperation("美团hotelIdList查询")
    public HttpResponse meituanHotelList(@RequestParam("maxId") Long maxId,
                                         @RequestParam("pageSize") Integer pageSize) {

        meituanStaticInfoService.queryHotelIdList(maxId, pageSize);

        return HttpResponse.getSuccessInstance();
    }

    @GetMapping("/meituan/hotel/push")
    @ApiOperation("美团酒店/酒店/房型/产品push")
    public HttpResponse meituanHotelList(@RequestParam(value = "pageNumber", required = false) Integer pageNumber,
                                         @RequestParam(value = "pageSize", required = false) Integer pageSize,
                                         @RequestParam(value = "type", required = false) String type) {

        meituanStaticInfoService.saveOrUpdateHotelInfo(pageNumber, pageSize, type);
        return HttpResponse.getSuccessInstance();
    }

    @GetMapping(value = "/rateHawk/priceCache")
    @ApiOperation("rateHawk价格缓存")
    public HttpResponse priceCache() {
        //qps限流
        RateLimiter rateLimiter = RateLimiter.create(0.16);
        rateHawkCPSQueryPriceService.queryPriceQueueTask(0,0,rateLimiter);
        return HttpResponse.getSuccessInstance();
    }
}
