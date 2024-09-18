package com.bingo.hotel.spa.intl.core.api.expedia.service;

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

    void saveCityInfo();

    void saveOrUpdateHotelInfo(boolean downloadFlag, boolean allPushFlag, Integer updateDays, List<String> supplierHotelIds);

    void deleteHotelInfo(String deleteDate);
}
