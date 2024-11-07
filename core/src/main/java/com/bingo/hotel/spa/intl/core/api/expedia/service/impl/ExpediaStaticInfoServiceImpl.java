package com.bingo.hotel.spa.intl.core.api.expedia.service.impl;

import com.bingo.hotel.base.intl.cli.client.HotelBaseIntlClient;
import com.bingo.hotel.base.intl.cli.enums.ExpediaContinentEnum;
import com.bingo.hotel.base.intl.cli.request.CityInfoRequest;
import com.bingo.hotel.base.intl.cli.request.CountryInfoRequest;
import com.bingo.hotel.base.intl.cli.request.HotelDetailsRequest;
import com.bingo.hotel.info.intl.cli.client.HotelInfoIntlClient;
import com.bingo.hotel.info.intl.cli.request.QueryHotelRequest;
import com.bingo.hotel.info.intl.cli.request.SupplierHotelBaseRequest;
import com.bingo.hotel.info.intl.cli.response.PageResp;
import com.bingo.hotel.info.intl.cli.response.SupplierHotelBaseResponse;
import com.bingo.hotel.info.intl.cli.result.InfoResult;
import com.bingo.hotel.spa.intl.core.api.common.asynchttp.ResponseResult;
import com.bingo.hotel.spa.intl.core.api.common.enums.SupplierSourceEnum;
import com.bingo.hotel.spa.intl.core.api.expedia.access.HotelDetailsAccess;
import com.bingo.hotel.spa.intl.core.api.expedia.access.HotelFileAccess;
import com.bingo.hotel.spa.intl.core.api.expedia.access.HotelRemoveAccess;
import com.bingo.hotel.spa.intl.core.api.expedia.access.QueryProductAccess;
import com.bingo.hotel.spa.intl.core.api.expedia.access.RegionAccess;
import com.bingo.hotel.spa.intl.core.api.expedia.access.RegionsAccess;
import com.bingo.hotel.spa.intl.core.api.expedia.adaptor.ExpediaStaticInfoAdaptor;
import com.bingo.hotel.spa.intl.core.api.expedia.bean.request.HotelInfoRequest;
import com.bingo.hotel.spa.intl.core.api.expedia.bean.request.QueryPriceRequest;
import com.bingo.hotel.spa.intl.core.api.expedia.bean.request.RegionsRequest;
import com.bingo.hotel.spa.intl.core.api.expedia.bean.response.HotelFileResponse;
import com.bingo.hotel.spa.intl.core.api.expedia.bean.response.HotelIdsResponse;
import com.bingo.hotel.spa.intl.core.api.expedia.bean.response.HotelStaticInfo;
import com.bingo.hotel.spa.intl.core.api.expedia.bean.response.QueryPriceResponse;
import com.bingo.hotel.spa.intl.core.api.expedia.bean.response.RegionsInfoResponse;
import com.bingo.hotel.spa.intl.core.api.expedia.service.ExpediaStaticInfoService;
import com.bingo.hotel.spa.intl.core.api.expedia.utils.ExpediaUtils;
import com.bingo.hotel.spa.intl.core.api.expedia.utils.ThreadPoolUtils;
import com.bingo.hotel.spa.intl.core.redis.DistributedRateLimiter;
import com.bingo.hotel.spa.intl.core.util.DateUtil;
import com.bingo.hotel.spa.intl.core.util.FileDealUtils;
import com.bingo.hotel.spa.intl.core.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.stream.Collectors;


@Service
@Slf4j
public class ExpediaStaticInfoServiceImpl implements ExpediaStaticInfoService {

