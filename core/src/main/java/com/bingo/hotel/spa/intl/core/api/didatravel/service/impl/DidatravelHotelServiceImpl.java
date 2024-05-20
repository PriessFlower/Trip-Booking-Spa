package com.bingo.hotel.spa.intl.core.api.didatravel.service.impl;

import com.alibaba.fastjson.JSON;
import com.bingo.hotel.base.intl.cli.enums.BedTypeAllEnum;
import com.bingo.hotel.info.intl.cli.client.HotelInfoIntlClient;
import com.bingo.hotel.info.intl.cli.dto.BedInfoDTO;
import com.bingo.hotel.info.intl.cli.enums.BroadnetEnum;
import com.bingo.hotel.info.intl.cli.request.QueryHotelRequest;
import com.bingo.hotel.info.intl.cli.request.SupplierHotelBaseRequest;
import com.bingo.hotel.info.intl.cli.request.SupplierProductBaseRequest;
import com.bingo.hotel.info.intl.cli.request.SupplierRoomBaseRequest;
import com.bingo.hotel.info.intl.cli.response.SupplierHotelBaseResponse;
import com.bingo.hotel.info.intl.cli.result.InfoResult;
import com.bingo.hotel.spa.intl.core.api.common.asynchttp.ResponseResult;
import com.bingo.hotel.spa.intl.core.api.didatravel.access.BedTypeAccess;
import com.bingo.hotel.spa.intl.core.api.didatravel.access.SearchAccess;
import com.bingo.hotel.spa.intl.core.api.didatravel.access.StaticInfoAccess;
import com.bingo.hotel.spa.intl.core.api.didatravel.bean.BedTypeList;
import com.bingo.hotel.spa.intl.core.api.didatravel.bean.CheckPriceResponse;
import com.bingo.hotel.spa.intl.core.api.didatravel.bean.GetBedTypeListRSSuccess;
import com.bingo.hotel.spa.intl.core.api.didatravel.bean.HotelType;
import com.bingo.hotel.spa.intl.core.api.didatravel.bean.HotelTypeRatePlan;
import com.bingo.hotel.spa.intl.core.api.didatravel.bean.QueryBedTypeResponse;
import com.bingo.hotel.spa.intl.core.api.didatravel.bean.UrlDTO;
import com.bingo.hotel.spa.intl.core.api.didatravel.service.DidatravelHotelService;
import com.bingo.hotel.spa.intl.core.redis.DistributedRateLimiter;
import com.bingo.hotel.spa.intl.core.util.JsonUtils;
import com.google.errorprone.annotations.concurrent.LazyInit;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.LineIterator;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author EDY
 */
@Slf4j
@Service
public class DidatravelHotelServiceImpl implements DidatravelHotelService {

    private final static String QUERY_BED_URL = "https://api.didatravel.com/api/staticdata/GetBedTypeList?$format=json";
    private final static String STATIC_INFO_URL = "https://api.didatravel.com/api/staticdata/GetStaticInformation?$format=json";

    private final static String CHECK_PRICE_URL = "https://api.didatravel.com/api/rate/pricesearch?$format=json";

    @Value("${didatravel.localFilePath}")
    private String LOCAL_FILE_PATH;

    private static final String COUNTRIES = "TH,SG,MY,JP,KR,HK,MO,AE,ID";

    @Value("${didatravel.licenseKey}")
    private String LicenseKey;

    @Value("${didatravel.clientId}")
    private String ClientID;

    private static Map<Integer, List<List<BedInfoDTO>>> bedInfoMap;

