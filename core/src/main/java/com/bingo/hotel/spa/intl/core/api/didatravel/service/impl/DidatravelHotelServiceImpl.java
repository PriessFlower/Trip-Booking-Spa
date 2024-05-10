package com.bingo.hotel.spa.intl.core.api.didatravel.service.impl;

import com.bingo.hotel.spa.intl.core.api.common.asynchttp.ResponseResult;
import com.bingo.hotel.spa.intl.core.api.didatravel.access.StaticInfoAccess;
import com.bingo.hotel.spa.intl.core.api.didatravel.bean.UrlDTO;
import com.bingo.hotel.spa.intl.core.api.didatravel.service.DidatravelHotelService;
import com.bingo.hotel.spa.intl.core.api.travelconnect.bean.search.response.SearchResponse;
import com.bingo.hotel.spa.intl.core.util.JsonUtils;
import com.google.common.collect.Maps;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * @author EDY
 */
@Slf4j
@Service
public class DidatravelHotelServiceImpl implements DidatravelHotelService {

    private static final String STATIC_INFO_URL = "https://api.didatravel.com/api/staticdata/GetStaticInformation?$format=json";


    @Override
    public void queryAndSaveStaticInfo(String staticType) {
        Map<String, Object> mapReq = new HashMap<>();
        mapReq.put("IsGetUrlOnly", true);
        Map<String, Object> headerMap = new HashMap<>();
        headerMap.put("LicenseKey", "BSYX_key0509");
        headerMap.put("ClientID", "BSYX");
        mapReq.put("Header", headerMap);
        mapReq.put("StaticType", staticType);
        ResponseResult<UrlDTO> access = new StaticInfoAccess(STATIC_INFO_URL).access(mapReq);
        log.info("获取url完毕" + JsonUtils.writeObject2Json(access.getData()));
    }

    public static void main(String[] args) {
        DidatravelHotelServiceImpl didatravelHotelService = new DidatravelHotelServiceImpl();
        didatravelHotelService.queryAndSaveStaticInfo("HotelSummary");
    }
}
