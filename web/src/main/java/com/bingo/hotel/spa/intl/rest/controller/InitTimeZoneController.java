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

    /**
     * @description:通过可售酒店初始化时区
     * @author: dick_w
     * @date: 2025/1/15 13:46
     * @param: []
     * @return: java.lang.String
     **/
    @RequestMapping("/initCityZone")
    public String initCityZone() {
        initTimeZoneService.initCityZone();
        return "success";
    }

    /**
     * @description:再次初始化时区为空的城市
     * @author: dick_w
     * @date: 2025/1/15 13:46
     * @param: []
     * @return: java.lang.String
     **/
    @RequestMapping("/initCityZoneNone")
    public String initCityZoneNone() {
        initTimeZoneService.initCityZoneNone();
        return "success";
    }
}