    public static Map<Integer, List<List<BedInfoDTO>>> getBedInfoMap(String LicenseKey, String ClientID) {
        if (bedInfoMap == null) {
            synchronized (LazyInit.class) {
                if (bedInfoMap == null) {
                    Map<String, Object> mapReq = new HashMap<>();
                    Map<String, Object> headerMap = new HashMap<>();
                    headerMap.put("LicenseKey", LicenseKey);
                    headerMap.put("ClientID", ClientID);
                    mapReq.put("Header", headerMap);
                    ResponseResult<QueryBedTypeResponse> result = new BedTypeAccess(QUERY_BED_URL).access(mapReq);
                    if (null == result.getData() || null == result.getData().getSuccess()) {
                        log.info("请求道旅查询床型接口错误：request:{},response:{}", JsonUtils.writeObject2Json(mapReq), JsonUtils.writeObject2Json(result));
                        return null;
                    }

                    HashMap<Integer, List<List<BedInfoDTO>>> bedListMap = new HashMap<>();
                    GetBedTypeListRSSuccess bedTypeResponse = result.getData().getSuccess();
                    List<BedTypeList> bedTypes = bedTypeResponse.getBedTypes();
                    for (BedTypeList bedType : bedTypes) {
                        if (null == bedType || StringUtils.isBlank(bedType.getName_CN())) {
                            continue;
                        }
                        String[] bedCondition = bedType.getName_CN().replaceAll("(4 TATAMI)", "")
                                .replaceAll("(10 TATAMI)", "")
                                .replaceAll("4大床 2单人床", "4 大床 2 单人床")
                                .replaceAll("1大床1 双人床", "1 大床 1 双人床")
                                .split("或");
                        List<List<BedInfoDTO>> bedInfoDTOSList = new ArrayList<>();
                        for (String s : bedCondition) {
                            List<BedInfoDTO> bedInfoDTOS = new ArrayList<>();
                            String[] bedInfo = s.trim().split(" ");
                            for (int i = 0; i < bedInfo.length - 1; i += 2) {
                                BedInfoDTO bedInfoDTO = new BedInfoDTO()
                                        .setBedType(String.valueOf(BedTypeAllEnum.getValueByDesc(bedInfo[i + 1])))
                                        .setBedNumber(Integer.parseInt(bedInfo[i]))
                                        .setBedDesc(bedInfo[i + 1]);
                                bedInfoDTOS.add(bedInfoDTO);
                            }
                            bedInfoDTOSList.add(bedInfoDTOS);
                        }
                        bedListMap.put(bedType.getID(), bedInfoDTOSList);
                    }
                    bedInfoMap = bedListMap;
                }
            }
        }
        return bedInfoMap;
    }

    @Resource
    private HotelInfoIntlClient hotelInfoIntlClient;

    @Resource
    private DistributedRateLimiter rateLimiter;

    @Override
    public void queryAndSaveStaticInfo(String staticType, String startDate, String endDate, int startNum, int endNum, boolean downloadFlag) {
        //1.组装参数
        Map<String, Object> mapReq = new HashMap<>();
        mapReq.put("IsGetUrlOnly", true);
        Map<String, Object> headerMap = new HashMap<>();
        headerMap.put("LicenseKey", LicenseKey);
        headerMap.put("ClientID", ClientID);
        mapReq.put("Header", headerMap);
        mapReq.put("StaticType", staticType);
        try {
            //2.请求道旅获取文件地址
            ResponseResult<UrlDTO> result = new StaticInfoAccess(STATIC_INFO_URL).access(mapReq);
            if (null == result.getData() || StringUtils.isBlank(result.getData().getUrl())) {
                log.info("请求道旅获取静态数据接口错误：request:{},response:{}", JsonUtils.writeObject2Json(mapReq), JsonUtils.writeObject2Json(result));
                return;
            }
            //3.解析地址下载文件
            String csvUrl = result.getData().getUrl();
            String localFilePath = LOCAL_FILE_PATH + csvUrl.substring(csvUrl.lastIndexOf("/"), csvUrl.indexOf("?"));
            if (downloadFlag) {
                downloadFile(csvUrl, localFilePath);
            }
            //4.解析文件数据并推送base服务
//            readCSVFromURL(localFilePath, staticType);
            readCSVFromFile(localFilePath, staticType, startDate, endDate, startNum, endNum);

            log.info("道旅静态数据处理完毕！");
        } catch (Exception e) {
            log.error("道旅静态数据获取异常", e);
            e.printStackTrace();
        }
    }

    public static void downloadFile(String remoteFilePath, String localFilePath) {
        URL website = null;
        ReadableByteChannel rbc = null;
        FileOutputStream fos = null;
        try {
            website = new URL(remoteFilePath);
            rbc = Channels.newChannel(website.openStream());
            fos = new FileOutputStream(localFilePath);//本地要存储的文件地址 例如：test.txt
            fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (rbc != null) {
                try {
                    rbc.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }

            }
        }
    }

