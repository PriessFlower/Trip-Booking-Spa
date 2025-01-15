package com.bingo.hotel.spa.intl.core.api.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.nacos.client.utils.JSONUtils;
import com.bingo.hotel.base.intl.cli.client.HotelBaseIntlClient;
import com.bingo.hotel.base.intl.cli.response.GetCityInfoBySupplierHotelIdResponse;
import com.bingo.hotel.base.intl.cli.result.BaseResult;
import com.bingo.hotel.spa.intl.core.api.common.bean.CityZone;
import com.bingo.hotel.spa.intl.core.api.common.mapper.InitTimeZoneMapper;
import com.bingo.hotel.spa.intl.core.api.common.mapper.UpHotelMapper;
import com.bingo.hotel.spa.intl.core.api.didatravel.utils.DidaTravelProductConvertUtil;
import com.bingo.hotel.spa.intl.core.api.model.DataRecord;
import com.bingo.hotel.spa.intl.core.api.service.InitTimeZoneService;
import com.bingo.hotel.spa.intl.core.redis.RedisUtils;
import com.ctrip.framework.apollo.spring.annotation.ApolloJsonValue;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

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

    @Autowired
    private UpHotelMapper upHotelMapper;

    @Override
    public void initTimeZone() {
//        new Thread(() -> {
//            while (true){
//                try {
//                    Thread.sleep(10000);
//                } catch (InterruptedException e) {
//                    throw new RuntimeException(e);
//                }
//                initTimeZoneMapper.test();
//            }
//        }).start();
        BaseResult<List<GetCityInfoBySupplierHotelIdResponse>> allCityInfoBySupplierId = hotelBaseIntlClient.getAllCityInfoBySupplierId(supplierQueryTimezone);
        List<GetCityInfoBySupplierHotelIdResponse> data = allCityInfoBySupplierId.getData();
        List<CityZone> cityZoneList = new ArrayList<>();
//        for (int i = 0; i < 10; i++) {
//            GetCityInfoBySupplierHotelIdResponse cityInfo = data.get(i);
//        }
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

    @Override
    @Async
    public void initCityZone() {
        //查询可售酒店id
        List<String> hotelIdList = upHotelMapper.getAllUpHotelList();
        int batchSize = 1000;
        for (int i = 0; i < hotelIdList.size(); i += batchSize) {
            List<String> hotelIds = hotelIdList.subList(i, Math.min(i + batchSize, hotelIdList.size()));
//            System.out.println(hotelIds.stream()
//                    .map(item -> "'" + item + "'") // 为每个元素加单引号
//                    .collect(Collectors.joining(", ")));
            //根据bgOrderId查询城市和国家信息
            List<GetCityInfoBySupplierHotelIdResponse> cityInfos =  getAllCityInfoByHotelIds(hotelIds);
            List<CityZone> cityZoneList = new ArrayList<>();
            List<CityZone> cityZoneListNew = new ArrayList<>();
            //判断城市和国家在表中是否已有数据，如果有则不查询第三方
            for (GetCityInfoBySupplierHotelIdResponse cityInfo : cityInfos) {
                CityZone cityZone = CityZone.builder()
                        .cityName(cityInfo.getCityName())
                        .countryName(cityInfo.getCountryName())
                        .build();
                cityZoneList.add(cityZone);
            }
            excludeData(cityZoneList);
            if(!CollectionUtils.isEmpty(cityZoneList)){
                for (CityZone cityZone : cityZoneList) {
                    DataRecord record = didaTravelProductConvertUtil.getCityInfo(cityZone.getCityName(), cityZone.getCountryName());
                    String timeZone = "";
                    if(record != null){
                        System.out.println(record.getUrl());
                        timeZone = didaTravelProductConvertUtil.getTimeZone(record.getUrl());
                    }
                    if(StringUtils.isNotBlank(timeZone)) {
                        redisUtils.hmSet(TIME_ZONE_KEY_PREFIX + cityZone.getCountryName(), cityZone.getCityName(), timeZone);
                    }
                    cityZone.setTimezone(timeZone);
                    cityZoneListNew.add(cityZone);
//                    try {
//                        Thread.sleep(100);
//                    } catch (InterruptedException e) {
//                        e.printStackTrace();
//                    }
                }
                addDataBaseNew(cityZoneListNew);
            }
//            break;
        }

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
            if(!CollectionUtils.isEmpty(cityZoneList)){
                initTimeZoneMapper.delBatch(cityZoneList);
                initTimeZoneMapper.insertBatch(cityZoneList);
            }
        }

    }

    /**
     * @description:获取cityname和countryname
     * @author: dick_w
     * @date: 2025/1/14 17:33
     * @param: [hotelIds]
     * @return: java.util.List<com.bingo.hotel.base.intl.cli.response.GetCityInfoBySupplierHotelIdResponse>
     **/
    private List<GetCityInfoBySupplierHotelIdResponse> getAllCityInfoByHotelIds(List<String> hotelIds){
        List<GetCityInfoBySupplierHotelIdResponse> getCityInfoBySupplierHotelIdResponses = new ArrayList<>();
        BaseResult<List<GetCityInfoBySupplierHotelIdResponse>> cityInfos = hotelBaseIntlClient.getCityInfoByHotelIds(hotelIds);
        if(cityInfos.isSUCCESS() && !CollectionUtils.isEmpty(cityInfos.getData())){
            getCityInfoBySupplierHotelIdResponses = cityInfos.getData();
        }
        return getCityInfoBySupplierHotelIdResponses;
    }

    /**
     * @description:剔除已存在的数据
     * @author: dick_w 
     * @date: 2025/1/14 16:08
     * @param: [cityZoneList]
     * @return: java.util.List<com.bingo.hotel.spa.intl.core.api.common.bean.CityZone>
     **/
    private List<CityZone> excludeData(List<CityZone> cityZoneList) {
        // 先查询，根据cityZoneList里面的cityName
        List<CityZone> dataBaseList = initTimeZoneMapper.getCityZoneListByCityNamesNew(cityZoneList);
        List<CityZone> delDataList = new ArrayList<>();
        List<CityZone> ignoreDataList = new ArrayList<>();
        //查询到库中存在的，判断是忽略还是删除重插
        for(CityZone cityZone:dataBaseList){
            if(StringUtils.isBlank(cityZone.getTimezone())){
                //需要删除重新插的
                delDataList.add(cityZone);
            }else{
                ignoreDataList.add(cityZone);
            }
        }
        System.out.println("delDataList---"+JSON.toJSONString(delDataList));
        System.out.println("ignoreDataList---"+JSON.toJSONString(ignoreDataList));
        if(!CollectionUtils.isEmpty(delDataList)){
            //删除时区空的数据
            initTimeZoneMapper.delBatchByIds(delDataList);
        }
        if(!CollectionUtils.isEmpty(ignoreDataList)){
            //剔除查询到的数据 但可能时区发生变化了 暂时先不考虑
            ignoreDataList.stream().forEach(a->a.setTimezone(null));
            cityZoneList.removeAll(ignoreDataList);
        }
        System.out.println("cityZoneList---"+JSON.toJSONString(cityZoneList));
        return cityZoneList;
    }

    /**
     * @description:添加数据
     * @author: dick_w 
     * @date: 2025/1/14 16:08
     * @param: [cityZoneList]
     * @return: void
     **/
    private void addDataBaseNew(List<CityZone> cityZoneList) {
        initTimeZoneMapper.insertBatch(cityZoneList);
    }

}