    @Value("${expedia.url.host}")
    String host;
    @Value("${expedia.session}")
    String sessionId;
    @Value("${expedia.ownIp}")
    String ownIp;
    @Value("${expedia.localFilePath}")
    private String LOCAL_FILE_PATH;
    @Value("${expedia.partner_point_of_sale}")
    private String partnerPointOfSale;
    @Value("${expedia.payment_terms}")
    private String paymentTerms;
    @Value("${expedia.billing_terms}")
    private String billingTerms;
    @Resource
    private HotelInfoIntlClient hotelInfoIntlClient;
    @Resource
    private HotelBaseIntlClient hotelBaseIntlClient;
    @Resource
    private ExpediaUtils expediaUtils;
    @Resource
    private DistributedRateLimiter rateLimiter;

    @Override
    public List<String> queryHotelIdByCity(String cityId) {
        List<String> hotelIds = new ArrayList<>();

        RegionsRequest regionsRequest = RegionsRequest.builder().include("property_ids").build();
        ResponseResult<RegionsInfoResponse> result = new RegionAccess(host, "en-US", expediaUtils.signGeneration(), ownIp, sessionId, rateLimiter).access(regionsRequest);
        if (null != result && null != result.getData() && CollectionUtils.isNotEmpty(result.getData().getHotelIds())) {
            hotelIds = result.getData().getHotelIds().stream().map(RegionsInfoResponse.HotelId::getId).collect(Collectors.toList());
        }
        return hotelIds;
    }

    @Override
    public void saveCountryInfo() {

        List<CountryInfoRequest> countryInfoList = new ArrayList<>();

        for (ExpediaContinentEnum expediaContinent : ExpediaContinentEnum.values()) {
            RegionsRequest regionsRequest = RegionsRequest.builder().include("details").build();
            ResponseResult<RegionsInfoResponse> result = new RegionsAccess(host, "en-US", expediaUtils.signGeneration(), ownIp, sessionId,
                    expediaContinent.getKey(), rateLimiter).access(regionsRequest);
            if (null != result && null != result.getData()) {
                RegionsInfoResponse.Descendants descendants = result.getData().getDescendants();
                if (null != descendants && CollectionUtils.isNotEmpty(descendants.getCountry())) {
                    descendants.getCountry().forEach(countryId -> {
                        try {
                            //查询国家信息
                            CountryInfoRequest countryInfoRequest = queryCountryInfo(countryId, expediaContinent.getDesc(), expediaContinent.getDesc_cn());
                            log.info("国家信息展示：{}", JsonUtils.writeObject2Json(countryInfoRequest));
                            countryInfoList.add(countryInfoRequest);
                            if (countryInfoList.size() >= 50) {
                                //分批保存国家信息
                                saveCountryList(countryInfoList);
                                countryInfoList.clear();
                            }
                        } catch (Exception e) {
                            log.error("保存国家信息异常 countryId为: 异常信息：", countryId, e);
                            countryInfoList.clear();
                        }
                    });
                }
            }
        }
        saveCountryList(countryInfoList);
        log.info("保存expedia国家信息完毕");
    }

    private CountryInfoRequest queryCountryInfo(String id, String continent, String continentCN) {
        CountryInfoRequest countryInfo = new CountryInfoRequest()
                .setCountryId(id)
                .setContinent(continent)
                .setContinentCN(continentCN);
        RegionsRequest regionsRequest = RegionsRequest.builder().include("details").build();
        //初次请求获取国家基本信息
        ResponseResult<RegionsInfoResponse> resultUS =
                new RegionsAccess(host, "en-US", expediaUtils.signGeneration(), ownIp, sessionId, id, rateLimiter).access(regionsRequest);
        if (null != resultUS && null != resultUS.getData()) {
            RegionsInfoResponse regionsInfoResponse = resultUS.getData();
            countryInfo
                    .setCountryCode(regionsInfoResponse.getCountry_code())
                    .setCountryName(regionsInfoResponse.getName())
                    .setLongitude(null == regionsInfoResponse.getCoordinates().getCenter_longitude() ? new BigDecimal("0") :
                            new BigDecimal(regionsInfoResponse.getCoordinates().getCenter_longitude()).setScale(10, 6))
                    .setLatitude(null == regionsInfoResponse.getCoordinates().getCenter_latitude() ? new BigDecimal("0") :
                            new BigDecimal(regionsInfoResponse.getCoordinates().getCenter_latitude()).setScale(10, 6));
        }
        //再次请求补全中文字段
        ResponseResult<RegionsInfoResponse> resultCN =
                new RegionsAccess(host, "zh-CN", expediaUtils.signGeneration(), ownIp, sessionId, id, rateLimiter).access(regionsRequest);
        if (null != resultCN && null != resultCN.getData()) {
            RegionsInfoResponse regionsInfoResponse = resultCN.getData();
            countryInfo.setCountryNameCN(regionsInfoResponse.getName());
        }
        countryInfo.setNote(SupplierSourceEnum.EXPEDIA.getDesc());
        return countryInfo;
    }