    public void readCSVFromFile(String filePath, String type, String startDate, String endDate, int startNum, int endNum) throws IOException {
        List<String[]> csvData = new ArrayList<>();
        LineIterator lineIterator = null;
        try {
            lineIterator = FileUtils.lineIterator(new File(filePath), "UTF-8");
            int page = 0;
            while (lineIterator.hasNext()) {
                String line = lineIterator.nextLine();
                String[] row = line.split("\\|", -1);
                if (page++ < startNum || page > endNum) {
                    continue;
                }
                csvData.add(row);
                if (csvData.size() >= 5000) {
                    log.info("已经读取csv行数：" + page);
                    if ("HotelSummary".equals(type)) {
                        saveHotelInfoSupplier(csvData);
                    } else if ("RoomTypeAttribute".equals(type)) {
                        saveRoomInfoSupplier(csvData, startDate, endDate);
                    }
                    csvData.clear();
                }
            }
            if ("HotelSummary".equals(type)) {
                saveHotelInfoSupplier(csvData);
            } else if ("RoomTypeAttribute".equals(type)) {
                saveRoomInfoSupplier(csvData, startDate, endDate);
            }
        } finally {
            LineIterator.closeQuietly(lineIterator);
        }
    }

    public void readCSVFromURL(String csvUrl, String type) throws IOException {
        URL url = new URL(csvUrl);
        BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream()));

        List<String[]> csvData = new ArrayList<>();
        String line;
        //过滤标题行
        reader.readLine();
        //读取内容
        while ((line = reader.readLine()) != null) {
            String[] row = line.split("\\|", -1);
            csvData.add(row);
            if (csvData.size() >= 5000) {
                if ("HotelSummary".equals(type)) {
                    saveHotelInfoSupplier(csvData);
                } else if ("RoomTypeAttribute".equals(type)) {
//                    saveRoomInfoSupplier(csvData,);
                }
                csvData.clear();
            }
        }
        if ("HotelSummary".equals(type)) {
            saveHotelInfoSupplier(csvData);
        } else if ("RoomTypeAttribute".equals(type)) {
//            saveRoomInfoSupplier(csvData);
        }
        reader.close();
    }


    public void saveHotelInfoSupplier(List<String[]> csvData) {
        List<SupplierHotelBaseRequest> supplierHotelBaseRequests = new ArrayList<>();
        for (String[] row : csvData) {
            if (StringUtils.isBlank(row[8]) || !COUNTRIES.contains(row[8])) {
                continue;
            }
            try {
                SupplierHotelBaseRequest supplierHotelBaseRequest = new SupplierHotelBaseRequest()
                        .setSupplierId(10003)
                        .setSupplierHotelId(row[0])
                        .setSupplierHotelName(row[1])
                        .setSupplierHotelNameCN(row[2])
                        .setTelephone(row[15])
                        .setPostcode(row[11])
                        .setAddress(row[3])
                        .setAddressCN(row[21])
                        .setCountryCode(row[8])
                        .setCountryName(row[9])
                        .setCountryId(row[10])
                        .setCityId(row[4])
                        .setCityName(row[5])
                        .setCityNameCN(row[6])
                        .setStateName(row[7])
                        .setStateNameCN("")
                        .setLongitude(row[12])
                        .setLatitude(row[13])
//                        .setHotelType()
//                        .setRooms()
//                        .setBrandId()
//                        .setBrandName()
                        .setScore(row[14])
                        .setDescriptions("")
                        .setIntroduceInfo("");
                supplierHotelBaseRequests.add(supplierHotelBaseRequest);
            } catch (Exception e) {
                System.out.println(JSON.toJSONString(row));
                throw e;
            }
            if (supplierHotelBaseRequests.size() >= 50) {
                hotelInfoIntlClient.saveHotelInfo(supplierHotelBaseRequests);
                supplierHotelBaseRequests.clear();
            }
        }
        if (CollectionUtils.isNotEmpty(supplierHotelBaseRequests)) {
            hotelInfoIntlClient.saveHotelInfo(supplierHotelBaseRequests);
        }
        log.info("酒店静态信息落库完毕");
    }

    public void saveRoomInfoSupplier(List<String[]> csvData, String startDate, String endDate) {
        List<SupplierRoomBaseRequest> supplierRoomBaseRequests = new ArrayList<>();
        for (String[] row : csvData) {
            SupplierRoomBaseRequest supplierRoomBaseRequest = new SupplierRoomBaseRequest()
                    .setSupplierId(10003)
                    .setSupplierHotelId(row[0])
                    .setSupplierRoomId(row[1])
                    .setSupplierRoomName(row[2])
                    .setSupplierRoomNameCN(row[3])
                    .setArea(row[7])
                    .setBroadNet("True".equals(row[4]) ? BroadnetEnum.FREE_WIFI.getValue() : BroadnetEnum.NOT.getValue())
                    .setCapacity(StringUtils.isBlank(row[6]) ? 1 : Integer.parseInt(row[6]))
                    .setHasWindows("True".equals(row[5]) ? 1 : 0);
            supplierRoomBaseRequests.add(supplierRoomBaseRequest);
        }
        try {
            List<String> hotelIds =
                    supplierRoomBaseRequests.stream().map(SupplierRoomBaseRequest::getSupplierHotelId).distinct().collect(Collectors.toList());
            QueryHotelRequest queryHotelRequest = new QueryHotelRequest()
                    .setSupplierId(10003)
                    .setSupplierHotelIds(hotelIds);
            InfoResult<List<SupplierHotelBaseResponse>> listHotelBaseResult = hotelInfoIntlClient.queryHotelList(queryHotelRequest);
            List<SupplierHotelBaseResponse> supplierHotelBaseResponseList = listHotelBaseResult.getData();
            if (CollectionUtils.isEmpty(supplierHotelBaseResponseList)) {
                return;
            }
            List<String> supplierHotelIds =
                    supplierHotelBaseResponseList.stream().map(SupplierHotelBaseResponse::getSupplierHotelId).distinct().collect(Collectors.toList());
            Map<String, List<SupplierRoomBaseRequest>> supplierRoomListMap =
                    supplierRoomBaseRequests.stream().filter(r -> supplierHotelIds.contains(r.getSupplierHotelId())).collect(Collectors.groupingBy(SupplierRoomBaseRequest::getSupplierHotelId));
            List<Integer> requestAllHotelIds = supplierRoomListMap.keySet().stream().map(s -> Integer.parseInt(s)).collect(Collectors.toList());
            int batchSize = 30;
            int currentBatch = 0;
            //批量方式
            for (int i = 0; i < requestAllHotelIds.size(); i += batchSize) {
                // 截取当前批次的数据
                List<Integer> requestHotelIds = requestAllHotelIds.subList(currentBatch * batchSize, Math.min(requestAllHotelIds.size(),
                        (currentBatch + 1) * batchSize));
                // 每处理完一组，增加当前批次计数器
                currentBatch++;
                //请求道旅查价
                //1.组装参数
                Map<String, Object> mapReq = new HashMap<>();
                mapReq.put("HotelIDList", requestHotelIds);
                mapReq.put("CheckInDate", startDate);
                mapReq.put("CheckOutDate", endDate);
                mapReq.put("Currency", "CNY");
                mapReq.put("Nationality", "CN");
                Map<String, Object> headerMap = new HashMap<>();
                headerMap.put("LicenseKey", LicenseKey);
                headerMap.put("ClientID", ClientID);
                mapReq.put("Header", headerMap);
                Map<String, Object> isRealTimeMap = new HashMap<>();
                isRealTimeMap.put("Value", false);
                isRealTimeMap.put("RoomCount", 1);
                mapReq.put("IsRealTime", isRealTimeMap);
                //2.请求道旅获取报价信息
                log.info("请求酒店入参：{}", JsonUtils.writeObject2Json(mapReq));
                ResponseResult<CheckPriceResponse> result = new SearchAccess(CHECK_PRICE_URL, rateLimiter).access(mapReq);
                Thread.sleep(500);
                if (null == result.getData() || null == result.getData().getSuccess()) {
                    log.info("请求道旅报价接口错误：request:{},response:{}", JsonUtils.writeObject2Json(mapReq),
                            JsonUtils.writeObject2Json(result));
                    continue;
                }
                List<HotelType> hotelList = result.getData().getSuccess().getPriceDetails().getHotelList();
                log.info("存在报价酒店数量为：{}", hotelList.size());
                for (HotelType hotelType : hotelList) {
                    List<SupplierProductBaseRequest> supplierProductBaseRequests = new ArrayList<>();
                    List<SupplierRoomBaseRequest> supplierRoomBaseSubRequest = supplierRoomListMap.get(String.valueOf(hotelType.getHotelID()));
                    if (CollectionUtils.isEmpty(supplierRoomBaseSubRequest)) {
                        continue;
                    }
                    for (HotelTypeRatePlan hotelTypeRatePlan : hotelType.getRatePlanList()) {
                        if (null == hotelTypeRatePlan.getRoomTypeID()) {
                            continue;
                        }
                        for (SupplierRoomBaseRequest roomBaseRequest : supplierRoomBaseSubRequest) {
                            if (CollectionUtils.isEmpty(roomBaseRequest.getBedInfoList()) && Integer.valueOf(roomBaseRequest.getSupplierRoomId()).equals(hotelTypeRatePlan.getRoomTypeID())) {
                                List<List<BedInfoDTO>> bedList = getBedInfoMap(LicenseKey, ClientID).get(hotelTypeRatePlan.getBedType());
                                if (CollectionUtils.isNotEmpty(bedList)) {
                                    roomBaseRequest.setBedInfoList(bedList);
                                }
                            }
                        }
                        SupplierProductBaseRequest supplierProductBaseRequest = new SupplierProductBaseRequest().setSupplierId(10003)
                                .setSupplierHotelId(String.valueOf(hotelType.getHotelID()))
                                .setSupplierRoomId(String.valueOf(hotelTypeRatePlan.getRoomTypeID()))
                                .setSupplierProductId(String.valueOf(hotelTypeRatePlan.getRatePlanID()))
                                .setSupplierProductName(hotelTypeRatePlan.getRatePlanName())
                                .setSupplierProductNameCN(hotelTypeRatePlan.getRoomName_CN())
                                .setBreakfast(hotelTypeRatePlan.getPriceList().get(0).getMealAmount())
                                .setCancelType(0);
                        supplierProductBaseRequests.add(supplierProductBaseRequest);
                    }
                    log.info("插入房型数量：{}", supplierRoomBaseSubRequest.size());
                    hotelInfoIntlClient.saveRoomInfo(supplierRoomBaseSubRequest);
                    log.info("插入产品数量：{}", supplierProductBaseRequests.size());
                    hotelInfoIntlClient.saveProductInfo(supplierProductBaseRequests);
                }

                //单酒店方式
//                    for (int i = 0; i < requestAllHotelIds.size(); i++) {
//                        //请求道旅查价
//                        //1.组装参数
//                        Map<String, Object> mapReq = new HashMap<>();
//                        mapReq.put("HotelIDList", Arrays.asList(requestAllHotelIds.get(i)));
//                        mapReq.put("CheckInDate", startDate);
//                        mapReq.put("CheckOutDate", endDate);
//                        mapReq.put("Currency", "CNY");
//                        mapReq.put("Nationality", "CN");
//                        Map<String, Object> headerMap = new HashMap<>();
//                        headerMap.put("LicenseKey", LicenseKey);
//                        headerMap.put("ClientID", ClientID);
//                        mapReq.put("Header", headerMap);
//                        Map<String, Object> isRealTimeMap = new HashMap<>();
//                        isRealTimeMap.put("Value", false);
//                        isRealTimeMap.put("RoomCount", 1);
//                        mapReq.put("IsRealTime", isRealTimeMap);
//                        //2.请求道旅获取报价信息
//                        ResponseResult<CheckPriceResponse> result = new SearchAccess(CHECK_PRICE_URL, rateLimiter).access(mapReq);
//                        if (null == result.getData() || null == result.getData().getSuccess()) {
//                            log.info("请求道旅报价接口错误：request:{},response:{}", JsonUtils.writeObject2Json(mapReq),
//                                    JsonUtils.writeObject2Json(result));
//                            continue;
//                        }
//                        if (null == result.getData().getSuccess().getPriceDetails() || CollectionUtils.isEmpty(result.getData().getSuccess().getPriceDetails().getHotelList())) {
//                            log.info("该酒店无报价信息：{}", requestAllHotelIds.get(i));
//                            continue;
//                        }
//                        HotelType hotelType = result.getData().getSuccess().getPriceDetails().getHotelList().get(0);
//                        List<SupplierProductBaseRequest> supplierProductBaseRequests = new ArrayList<>();
//                        List<SupplierRoomBaseRequest> supplierRoomBaseSubRequest = supplierRoomListMap.get(String.valueOf(hotelType.getHotelID()));
//                        if (CollectionUtils.isEmpty(supplierRoomBaseSubRequest)) {
//                            continue;
//                        }
//                        for (HotelTypeRatePlan hotelTypeRatePlan : hotelType.getRatePlanList()) {
//                            if (null == hotelTypeRatePlan.getRoomTypeID()) {
//                                continue;
//                            }
//                            for (SupplierRoomBaseRequest roomBaseRequest : supplierRoomBaseSubRequest) {
//                                if (CollectionUtils.isEmpty(roomBaseRequest.getBedInfoList()) && Integer.valueOf(roomBaseRequest.getSupplierRoomId()).equals(hotelTypeRatePlan.getRoomTypeID())) {
//                                    List<List<BedInfoDTO>> bedList = getBedInfoMap(LicenseKey, ClientID).get(hotelTypeRatePlan.getBedType());
//                                    if (CollectionUtils.isNotEmpty(bedList)) {
//                                        roomBaseRequest.setBedInfoList(bedList);
//                                    }
//                                }
//                            }
//                            SupplierProductBaseRequest supplierProductBaseRequest = new SupplierProductBaseRequest().setSupplierId(10003)
//                                    .setSupplierHotelId(String.valueOf(hotelType.getHotelID()))
//                                    .setSupplierRoomId(String.valueOf(hotelTypeRatePlan.getRoomTypeID()))
//                                    .setSupplierProductId(String.valueOf(hotelTypeRatePlan.getRatePlanID()))
//                                    .setSupplierProductName(hotelTypeRatePlan.getRatePlanName())
//                                    .setSupplierProductNameCN(hotelTypeRatePlan.getRoomName_CN())
//                                    .setBreakfast(hotelTypeRatePlan.getPriceList().get(0).getMealAmount())
//                                    .setCancelType(0);
//                            supplierProductBaseRequests.add(supplierProductBaseRequest);
//                        }
//                        log.info("插入房型数量：{}", supplierRoomBaseSubRequest.size());
//                        hotelInfoIntlClient.saveRoomInfo(supplierRoomBaseSubRequest);
//                        log.info("插入产品数量：{}", supplierProductBaseRequests.size());
//                        hotelInfoIntlClient.saveProductInfo(supplierProductBaseRequests);
//                    }
            }
        } catch (Exception e) {
            log.error("落库房型数据异常", e);
            throw e;
        }
    }

    public static void main(String[] args) {

//        System.out.println(JsonUtils.writeObject2Json(getBedInfoMap("LicenseKey","ClientID")));
//        DidatravelHotelServiceImpl didatravelHotelService = new DidatravelHotelServiceImpl();
//        didatravelHotelService.queryAndSaveStaticInfo("HotelSummary");
//        List<String> str = new ArrayList<>();
//        for (int i = 0; i < 123; i++) {
//            str.add(String.valueOf(i));
//        }
//        int batchSize = 50;
//        int currentBatch = 0;
//
//        for (int i = 0; i < str.size(); i += batchSize) {
//
//            // 清空当前批次的数据
//            List<String> strSub = str.subList(currentBatch * batchSize, Math.min(str.size(), (currentBatch + 1) * batchSize));
//            // 每处理完一组，增加当前批次计数器
//            currentBatch++;
//            System.out.println("1" + strSub);
//            str.set(i, str.get(i) + ",处理中...");
//        }

    }
}
