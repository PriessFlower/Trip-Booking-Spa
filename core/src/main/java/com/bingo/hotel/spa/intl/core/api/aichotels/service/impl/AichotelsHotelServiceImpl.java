package com.bingo.hotel.spa.intl.core.api.aichotels.service.impl;

import com.bingo.hotel.info.intl.cli.client.HotelInfoIntlClient;
import com.bingo.hotel.info.intl.cli.request.SupplierHotelBaseRequest;
import com.bingo.hotel.info.intl.cli.request.SupplierRoomBaseRequest;
import com.bingo.hotel.info.intl.cli.result.InfoResult;
import com.bingo.hotel.spa.intl.cli.seq.PriceReq;
import com.bingo.hotel.spa.intl.core.api.aichotels.Encryption;
import com.bingo.hotel.spa.intl.core.api.aichotels.access.AvailabilityAccess;
import com.bingo.hotel.spa.intl.core.api.aichotels.access.CityListAccess;
import com.bingo.hotel.spa.intl.core.api.aichotels.access.HotelListAccess;
import com.bingo.hotel.spa.intl.core.api.aichotels.access.RoomInfoAccess;
import com.bingo.hotel.spa.intl.core.api.aichotels.access.SingleHotelAccess;
import com.bingo.hotel.spa.intl.core.api.aichotels.adaptor.AichotelsHotelAdaptor;
import com.bingo.hotel.spa.intl.core.api.aichotels.adaptor.AichotelsRoomAdaptor;
import com.bingo.hotel.spa.intl.core.api.aichotels.bean.hotel.city.CityListResponse;
import com.bingo.hotel.spa.intl.core.api.aichotels.bean.hotel.list.HotelListResponse;
import com.bingo.hotel.spa.intl.core.api.aichotels.bean.hotel.room.RoomInfoResponse;
import com.bingo.hotel.spa.intl.core.api.aichotels.bean.hotel.single.SingleHotelResponse;
import com.bingo.hotel.spa.intl.core.api.aichotels.bean.price.availability.AvailabilityRequest;
import com.bingo.hotel.spa.intl.core.api.aichotels.bean.price.availability.AvailabilityResponse;
import com.bingo.hotel.spa.intl.core.api.aichotels.service.AichotelsHotelService;
import com.bingo.hotel.spa.intl.core.api.aichotels.utils.AichotelsProductConvertUtil;
import com.bingo.hotel.spa.intl.core.api.common.asynchttp.ResponseResult;
import com.bingo.hotel.spa.intl.core.api.travelconnect.bean.search.response.SearchResponse;
import com.bingo.hotel.spa.intl.core.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class AichotelsHotelServiceImpl implements AichotelsHotelService {
    @Value("${aichotels.url.host}")
    String host;
    @Value("${aichotels.url.cityList}")
    String cityList;
    @Value("${aichotels.url.hotelList}")
    String hotelListHost;

    @Value("${aichotels.url.singleHotel}")
    String singleHotel;

    @Value("${aichotels.url.rooms}")
    String roomsUrl;

    @Value("${aichotels.url.availability}")
    String availability;

    @Value("${aichotels.apiClientKey}")
    String apiClientKey;

    @Value("${aichotels.secret}")
    String secret;
    @Autowired
    private HotelInfoIntlClient hotelInfoIntlClient;

    @Override
    public void getHotelCodeListByCity(String city) {
        String date = Encryption.getDate();
        String cityApiClientToken = Encryption.generateSignature("GET", cityList + "/" + city, date, secret);
        ResponseResult<CityListResponse> cityResponse = new CityListAccess(host + cityList + "/" + city, apiClientKey, date, cityApiClientToken).access(null);
        for (CityListResponse.CityListBean cityListBean : cityResponse.getData().getCity_list()) {
            String apiClientToken = Encryption.generateSignature("GET", hotelListHost + "/" + cityListBean.getCity_id(), date, secret);
            ResponseResult<HotelListResponse> response = new HotelListAccess(host + hotelListHost + "/" + cityListBean.getCity_id(), apiClientKey, date, apiClientToken).access(null);
            List<SupplierHotelBaseRequest> request = new ArrayList<>();
            List<SupplierRoomBaseRequest> supplierRoomBaseRequest = new ArrayList<>();
            for (HotelListResponse.HotelinfoListBean hotelinfoListBean : response.getData().getHotelinfo_list()) {
                String singleHotelApiClientToken = Encryption.generateSignature("GET", singleHotel + "/" + hotelinfoListBean.getHotel_id(), date, secret);
                ResponseResult<SingleHotelResponse> singleHotelResponse = new SingleHotelAccess(host + singleHotel + "/" + hotelinfoListBean.getHotel_id() + "?" + "locale=zh_CN", apiClientKey, date, singleHotelApiClientToken).access(null);
                request.addAll(AichotelsHotelAdaptor.transform(singleHotelResponse.getData()));
                String roomClientToken = Encryption.generateSignature("GET", roomsUrl + "/" + hotelinfoListBean.getHotel_id(), date, secret);
                ResponseResult<RoomInfoResponse> roomInfoResponse = new RoomInfoAccess(host + roomsUrl + "/" + hotelinfoListBean.getHotel_id(), apiClientKey, date, roomClientToken).access(null);
                supplierRoomBaseRequest.addAll(AichotelsRoomAdaptor.transform(roomInfoResponse.getData()));
            }
            InfoResult infoResult = hotelInfoIntlClient.saveHotelInfo(request);
            InfoResult roomResult = hotelInfoIntlClient.saveRoomInfo(supplierRoomBaseRequest);
        }
    }

    @Override
    public AvailabilityResponse getHotelPrice(PriceReq priceReq, String sHotelId) {
        String date = Encryption.getDate();
        String apiClientToken = Encryption.generateSignature("POST", availability, date, secret);
        AvailabilityRequest availabilityRequest = AvailabilityRequest.builder()
                .check_in(priceReq.getCheckIn())
                .check_out(priceReq.getCheckout())
                .hotel_id(Integer.parseInt(sHotelId))
                .room_number(priceReq.getRoomNum())
                .adult_number(priceReq.getAdultNum())
                .kids_number(priceReq.getChildNum()).build();
        ResponseResult<AvailabilityResponse> roomInfoResponse = new AvailabilityAccess(host + availability, apiClientKey, date, apiClientToken).access(availabilityRequest);
        return roomInfoResponse.getData();
    }

    public static void main(String[] args) {
        String date = Encryption.getDate();
        String apiClientToken = Encryption.generateSignature("GET", "/content/public/hotel_rooms/114312", date, "UBiz3ZX58INhgioxv9ToAK2VqbQRe3f3Fp8v");
        ResponseResult<RoomInfoResponse> response = new RoomInfoAccess("https://api-uat.aichotels.net.cn/content/public/hotel_rooms/114312?locale=zh_CN", "BaoShengTest", date, apiClientToken).access(null);
        System.out.println(JsonUtils.writeObject2Json(response));
    }
}
