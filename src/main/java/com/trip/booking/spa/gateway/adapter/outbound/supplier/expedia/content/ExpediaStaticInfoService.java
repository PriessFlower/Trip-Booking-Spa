package com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.content;

import java.util.List;

/**
 * expedia静态信息相关接口.
 *
 * @author : hanJH
 * @version : 1.0 2024/09/03
 * @since : 1.0
 **/
public interface ExpediaStaticInfoService {

    List<String> queryHotelIdByCity(String cityId);

    void saveCountryInfo();

    void saveCityInfo(List<String> countryIds);

    void saveOrUpdateHotelInfo(boolean downloadFlag, boolean allPushFlag, Integer updateDays, List<String> supplierHotelIds, Integer startLine);

    void deleteHotelInfo(String deleteDate);

    void saveOrUpdateProductInfo(String checkInDate, String checkOutDate, List<String> supplierHotelIds, Integer startNum);
}
