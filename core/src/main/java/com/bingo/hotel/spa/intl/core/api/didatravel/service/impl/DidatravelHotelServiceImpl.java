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
import com.bingo.hotel.spa.intl.core.api.didatravel.bean.price.DidaTravelRequest;
import com.bingo.hotel.spa.intl.core.api.didatravel.bean.price.DidaTravelResponse;
import com.bingo.hotel.spa.intl.core.api.didatravel.bean.price.priceConfirm.PriceConfirmRequest;
import com.bingo.hotel.spa.intl.core.api.didatravel.bean.price.priceConfirm.PriceConfirmResponse;
import com.bingo.hotel.spa.intl.core.api.didatravel.service.DidatravelHotelService;
import com.bingo.hotel.spa.intl.core.api.travelconnect.bean.search.response.SearchResponse;
import com.bingo.hotel.spa.intl.core.util.JsonUtils;
import com.google.common.collect.Maps;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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
    private static final String PRICE_URL = "https://api.didatravel.com/api/rate/pricesearch?$format=json";
    private static final String PRICECONFRIM_URL = "https://api.didatravel.com/api/rate/PriceConfirm?$format=json";


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

    @Override
    public DidaTravelResponse getHotelService(PriceReq priceReq, String sHotelId) {
        Map<String, Object> mapReq = new HashMap<>();
        mapReq.put("IsGetUrlOnly", true);
        DidaTravelRequest.HeaderType headerType = new DidaTravelRequest.HeaderType();
        headerType.setLicenseKey("BSYX_textkey");
        headerType.setClientID("BSYX_text");

        ArrayList<Integer> list = new ArrayList<>();
        list.add(Integer.parseInt(sHotelId));

        DidaTravelRequest.PriceSearchRequestIsRealTime priceSearchRequestIsRealTime = new DidaTravelRequest.PriceSearchRequestIsRealTime();
        priceSearchRequestIsRealTime.setValue(true);
        priceSearchRequestIsRealTime.setRoomCount(priceReq.getRoomNum());

        DidaTravelRequest.PriceSearchRequestRealTimeOccupancy priceSearchRequestRealTimeOccupancy = new DidaTravelRequest.PriceSearchRequestRealTimeOccupancy();
        priceSearchRequestRealTimeOccupancy.setAdultCount(priceReq.getAdultNum());
        priceSearchRequestRealTimeOccupancy.setChildCount(priceReq.getChildNum());
        priceSearchRequestRealTimeOccupancy.setChildAgeDetails(priceReq.getChildNum() == 0 ? new ArrayList<>() : priceReq.getChildAges());


        DidaTravelRequest didaTravelRequest = DidaTravelRequest.builder()
                .Header(headerType)
                .HotelIDList(list)
                .CheckInDate(priceReq.getCheckIn())
                .CheckOutDate(priceReq.getCheckout())
                .IsRealTime(priceSearchRequestIsRealTime)
                .RealTimeOccupancy(priceSearchRequestRealTimeOccupancy)
                .Currency("CNY")
                .Nationality("CN")
                .IsNeedOnRequest(false)
                .build();
        ResponseResult<DidaTravelResponse> access = new DidaTravelAccess(PRICE_URL).access(didaTravelRequest);

//        return JsonUtils.readValue(access.getOrigData(), DidaTravelResponse.class);
        return access.getData();
    }

    @Override
    public PriceConfirmResponse checkPrice(CheckPriceReq checkPriceReq) {
        Map<String, Object> mapReq = new HashMap<>();
        mapReq.put("IsGetUrlOnly", true);
        PriceConfirmRequest.HeaderType headerType = new PriceConfirmRequest.HeaderType();
        headerType.setLicenseKey("BSYX_textkey");
        headerType.setClientID("BSYX_text");

        PriceConfirmRequest.RoomOccupancyType roomOccupancyType = new PriceConfirmRequest.RoomOccupancyType();
        roomOccupancyType.setAdultCount(checkPriceReq.getAdultCount());
        roomOccupancyType.setChildCount(0);
        roomOccupancyType.setRoomNum(1);
        roomOccupancyType.setChildAgeDetails(new ArrayList<>());

        ArrayList<PriceConfirmRequest.RoomOccupancyType> roomOccupancyTypeList = new ArrayList<>();
        roomOccupancyTypeList.add(roomOccupancyType);

        PriceConfirmRequest priceConfirmRequest = PriceConfirmRequest.builder()
                .Header(headerType)
                .PreBook(true)
                .CheckInDate(checkPriceReq.getCheckIn())
                .CheckOutDate(checkPriceReq.getCheckOut())
                .NumOfRooms(checkPriceReq.getRoomNum())
                .HotelID(Integer.valueOf(checkPriceReq.getSHotelId()))
                .OccupancyDetails(roomOccupancyTypeList)
                .Currency("CNY")
                .Nationality("CN")
                .RatePlanID(checkPriceReq.getSProductId())
                .IsNeedOnRequest(false)
                .build();

        ResponseResult<PriceConfirmResponse> access = new PriceConfirmAccess(PRICECONFRIM_URL).access(priceConfirmRequest);
        return access.getData();
    }

    public static void main(String[] args) {
        DidatravelHotelServiceImpl didatravelHotelService = new DidatravelHotelServiceImpl();
        didatravelHotelService.queryAndSaveStaticInfo("HotelSummary");
    }
}