    @Override
    public void saveCityInfo(List<String> countryIds) {
        RegionsRequest regionsRequest = RegionsRequest.builder().include("details").build();
        if (CollectionUtils.isNotEmpty(countryIds)) {
            pushCountry(regionsRequest, countryIds);
            return;
        }
        for (ExpediaContinentEnum expediaContinent : ExpediaContinentEnum.values()) {
            ResponseResult<RegionsInfoResponse> result = new RegionsAccess(host, "en-US", expediaUtils.signGeneration(), ownIp, sessionId,
                    expediaContinent.getKey(), rateLimiter).access(regionsRequest);
            if (null != result && null != result.getData()) {
                RegionsInfoResponse.Descendants descendants = result.getData().getDescendants();
                if (null != descendants && CollectionUtils.isNotEmpty(descendants.getCountry())) {
                    pushCountry(regionsRequest, descendants.getCountry());
                }
            }
        }
    }

    private void pushCountry(RegionsRequest regionsRequest, List<String> countryIds) {
        countryIds.forEach(countryId -> {
            String nameCN = "";
            ResponseResult<RegionsInfoResponse> resultCN =
                    new RegionsAccess(host, "zh-CN", expediaUtils.signGeneration(), ownIp, sessionId, countryId, rateLimiter).access(regionsRequest);
            if (null != resultCN && null != resultCN.getData()) {
                nameCN = resultCN.getData().getName();
            }
            ResponseResult<RegionsInfoResponse> resultUS =
                    new RegionsAccess(host, "en-US", expediaUtils.signGeneration(), ownIp, sessionId, countryId, rateLimiter).access(regionsRequest);
            if (null != resultUS && null != resultUS.getData() && null != resultUS.getData().getDescendants()) {
                RegionsInfoResponse.Descendants countryDescendants = resultUS.getData().getDescendants();
                if (CollectionUtils.isNotEmpty(countryDescendants.getProvince_state())) {
                    String finalNameCN = nameCN;
                    for (String provinceState : countryDescendants.getProvince_state()) {
                        ThreadPoolUtils.execute(() -> {
                            queryCityInfo(provinceState, countryId, resultUS.getData().getName(), finalNameCN, countryId);
//                        queryCityInfo(countryDescendants.getProvince_state(), countryId, resultUS.getData().getName(), finalNameCN, countryId);
                            log.info("{}-》》》》》》》》下省份全部推送完毕", finalNameCN);
                        });
                    }
                }
//                if (CollectionUtils.isNotEmpty(countryDescendants.getCity())) {
//                    String finalNameCN = nameCN;
//                    ThreadPoolUtils.execute(() -> {
////                        queryCityInfo(countryDescendants.getCity(), countryId, resultUS.getData().getName(), finalNameCN, countryId);
//                        queryCityInfo(countryDescendants.getCity(), countryId, resultUS.getData().getName(), finalNameCN, countryId);
//                        log.info("{}-》》》》》》》》下城市全部推送完毕", finalNameCN);
//                    });
//                }
            }
        });
    }

