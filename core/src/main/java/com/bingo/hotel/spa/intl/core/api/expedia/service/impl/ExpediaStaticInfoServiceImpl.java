package com.bingo.hotel.spa.intl.core.api.expedia.service.impl;

import com.bingo.hotel.base.intl.cli.client.HotelBaseIntlClient;
import com.bingo.hotel.base.intl.cli.dto.BedInfoDTO;
import com.bingo.hotel.base.intl.cli.dto.GlobalHotelBaseExtendDTO;
import com.bingo.hotel.base.intl.cli.dto.GlobalHotelPictureDTO;
import com.bingo.hotel.base.intl.cli.enums.ExpediaContinentEnum;
import com.bingo.hotel.base.intl.cli.request.CityInfoRequest;
import com.bingo.hotel.base.intl.cli.request.CountryInfoRequest;
import com.bingo.hotel.base.intl.cli.request.HotelDetailsRequest;
import com.bingo.hotel.base.intl.cli.request.RoomBaseRequest;
import com.bingo.hotel.info.intl.cli.client.HotelInfoIntlClient;
import com.bingo.hotel.spa.intl.core.api.common.asynchttp.ResponseResult;
import com.bingo.hotel.spa.intl.core.api.common.enums.SupplierSourceEnum;
import com.bingo.hotel.spa.intl.core.api.expedia.access.HotelDetailsAccess;
import com.bingo.hotel.spa.intl.core.api.expedia.access.HotelFileAccess;
import com.bingo.hotel.spa.intl.core.api.expedia.access.RegionsAccess;
import com.bingo.hotel.spa.intl.core.api.expedia.bean.request.HotelInfoRequest;
import com.bingo.hotel.spa.intl.core.api.expedia.bean.request.RegionsRequest;
import com.bingo.hotel.spa.intl.core.api.expedia.bean.response.HotelFileResponse;
import com.bingo.hotel.spa.intl.core.api.expedia.bean.response.HotelStaticInfo;
import com.bingo.hotel.spa.intl.core.api.expedia.bean.response.RegionsInfoResponse;
import com.bingo.hotel.spa.intl.core.api.expedia.service.ExpediaStaticInfoService;
import com.bingo.hotel.spa.intl.core.api.expedia.utils.ExpediaUtils;
import com.bingo.hotel.spa.intl.core.api.expedia.utils.ThreadPoolUtils;
import com.bingo.hotel.spa.intl.core.redis.DistributedRateLimiter;
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
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicReference;


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
    @Resource
    private HotelInfoIntlClient hotelInfoIntlClient;
    @Resource
    private HotelBaseIntlClient hotelBaseIntlClient;
    @Resource
    private ExpediaUtils expediaUtils;
    @Resource
    private DistributedRateLimiter rateLimiter;

    @Override
    public void saveCountryInfo() {

        ArrayList<CountryInfoRequest> countryInfoList = new ArrayList<>();

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
    public void saveCityInfo() {
        for (ExpediaContinentEnum expediaContinent : ExpediaContinentEnum.values()) {
            List<CityInfoRequest> cityInfoRequestList = Collections.synchronizedList(new ArrayList<>());
            RegionsRequest regionsRequest = RegionsRequest.builder().include("details").build();
            ResponseResult<RegionsInfoResponse> result = new RegionsAccess(host, "en-US", expediaUtils.signGeneration(), ownIp, sessionId,
                    expediaContinent.getKey(), rateLimiter).access(regionsRequest);
            if (null != result && null != result.getData()) {
                RegionsInfoResponse.Descendants descendants = result.getData().getDescendants();
                if (null != descendants && CollectionUtils.isNotEmpty(descendants.getCountry())) {
                    descendants.getCountry().forEach(countryId -> {
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
                                countryDescendants.getProvince_state().forEach(provinceState -> {
                                    ThreadPoolUtils.execute(() -> {
                                        queryCityInfo(provinceState, countryId, resultUS.getData().getName(), finalNameCN, countryId, cityInfoRequestList);
                                    });
                                });
                            }
                        }
                    });
                }
            }
        }
    }

    private void queryCityInfo(String id, String stateId, String stateName, String stateNameCN, String countryId, List<CityInfoRequest> cityInfoList) {
        log.info("保存cityId:{} 开始：", id);
        try {
            CityInfoRequest cityInfo = new CityInfoRequest()
                    .setCountryId(countryId)
                    .setCityId(id)
                    .setStateId(stateId)
                    .setStateName(stateName)
                    .setStateNameCN(stateNameCN);
            RegionsRequest regionsRequest = RegionsRequest.builder().include("details").build();

            //补充中文名称
            String nameCN = "";
            ResponseResult<RegionsInfoResponse> resultCN =
                    new RegionsAccess(host, "zh-CN", expediaUtils.signGeneration(), ownIp, sessionId, id, rateLimiter).access(regionsRequest);
            if (null != resultCN && null != resultCN.getData()) {
                RegionsInfoResponse regionsInfoResponse = resultCN.getData();
                cityInfo.setCityNameCN(regionsInfoResponse.getName());
                nameCN = regionsInfoResponse.getName();
            }
            //补充核心字段
            ResponseResult<RegionsInfoResponse> resultUS =
                    new RegionsAccess(host, "en-US", expediaUtils.signGeneration(), ownIp, sessionId, id, rateLimiter).access(regionsRequest);
            if (null != resultUS && null != resultUS.getData()) {
                RegionsInfoResponse regionsInfoResponse = resultUS.getData();
                cityInfo
                        .setCityName(regionsInfoResponse.getName())
                        .setLongitude(null == regionsInfoResponse.getCoordinates().getCenter_longitude() ? new BigDecimal("0") :
                                new BigDecimal(regionsInfoResponse.getCoordinates().getCenter_longitude()).setScale(10, 6))
                        .setLatitude(null == regionsInfoResponse.getCoordinates().getCenter_latitude() ? new BigDecimal("0") :
                                new BigDecimal(regionsInfoResponse.getCoordinates().getCenter_latitude()).setScale(10, 6))
                        .setNote(regionsInfoResponse.getType());
                if (null != regionsInfoResponse.getDescendants()) {
                    RegionsInfoResponse.Descendants descendants = regionsInfoResponse.getDescendants();
                    String finalNameCN = nameCN;

                    if (CollectionUtils.isNotEmpty(descendants.getProvince_state())) {
                        descendants.getProvince_state().forEach(provinceState -> {
                            //查询城市信息
                            queryCityInfo(provinceState, id, regionsInfoResponse.getName(), finalNameCN, countryId, cityInfoList);
                        });
                    }
                    if (CollectionUtils.isNotEmpty(descendants.getCity())) {
                        descendants.getCity().forEach(cityId -> {
                            //查询城市信息
                            queryCityInfo(cityId, id, regionsInfoResponse.getName(), finalNameCN, countryId, cityInfoList);
                        });
                    }
                }
            }
            cityInfoList.add(cityInfo);
            extracted(cityInfoList);
        } catch (Exception e) {
            log.error("保存城市信息异常 city为:{} 异常信息：", id, e);
        }
        log.info("保存cityId:{} 完毕：", id);
    }

    private synchronized void extracted(List<CityInfoRequest> cityInfoList) {
        if (cityInfoList.size() >= 50) {
            //分批保存城市信息
            saveCityList(cityInfoList);
            cityInfoList.clear();
        }
    }

    private void saveCountryList(List<CountryInfoRequest> countryInfoList) {
        try {
            hotelBaseIntlClient.saveCountryList(countryInfoList);
        } catch (Exception e) {
            log.error("保存国家信息异常 request:{}, 异常信息：", JsonUtils.writeObject2Json(countryInfoList), e);
        }
    }

    private void saveCityList(List<CityInfoRequest> cityInfoList) {
        try {
            log.info("发起城市请求：{}", cityInfoList.size());
            hotelBaseIntlClient.saveCityList(cityInfoList);
        } catch (Exception e) {
            log.error("保存城市信息异常 request:{}, 异常信息：", JsonUtils.writeObject2Json(cityInfoList), e);
        }
    }

    @Override
    public void saveOrUpdateHotelInfo(boolean downloadFlag, boolean allPushFlag, Integer updateDays, List<String> supplierHotelIds) {
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
            parseFile(localFilePath.replace(".gz", ""), allPushFlag, updateDays);
        }

        log.info("expedia酒店基础处理完毕！");
    }

    private void pushHotelByHotelId(List<String> supplierHotelIds) {
        List<HotelDetailsRequest> hotelDetailsRequests = new ArrayList<>();
        supplierHotelIds.forEach(supplierHotelId -> {
            pushHotelList(hotelDetailsRequests, supplierHotelId);
        });
        hotelBaseIntlClient.saveHotelDetails(hotelDetailsRequests);
    }

    public static void main(String[] args) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        String add = "2024-09-10T15:00:00.000Z";
        String update = "2024-09-10T15:41:01.018Z";
        try {
            Date addTime = sdf.parse(add);
            Date updateTime = sdf.parse(update);
            LocalDate currentDate = LocalDate.now().minusDays(3);
            Date needPushTime = Date.from(currentDate.atStartOfDay(ZoneOffset.ofHours(8)).toInstant());
            if (addTime.getTime() < needPushTime.getTime() && updateTime.getTime() < needPushTime.getTime()) {
                System.out.println("不需要上传");
            }
        } catch (Exception e) {
            log.info("时间转行校验异常", e);
        }
    }

    private void parseFile(String localFilePath, boolean allPushFlag, Integer updateDays) {
        try (BufferedReader reader = new BufferedReader(new FileReader(localFilePath))) {
            String line;
            List<HotelDetailsRequest> hotelDetailsRequests = new ArrayList<>();
            log.info("开始推送酒店信息");
            int sumHotel = 0;
            while ((line = reader.readLine()) != null) {
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
                    sumHotel += 1;
                    if (sumHotel % 1000 == 0) {
                        log.info("已经推送酒店总数：{}", sumHotel);
                    }
                } catch (Exception e) {
                    log.info("时间转行校验异常", e);
                }
                ThreadPoolUtils.execute(() -> {
                    pushHotelList(hotelDetailsRequests, hotelStaticInfo.getProperty_id());
                });
            }
            //保存酒店详情
            hotelBaseIntlClient.saveHotelDetails(hotelDetailsRequests);
            log.info("酒店静态信息推送完毕,共：{}", sumHotel);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void pushHotelList(List<HotelDetailsRequest> hotelDetailsRequests, String hotelId) {
        HotelInfoRequest hotelInfoRequest =
                HotelInfoRequest.builder().supply_source(SupplierSourceEnum.EXPEDIA.getDesc()).property_id(hotelId).build();
        try {
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
            pushHotelDetails(hotelDetailsRequests, convertHotelDetails(resultUS.getData(), resultCN.getData()));
        } catch (Exception e) {
            log.error("酒店查询异常，request:{}", JsonUtils.writeObject2Json(hotelInfoRequest), e);
        }
    }

    private synchronized void pushHotelDetails(List<HotelDetailsRequest> hotelDetailsRequests, HotelDetailsRequest hotelDetailsRequest) {
        hotelDetailsRequests.add(hotelDetailsRequest);
        if (hotelDetailsRequests.size() >= 5) {
            //保存酒店详情
            hotelBaseIntlClient.saveHotelDetails(hotelDetailsRequests);
            hotelDetailsRequests.clear();
        }
    }

    private HotelDetailsRequest convertHotelDetails(HotelStaticInfo resultUS, HotelStaticInfo resultCN) {
        HotelDetailsRequest hotelDetailsRequest = new HotelDetailsRequest()
                .setHotelId(resultUS.getProperty_id())
                .setHotelName(resultUS.getName())
                .setHotelNameCN(resultCN.getName())
                .setTelephone(resultUS.getPhone())
                .setPostCode(resultUS.getAddress().getPostal_code())
                .setAddress(resultUS.getAddress().getLine_1())
                .setAddressCN(resultCN.getAddress().getLine_1())
                .setCountryCode(resultUS.getAddress().getCountry_code())
                .setCityName(resultUS.getAddress().getCity())
                .setCityNameCN(resultCN.getAddress().getCity())
                .setStar(null == resultUS.getRatings() || null == resultUS.getRatings().getProperty() || StringUtils.isBlank(resultUS.getRatings().getProperty().getRating()) ? "0" :
                        resultUS.getRatings().getProperty().getRating())
                .setScore(null == resultUS.getRatings() || null == resultUS.getRatings().getGuest() || StringUtils.isBlank(resultUS.getRatings().getGuest().getOverall()) ? "0" :
                        resultUS.getRatings().getGuest().getOverall())
                .setLongitude(String.valueOf(resultUS.getLocation().getCoordinates().getLongitude()))
                .setLatitude(String.valueOf(resultUS.getLocation().getCoordinates().getLatitude()))
                .setGroup(null == resultUS.getChain() ? "" : resultUS.getChain().getName())
                .setBrand(null == resultUS.getBrand() ? "" : resultUS.getBrand().getName());

        //图片集合
        List<GlobalHotelPictureDTO> globalHotelPictures = new ArrayList<>();
        //图片
        if (CollectionUtils.isNotEmpty(resultUS.getImages())) {
            resultUS.getImages().forEach(images -> {
                HotelStaticInfo.UrlInfo urlInfo = null == images.getLinks().get("1000px") ? images.getLinks().get("350px") : images.getLinks().get("1000px");
                if (null != urlInfo) {
                    GlobalHotelPictureDTO globalHotelPictureDTO = new GlobalHotelPictureDTO()
                            .setHotelId(resultUS.getProperty_id())
                            .setType("hotel")
                            .setName(images.getCaption())
                            .setSort(images.getHero_image() ? 0 : 1)
                            .setUrl(urlInfo.getHref());
                    globalHotelPictures.add(globalHotelPictureDTO);
                }
            });
        }
        hotelDetailsRequest.setGlobalHotelPictureDTOS(globalHotelPictures);
        //酒店附属信息集合
        List<GlobalHotelBaseExtendDTO> globalHotelBaseExtends = new ArrayList<>();
        //附属信息
        //英文
        Map<String, String> checkinUS = resultUS.getCheckin();
        GlobalHotelBaseExtendDTO globalHotelBaseExtendUS = new GlobalHotelBaseExtendDTO()
                .setHotelId(resultUS.getProperty_id())
                .setLanguage("en-US")
                .setCheckIn(StringUtils.isBlank(checkinUS.get("24_hour")) ? checkinUS.get("begin_time") + "-" + checkinUS.get("end_time") : checkinUS.get(
                        "24_hour"))
                .setCheckOut(null == resultUS.getCheckout() ? "" : resultUS.getCheckout().getTime())
                .setInstructions(checkinUS.get("instructions") + checkinUS.get("special_instructions"))
                .setMinAge(checkinUS.get("min_age"))
                .setFees(null == resultUS.getFees() ? "" : convertNull(resultUS.getFees().getMandatory()) + convertNull(resultUS.getFees().getOptional()))
                .setPolicies(null == resultUS.getPolicies() ? "" : resultUS.getPolicies().getKnow_before_you_go());
        globalHotelBaseExtends.add(globalHotelBaseExtendUS);
        //中文
        Map<String, String> checkinCN = resultCN.getCheckin();
        GlobalHotelBaseExtendDTO globalHotelBaseExtendCN = new GlobalHotelBaseExtendDTO()
                .setHotelId(resultCN.getProperty_id())
                .setLanguage("zh-CN")
                .setCheckIn(StringUtils.isBlank(checkinCN.get("24_hour")) ? checkinCN.get("begin_time") + "-" + checkinCN.get("end_time") : checkinCN.get("24_hour"))
                .setCheckOut(null == resultUS.getCheckout() ? "" : resultUS.getCheckout().getTime())
                .setInstructions(checkinCN.get("instructions") + checkinCN.get("special_instructions"))
                .setMinAge(checkinCN.get("min_age"))
                .setFees(null == resultUS.getFees() ? "" : convertNull(resultUS.getFees().getMandatory()) + convertNull(resultUS.getFees().getOptional()))
                .setPolicies(null == resultUS.getPolicies() ? "" : resultUS.getPolicies().getKnow_before_you_go());
        globalHotelBaseExtends.add(globalHotelBaseExtendCN);
        hotelDetailsRequest.setGlobalHotelBaseExtendDTOS(globalHotelBaseExtends);

        //房型信息
        List<RoomBaseRequest> roomBaseList = new ArrayList<>();
        Map<String, HotelStaticInfo.Room> roomUSMap = resultUS.getRooms();
        Map<String, HotelStaticInfo.Room> roomCNMap = resultCN.getRooms();
        if (null != roomUSMap && !roomUSMap.isEmpty()) {
            roomUSMap.keySet().forEach(roomId -> {
                HotelStaticInfo.Room roomUS = roomUSMap.get(roomId);
                HotelStaticInfo.Room roomCN = roomCNMap.get(roomId);
                RoomBaseRequest bedInfo = convertBedInfo(roomUS.getBed_groups(), roomCN.getBed_groups());
                RoomBaseRequest roomBaseRequest = new RoomBaseRequest()
                        .setHotelId(resultUS.getProperty_id())
                        .setRoomId(roomId)
                        .setRoomName(roomUS.getName())
                        .setRoomNameCN(convertNull(roomCN.getName()))
                        .setArea(null == roomUS.getArea() ? "0" : String.valueOf(roomUS.getArea().getSquare_meters()))
                        .setBroadnet(0)
                        .setBedType(bedInfo.getBedType())
                        .setBedName(bedInfo.getBedName())
                        .setBedNameCN(bedInfo.getBedNameCN())
                        .setBedDesc(bedInfo.getBedDesc())
                        .setCapacity(roomUS.getOccupancy().getMax_allowed().getTotal())
                        .setHasBathroom(0)
                        .setHasWindows(0)
                        .setIsSmoking(0);
                //房型图片
                List<GlobalHotelPictureDTO> globalRoomPictures = new ArrayList<>();
                if (CollectionUtils.isNotEmpty(roomUS.getImages())) {
                    roomUS.getImages().forEach(images -> {
                        HotelStaticInfo.UrlInfo urlInfo = null == images.getLinks().get("1000px") ? images.getLinks().get("350px") : images.getLinks().get("1000px");
                        if (null != urlInfo) {
                            GlobalHotelPictureDTO globalRoomPictureDTO = new GlobalHotelPictureDTO()
                                    .setHotelId(resultUS.getProperty_id())
                                    .setRoomId(roomId)
                                    .setType("hotel")
                                    .setName(images.getCaption())
                                    .setSort(images.getHero_image() ? 0 : 1)
                                    .setUrl(urlInfo.getHref());
                            globalRoomPictures.add(globalRoomPictureDTO);
                        }
                    });
                }
                roomBaseRequest.setGlobalRoomPictureDTOS(globalRoomPictures);
                roomBaseList.add(roomBaseRequest);
            });
        }
        hotelDetailsRequest.setRoomBaseList(roomBaseList);
        return hotelDetailsRequest;
    }

    private RoomBaseRequest convertBedInfo(Map<String, HotelStaticInfo.BedGroup> bed_groups_us, Map<String, HotelStaticInfo.BedGroup> bed_groups_cn) {
        Set<String> bedTypeSet = new HashSet<>();
        AtomicReference<String> bedNameUS = new AtomicReference<>("");
        AtomicReference<String> bedNameCN = new AtomicReference<>("");
        List<List<BedInfoDTO>> bedInfosList = new ArrayList<>();
        if (null != bed_groups_us && !bed_groups_us.isEmpty()) {
            bed_groups_us.keySet().forEach(bedId -> {
                List<BedInfoDTO> bedInfoDTOS = new ArrayList<>();
                HotelStaticInfo.BedGroup bedGroupUS = bed_groups_us.get(bedId);
                HotelStaticInfo.BedGroup bedGroupCN = bed_groups_cn.get(bedId);
                bedNameUS.set(StringUtils.isBlank(bedNameUS.get()) ? bedGroupUS.getDescription() : bedNameUS + "或" + bedGroupUS.getDescription());
                bedNameCN.set(StringUtils.isBlank(bedNameCN.get()) ? bedGroupCN.getDescription() : bedNameCN + "或" + bedGroupCN.getDescription());
                bedGroupUS.getConfiguration().forEach(bedInfo -> {
                    bedTypeSet.add(bedInfo.getType());
                    BedInfoDTO bedInfoDTO = new BedInfoDTO()
                            .setBedNumber(bedInfo.getQuantity())
                            .setBedDesc(bedInfo.getType())
                            .setBedType(bedInfo.getSize());
                    bedInfoDTOS.add(bedInfoDTO);
                });
                bedInfosList.add(bedInfoDTOS);
            });
        }
        RoomBaseRequest bedInfo = new RoomBaseRequest()
                .setBedType(CollectionUtils.isEmpty(bedTypeSet) ? "" : bedTypeSet.toString())
                .setBedName(bedNameUS.get())
                .setBedNameCN(bedNameCN.get())
                .setBedDesc(JsonUtils.writeObject2Json(bedInfosList));
        return bedInfo;
    }

    private String convertNull(String str) {
        if (StringUtils.isBlank(str)) {
            return "";
        }
        return str;
    }

    @Override
    public void deleteHotelInfo(String deleteDate) {

    }

}
