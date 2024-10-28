package com.bingo.hotel.spa.intl.core.api.expedia.service;

import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * expedia静态信息相关接口.
 *
 * @author : hanJH
 * @version : 1.0 2024/09/03
 * @since : 1.0
 **/
public interface ExpediaStaticInfoService {

    void saveCountryInfo();

    void saveCityInfo(List<String> countryIds);

    void saveOrUpdateHotelInfo(boolean downloadFlag, boolean allPushFlag, Integer updateDays, List<String> supplierHotelIds, Integer startLine);

    void deleteHotelInfo(String deleteDate);

    void saveOrUpdateProductInfo(String checkInDate, String checkOutDate, List<String> supplierHotelIds);
}
