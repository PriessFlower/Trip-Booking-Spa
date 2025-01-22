package com.bingo.hotel.spa.intl.core.api.service;

import org.springframework.web.bind.annotation.RequestParam;

/**
 * InitTimeZoneService
 * @author xrt
 */
public interface InitTimeZoneService {
    void initTimeZone();

    void initCityZone();

    void initCityZoneNone();

    String getCityZoneByHotelId(String timeZone,String hotelId,Integer supplierId);

    void initDatabaseToRedis();
}
