package com.bingo.hotel.spa.intl.core.api.ratehawk.service;

/**
 * RateHawk静态信息相关接口.
 *
 * @author : hanJH
 * @version : 1.0 2024/12/06
 * @since : 1.0
 **/
public interface RateHawkService {

    void queryAndSaveStaticInfo(String staticType, String startTime, String endTime, int startNum, int endNum, boolean downloadFlag);

}