    /**
     * 自旋等待机制
     */
    private void spinWaitingMechanism() {
        if (ThreadPoolUtils.getThreadPool().getQueue().size() > 10) {
            try {
                log.info("等待上一个国家查询完毕");
                Thread.sleep(20 * 1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            spinWaitingMechanism();
        }
    }


    //    private void queryCityInfo(List<String> cityList, String stateId, String stateName, String stateNameCN, String countryId) {
    private void queryCityInfo(String cityId, String stateId, String stateName, String stateNameCN, String countryId) {

//        log.info("保存cityIds:{} 开始：", cityList.toString());
        List<CityInfoRequest> cityInfoList = new ArrayList<>();

//        cityList.forEach(cityId -> {
        try {
            CityInfoRequest cityInfo = new CityInfoRequest()
                    .setCountryId(countryId)
                    .setCityId(cityId)
                    .setStateId(stateId)
                    .setStateName(stateName)
                    .setStateNameCN(stateNameCN);
            RegionsRequest regionsRequest = RegionsRequest.builder().include("details").build();

            //补充中文名称
            String nameCN = "";
            ResponseResult<RegionsInfoResponse> resultCN =
                    new RegionsAccess(host, "zh-CN", expediaUtils.signGeneration(), ownIp, sessionId, cityId, rateLimiter).access(regionsRequest);
            if (null != resultCN && null != resultCN.getData()) {
                RegionsInfoResponse regionsInfoResponse = resultCN.getData();
                cityInfo.setCityNameCN(regionsInfoResponse.getName());
                nameCN = regionsInfoResponse.getName();
            }
            //补充核心字段
            ResponseResult<RegionsInfoResponse> resultUS =
                    new RegionsAccess(host, "en-US", expediaUtils.signGeneration(), ownIp, sessionId, cityId, rateLimiter).access(regionsRequest);
            if (null != resultUS && null != resultUS.getData()) {
                RegionsInfoResponse regionsInfoResponse = resultUS.getData();
                cityInfo
                        .setCityName(regionsInfoResponse.getName())
                        .setLongitude(null == regionsInfoResponse.getCoordinates() || null == regionsInfoResponse.getCoordinates().getCenter_longitude()
                                ? new BigDecimal("0") : new BigDecimal(regionsInfoResponse.getCoordinates().getCenter_longitude()).setScale(10, 6))
                        .setLatitude(null == regionsInfoResponse.getCoordinates() || null == regionsInfoResponse.getCoordinates().getCenter_latitude() ?
                                new BigDecimal("0") : new BigDecimal(regionsInfoResponse.getCoordinates().getCenter_latitude()).setScale(10, 6))
                        .setNote(regionsInfoResponse.getType());
                if (null != regionsInfoResponse.getDescendants()) {
                    RegionsInfoResponse.Descendants descendants = regionsInfoResponse.getDescendants();
                    String finalNameCN = nameCN;
//                        ThreadPoolUtils.execute(() -> {
                    if (CollectionUtils.isNotEmpty(descendants.getProvince_state())) {
//                                //查询城市信息
//                                queryCityInfo(descendants.getProvince_state(), cityId, regionsInfoResponse.getName(), finalNameCN, countryId);
                        for (String provinceState : descendants.getProvince_state()) {
                            queryCityInfo(provinceState, cityId, regionsInfoResponse.getName(), finalNameCN, countryId);
                        }
                    }
                    if (CollectionUtils.isNotEmpty(descendants.getCity())) {
//                                //查询城市信息
//                                queryCityInfo(descendants.getCity(), cityId, regionsInfoResponse.getName(), finalNameCN, countryId);
                        for (String city : descendants.getCity()) {
                            queryCityInfo(city, cityId, regionsInfoResponse.getName(), finalNameCN, countryId);
                        }
                    }
//                        });
                }
            }
//                saveCityList(Arrays.asList(cityInfo));
            cityInfoList.add(cityInfo);
        } catch (Exception e) {
            log.error("保存城市信息异常 city为:{} 异常信息：", cityId, e);
        }
//        });
        saveCityList(cityInfoList);
        log.info("保存cityIds:{} 完毕：", cityId);
    }

    private void saveCityList(List<CityInfoRequest> cityInfoList) {
        int batchSize = 50;
        int currentBatch = 0;
        //批量方式
        for (int i = 0; i < cityInfoList.size(); i += batchSize) {
            // 截取当前批次的数据
            List<CityInfoRequest> saveCityInfoList = cityInfoList.subList(currentBatch * batchSize, Math.min(cityInfoList.size(), (currentBatch + 1) * batchSize));
            //分批保存城市信息
            try {
                log.info("发起城市请求：{}", saveCityInfoList.size());
                hotelBaseIntlClient.saveCityList(saveCityInfoList);
            } catch (Exception e) {
                log.error("保存城市信息异常 request:{}, 异常信息：", JsonUtils.writeObject2Json(saveCityInfoList), e);
            }
            // 每处理完一组，增加当前批次计数器
            currentBatch++;
        }
//        log.info("国家：{}下地区：{}-》》》》》》》》下城市推送完毕", cityInfoList.get(0).getCountryId(), cityInfoList.get(0).getStateNameCN());
    }

    private void saveCountryList(List<CountryInfoRequest> countryInfoList) {
        try {
            hotelBaseIntlClient.saveCountryList(countryInfoList);
        } catch (Exception e) {
            log.error("保存国家信息异常 request:{}, 异常信息：", JsonUtils.writeObject2Json(countryInfoList), e);
        }
    }

    @Override
    public void saveOrUpdateHotelInfo(boolean downloadFlag, boolean allPushFlag, Integer updateDays, List<String> supplierHotelIds, Integer startLine) {
        //1.请求供应商获取全量酒店信息文件
        HotelInfoRequest hotelInfoRequest = HotelInfoRequest.builder().supply_source(SupplierSourceEnum.EXPEDIA.getDesc()).build();
        ResponseResult<HotelFileResponse> result = new HotelFileAccess(host, "zh-CN", expediaUtils.signGeneration(), ownIp, sessionId, rateLimiter).access(hotelInfoRequest);
        if (null == result.getData() || StringUtils.isBlank(result.getData().getHref())) {
            log.info("请求expedia获取酒店文件接口错误：request:{},response:{}", JsonUtils.writeObject2Json(hotelInfoRequest), JsonUtils.writeObject2Json(result));
            return;
        }
        //2.解析地址下载文件并解压
        String csvUrl = result.getData().getHref();
        String localFilePath = LOCAL_FILE_PATH + csvUrl.substring(csvUrl.lastIndexOf("/"), csvUrl.indexOf("?"));
        if (downloadFlag) {
            FileDealUtils.downloadFile(csvUrl, localFilePath);
            FileDealUtils.gzipFile(localFilePath, localFilePath.replace(".gz", ""));
        }

        //3.解析文件数据并推送保存静态数据
        if (CollectionUtils.isNotEmpty(supplierHotelIds)) {
            pushHotelByHotelId(supplierHotelIds);
        } else {
            parseFile(localFilePath.replace(".gz", ""), allPushFlag, updateDays, startLine);
        }

        log.info("expedia酒店基础处理完毕！");
    }

    private void pushHotelByHotelId(List<String> supplierHotelIds) {
        List<HotelDetailsRequest> hotelDetailsRequests = new ArrayList<>();
        List<SupplierHotelBaseRequest> supplierHotelBaseRequests = new ArrayList<>();
        supplierHotelIds.forEach(supplierHotelId -> {
            pushHotelList(supplierHotelId);
        });
//        hotelBaseIntlClient.saveHotelDetails(hotelDetailsRequests);
//        hotelInfoIntlClient.saveHotelInfo(supplierHotelBaseRequests);
//        hotelInfoIntlClient.saveRoomInfo(supplierHotelBaseRequests.stream().flatMap(supplierHotelBaseRequest -> supplierHotelBaseRequest.getRoomList().stream()).collect(Collectors.toList()));
    }

    private void parseFile(String localFilePath, boolean allPushFlag, Integer updateDays, Integer startLine) {
        try (BufferedReader reader = new BufferedReader(new FileReader(localFilePath))) {
            String line;
//            //base酒店+房型
//            List<HotelDetailsRequest> hotelDetailsRequests = new ArrayList<>();
//            //info酒店+房型
//            List<SupplierHotelBaseRequest> supplierHotelBaseRequests = new ArrayList<>();
            log.info("开始推送酒店信息");
            int sumHotel = 0;
            while ((line = reader.readLine()) != null) {
                sumHotel += 1;
                if (null != startLine && startLine > sumHotel) {
                    continue;
                }
                HotelStaticInfo hotelStaticInfo = JsonUtils.readValue(line, HotelStaticInfo.class);
                // 创建SimpleDateFormat对象，并设置日期时间模式
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
                sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
                try {
                    HotelStaticInfo.Dates dates = hotelStaticInfo.getDates();
                    Date addTime = sdf.parse(dates.getAdded());
                    Date updateTime = sdf.parse(dates.getUpdated());
                    LocalDate currentDate = LocalDate.now().minusDays(updateDays);
                    Date needPushTime = Date.from(currentDate.atStartOfDay(ZoneOffset.ofHours(8)).toInstant());
                    if (!allPushFlag && addTime.getTime() < needPushTime.getTime() && updateTime.getTime() < needPushTime.getTime()) {
                        continue;
                    }
                } catch (Exception e) {
                    log.info("时间转换校验异常", e);
                }
                if (sumHotel % 1000 == 0) {
                    log.info("已经推送酒店总数：{}", sumHotel);
                }
                ThreadPoolUtils.execute(() -> {
                    pushHotelList(hotelStaticInfo.getProperty_id());
                });
            }
//            //保存酒店详情
//            hotelBaseIntlClient.saveHotelDetails(hotelDetailsRequests);
            log.info("酒店静态信息推送完毕,共：{}", sumHotel);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void pushHotelList(String hotelId) {
        HotelInfoRequest hotelInfoRequest =
                HotelInfoRequest.builder().supply_source(SupplierSourceEnum.EXPEDIA.getDesc()).property_id(hotelId).build();
        try {
            long startTime = System.currentTimeMillis();
            ResponseResult<HotelStaticInfo> resultUS =
                    new HotelDetailsAccess(host, "en-US", expediaUtils.signGeneration(), ownIp, sessionId, rateLimiter).access(hotelInfoRequest);
            if (null == resultUS || null == resultUS.getData()) {
                log.info("请求expedia获取酒店英文详情接口错误：request:{},response:{}", JsonUtils.writeObject2Json(hotelInfoRequest), JsonUtils.writeObject2Json(resultUS));
                return;
            }
            ResponseResult<HotelStaticInfo> resultCN =
                    new HotelDetailsAccess(host, "zh-CN", expediaUtils.signGeneration(), ownIp, sessionId, rateLimiter).access(hotelInfoRequest);
            if (null == resultCN || null == resultCN.getData()) {
                log.info("请求expedia获取酒店中文详情接口错误：request:{},response:{}", JsonUtils.writeObject2Json(hotelInfoRequest), JsonUtils.writeObject2Json(resultCN));
                return;
            }
            long endTime = System.currentTimeMillis();
            log.info("查询expedia酒店详情耗时：{}", endTime - startTime);
            //pushBase
            pushBaseHotelDetails(ExpediaStaticInfoAdaptor.transformBaseHotelReq(resultUS.getData(), resultCN.getData()));
            //pushInfo
            pushInfoHotelDetails(resultUS.getData(), resultCN.getData());


        } catch (Exception e) {
            log.error("酒店查询异常，request:{}", JsonUtils.writeObject2Json(hotelInfoRequest), e);
        }
    }

    //    private synchronized void pushBaseHotelDetails(List<HotelDetailsRequest> hotelDetailsRequests, HotelDetailsRequest hotelDetailsRequest) {
//        hotelDetailsRequests.add(hotelDetailsRequest);
//        if (hotelDetailsRequests.size() >= 5) {
//            //保存酒店详情
//            long startTime = System.currentTimeMillis();
//            hotelBaseIntlClient.saveHotelDetails(hotelDetailsRequests);
//            long endTime = System.currentTimeMillis();
//            log.info("推送base酒店详情耗时：{}", endTime - startTime);
//            log.info("推送base酒店id：{}", hotelDetailsRequests.stream().map(HotelDetailsRequest::getHotelId).collect(Collectors.toList()).toString());
//            hotelDetailsRequests.clear();
//        }
//    }
    private void pushBaseHotelDetails(HotelDetailsRequest hotelDetailsRequest) {

        //保存酒店详情
        long startTime = System.currentTimeMillis();
        hotelBaseIntlClient.saveHotelDetails(Arrays.asList(hotelDetailsRequest));
        long endTime = System.currentTimeMillis();
        log.info("推送base酒店详情耗时：{}", endTime - startTime);

    }

    private void pushInfoHotelDetails(HotelStaticInfo hotelStaticInfoUS, HotelStaticInfo hotelStaticInfoCN) {
        SupplierHotelBaseRequest supplierHotelBaseRequest = ExpediaStaticInfoAdaptor.transformInfoHotelReq(hotelStaticInfoUS, hotelStaticInfoCN);
        //保存酒店详情
        long startTime = System.currentTimeMillis();
        hotelInfoIntlClient.saveHotelInfo(Arrays.asList(supplierHotelBaseRequest));
        //保存房型信息
        hotelInfoIntlClient.saveRoomInfo(supplierHotelBaseRequest.getRoomList());
        long endTime = System.currentTimeMillis();
        log.info("推送Info酒店详情耗时：{}", endTime - startTime);

    }

    @Override
    public void deleteHotelInfo(String deleteDate) {

        if (StringUtils.isBlank(deleteDate)) {
            deleteDate = DateUtil.getPastDay("", 7);
        }
        ResponseResult<HotelIdsResponse> result = new HotelRemoveAccess(host, expediaUtils.signGeneration(), ownIp, sessionId, rateLimiter).access(deleteDate);
        if (null == result.getData() || CollectionUtils.isEmpty(result.getData().getHotelIds())) {
            log.info("请求expedia获取酒店文件接口错误：request:{},response:{}", deleteDate, JsonUtils.writeObject2Json(result));
            return;
        }
        List<String> hotelIds = result.getData().getHotelIds();
        int batchSize = 50;
        int currentBatch = 0;
        //批量方式
        for (int i = 0; i < hotelIds.size(); i += batchSize) {
            // 截取当前批次的数据
            List<String> requestHotelIds = hotelIds.subList(currentBatch * batchSize, Math.min(hotelIds.size(), (currentBatch + 1) * batchSize));
            hotelBaseIntlClient.removeHotelDetails(requestHotelIds);
            // 每处理完一组，增加当前批次计数器
            currentBatch++;
        }
    }

    @Override
    public void saveOrUpdateProductInfo(String checkInDate, String checkOutDate, List<String> supplierHotelIds, Integer startNum) {
        if (StringUtils.isBlank(checkInDate) || StringUtils.isBlank(checkOutDate)) {
            checkInDate = DateUtil.getFutureDay(null, 9);
            checkOutDate = DateUtil.getFutureDay(null, 10);
        }
        if (CollectionUtils.isNotEmpty(supplierHotelIds)) {
            pushProductInfo(checkInDate, checkOutDate, supplierHotelIds);
            return;
        }
        int pageNum = null == startNum ? 0 : startNum;
        QueryHotelRequest queryHotelRequest = new QueryHotelRequest().setSupplierId(10005);
        while (true) {
            queryHotelRequest.setPageNum(pageNum).setPageSize(100);
            InfoResult<PageResp<SupplierHotelBaseResponse>> hotelInfoPageListResult = hotelInfoIntlClient.queryHotelPageList(queryHotelRequest);
            if (!hotelInfoPageListResult.isSUCCESS() || null == hotelInfoPageListResult.getData() || CollectionUtils.isEmpty(hotelInfoPageListResult.getData().getList())) {
                log.info("酒店展示集合查询未果，入参：{}，反参：{}", JsonUtils.writeObject2Json(queryHotelRequest), JsonUtils.writeObject2Json(hotelInfoPageListResult));
                return;
            }
            supplierHotelIds = hotelInfoPageListResult.getData().getList().stream().map(SupplierHotelBaseResponse::getSupplierHotelId).collect(Collectors.toList());
            pushProductInfo(checkInDate, checkOutDate, supplierHotelIds);
            pageNum++;
        }
    }

    private void pushProductInfo(String checkInDate, String checkOutDate, List<String> supplierHotelIds) {
        supplierHotelIds.forEach(supplierHotelId -> {
            ThreadPoolUtils.execute(() -> {
                QueryPriceRequest queryPriceRequest = QueryPriceRequest.builder()
                        .property_id(supplierHotelId)
                        .checkin(checkInDate)
                        .checkout(checkOutDate)
                        .currency("USD")
                        .occupancies(Arrays.asList("1"))
                        .sales_environment("hotel_only")
                        .billing_terms(billingTerms)
                        .payment_terms(paymentTerms)
                        .partner_point_of_sale(partnerPointOfSale)
                        .build();
                try {
                    ResponseResult<QueryPriceResponse> resultOnly =
                            new QueryProductAccess(host, "en-US", expediaUtils.signGeneration(), ownIp, sessionId, rateLimiter).access(queryPriceRequest);
                    if (null != resultOnly.getData() && CollectionUtils.isNotEmpty(resultOnly.getData().getHotelPrices())) {
                        //推送base
                        hotelBaseIntlClient.aggregatorProductMapping(ExpediaStaticInfoAdaptor.transformBaseProductReq(resultOnly.getData()));
                        //推送info
                        hotelInfoIntlClient.saveProductInfo(ExpediaStaticInfoAdaptor.transformInfoProductReq(resultOnly.getData()));
                    } else {
                        log.info("请求expedia查询零售价异常：request:{},response:{}", JsonUtils.writeObject2Json(queryPriceRequest),
                                JsonUtils.writeObject2Json(resultOnly));
                    }
                    //查询打包价
                    queryPriceRequest.setSales_environment("hotel_package");
                    ResponseResult<QueryPriceResponse> resultPackage =
                            new QueryProductAccess(host, "en-US", expediaUtils.signGeneration(), ownIp, sessionId, rateLimiter).access(queryPriceRequest);
                    if (null != resultPackage.getData() && CollectionUtils.isNotEmpty(resultPackage.getData().getHotelPrices())) {
                        //推送base
                        hotelBaseIntlClient.aggregatorProductMapping(ExpediaStaticInfoAdaptor.transformBaseProductReq(resultPackage.getData()));
                        //推送info
                        hotelInfoIntlClient.saveProductInfo(ExpediaStaticInfoAdaptor.transformInfoProductReq(resultPackage.getData()));
                    } else {
                        log.info("请求expedia查询打包价异常：request:{},response:{}", JsonUtils.writeObject2Json(queryPriceRequest),
                                JsonUtils.writeObject2Json(resultPackage));
                    }
                } catch (Exception e) {
                    log.error("推送产品信息异常：request:{} ", JsonUtils.writeObject2Json(queryPriceRequest), e);
                }
            });
        });
    }

}
