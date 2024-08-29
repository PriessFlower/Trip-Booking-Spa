package com.bingo.hotel.spa.intl.core.api.service.impl;

import com.bingo.hotel.base.intl.cli.client.HotelBaseIntlClient;
import com.bingo.hotel.base.intl.cli.response.GetCityInfoBySupplierHotelIdResponse;
import com.bingo.hotel.base.intl.cli.result.BaseResult;
import com.bingo.hotel.spa.intl.core.api.common.bean.CityZone;
import com.bingo.hotel.spa.intl.core.api.common.mapper.InitTimeZoneMapper;
import com.bingo.hotel.spa.intl.core.api.didatravel.utils.DidaTravelProductConvertUtil;
import com.bingo.hotel.spa.intl.core.api.model.DataRecord;
import com.bingo.hotel.spa.intl.core.api.service.InitTimeZoneService;
import com.bingo.hotel.spa.intl.core.redis.RedisUtils;
import com.ctrip.framework.apollo.spring.annotation.ApolloJsonValue;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * InitTimeZoneServiceImpl
 * @author xrt
 */
@Service
@Slf4j
public class InitTimeZoneServiceImpl implements InitTimeZoneService {

    @Autowired
    private HotelBaseIntlClient hotelBaseIntlClient;

    @Autowired
    private DidaTravelProductConvertUtil didaTravelProductConvertUtil;

    @Autowired
    private RedisUtils redisUtils;

    @Autowired
    private InitTimeZoneMapper initTimeZoneMapper;

    @ApolloJsonValue("${supplier.query.timezone}")
    private List<String> supplierQueryTimezone;


    public static final String TIME_ZONE_KEY_PREFIX = "time_zone:";

    @Override
    public void initTimeZone() {
        BaseResult<List<GetCityInfoBySupplierHotelIdResponse>> allCityInfoBySupplierId = hotelBaseIntlClient.getAllCityInfoBySupplierId(supplierQueryTimezone);
        List<GetCityInfoBySupplierHotelIdResponse> data = allCityInfoBySupplierId.getData();
        List<CityZone> cityZoneList = new ArrayList<>();
        for (GetCityInfoBySupplierHotelIdResponse cityInfo : data) {
            DataRecord record = didaTravelProductConvertUtil.getCityInfo(cityInfo.getCityName(), cityInfo.getCountryName());
            String timeZone = "";
            if(record != null){
                timeZone = didaTravelProductConvertUtil.getTimeZone(record.getUrl());
            }
            if(StringUtils.isNotBlank(timeZone)) {
                redisUtils.hmSet(TIME_ZONE_KEY_PREFIX + cityInfo.getCountryName(), cityInfo.getCityName(), timeZone);
            }
            CityZone cityZone = CityZone.builder()
                    .cityName(cityInfo.getCityName())
                    .countryName(cityInfo.getCountryName())
                    .timezone(timeZone)
                    .build();
            cityZoneList.add(cityZone);
        }
         addDataBase(cityZoneList);
    }

    private void addDataBase(List<CityZone> cityZoneList) { //60
        // 先查询，根据cityZoneList里面的cityName
        List<CityZone> dataBaseList = initTimeZoneMapper.getCityZoneListByCityNames(cityZoneList);
        if(CollectionUtils.isEmpty(dataBaseList)){
            initTimeZoneMapper.insertBatch(cityZoneList);
        }else{
            // 比对
            // 添加
            cityZoneList.removeAll(dataBaseList);
            initTimeZoneMapper.insertBatch(cityZoneList);
        }

    }
}
