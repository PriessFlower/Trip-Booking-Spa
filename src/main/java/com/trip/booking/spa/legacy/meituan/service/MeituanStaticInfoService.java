package com.trip.booking.spa.legacy.meituan.service;

/**
 * expedia静态信息相关接口.
 *
 * @author : hanJH
 * @version : 1.0 2024/09/03
 * @since : 1.0
 **/
public interface MeituanStaticInfoService {

    void queryHotelIdList(Long maxId, Integer pageSize);

    void saveOrUpdateHotelInfo(Integer pageNumber, Integer pageSize, String type);
}
