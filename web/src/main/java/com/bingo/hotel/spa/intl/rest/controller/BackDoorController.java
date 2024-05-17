package com.bingo.hotel.spa.intl.rest.controller;


import com.bingo.hotel.spa.intl.core.api.aichotels.service.AichotelsHotelService;
import com.bingo.hotel.spa.intl.core.api.didatravel.service.DidatravelHotelService;
import com.bingo.hotel.spa.intl.core.api.travelconnect.service.TravelconnectHotelService;
import com.bingo.hotel.spa.intl.rest.common.HttpResponse;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;


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
                                    @RequestParam("startNum") int startNum) {
        didatravelHotelService.queryAndSaveStaticInfo(staticType, startDate, endDate, startNum);
        return HttpResponse.getSuccessInstance();
    }

}
