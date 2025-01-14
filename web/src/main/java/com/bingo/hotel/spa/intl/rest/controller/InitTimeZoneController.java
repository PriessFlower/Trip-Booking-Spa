package com.bingo.hotel.spa.intl.rest.controller;

import com.bingo.hotel.spa.intl.core.api.service.InitTimeZoneService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * 初始化时区控制器
 * @author xrt
 */
@RestController
public class InitTimeZoneController {

    @Resource
    private InitTimeZoneService initTimeZoneService;

    @RequestMapping("/initTimeZone")
    public String initTimeZone() {
        initTimeZoneService.initTimeZone();
        return "success";
    }


    @RequestMapping("/initCityZone")
    public String initCityZone() {
        initTimeZoneService.initCityZone();
        return "success";
    }
}
