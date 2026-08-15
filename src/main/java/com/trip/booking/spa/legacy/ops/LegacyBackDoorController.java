package com.trip.booking.spa.legacy.ops;


import com.trip.booking.spa.legacy.aichotels.service.AichotelsHotelService;
import com.trip.booking.spa.legacy.didatravel.service.DidatravelHotelService;
import com.trip.booking.spa.legacy.fastpay.service.FastPayService;
import com.trip.booking.spa.legacy.huitravel.service.HuiTravelService;
import com.trip.booking.spa.legacy.meituan.service.MeituanStaticInfoService;
import com.trip.booking.spa.legacy.ratehawk.service.RateHawkCPSQueryPriceService;
import com.trip.booking.spa.legacy.ratehawk.service.RateHawkService;
import com.trip.booking.spa.legacy.travelconnect.service.TravelconnectHotelService;
import com.trip.booking.spa.gateway.adapter.inbound.rest.common.HttpResponse;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;


/**
 * 旧供应商的运维后门端点，自 BackDoorController 原样拆出（URL 不变，行为不变）。
 * 随各家迁移逐个删除；最后一家迁完本类整体消失。
 */
@Slf4j
@RestController
@RequestMapping("/hotel")
public class LegacyBackDoorController {
    @Autowired
    private TravelconnectHotelService travelconnectHotelService;
    @Autowired
    private AichotelsHotelService aichotelsHotelService;
    @Resource
    private DidatravelHotelService didatravelHotelService;
    @Autowired
    private HuiTravelService huiTravelService;
    @Resource
    private FastPayService fastPayStaticInfoService;
    @Resource
    private RateHawkService rateHawkService;
    @Resource
    private MeituanStaticInfoService meituanStaticInfoService;
    @Resource
    private RateHawkCPSQueryPriceService rateHawkCPSQueryPriceService;

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

    /**
     * 手动触发一轮 RateHawk 刷价。速率、批量与互斥锁由 Service 统一解析，与定时调度一致。
     *
     * <p>本入口不受 task.ratehawk-cps.enabled 约束：定时关闭期间手动补刷是正当运维场景。
     * 该绕过以 warn 日志留痕（PROJECT.md §3.8.4）。
     */
    @GetMapping(value = "/rateHawk/priceCache")
    @ApiOperation("rateHawk价格缓存-手动触发一轮")
    public HttpResponse priceCache() {
        log.warn("[gate] 绕过 task.ratehawk-cps.enabled：BackDoor 手动触发一轮刷价");
        rateHawkCPSQueryPriceService.queryPriceQueueTask(0, 0, "manual");
        return HttpResponse.getSuccessInstance();
    }
}
