package com.bingo.hotel.spa.intl.core.api.huitravel.service.impl;


import com.bingo.hotel.info.intl.cli.client.HotelInfoIntlClient;
import com.bingo.hotel.info.intl.cli.result.InfoResult;
import com.bingo.hotel.spa.intl.cli.seq.CheckPriceReq;
import com.bingo.hotel.spa.intl.cli.seq.PriceReq;
import com.bingo.hotel.spa.intl.core.api.aichotels.access.AvailabilityAccess;
import com.bingo.hotel.spa.intl.core.api.aichotels.adaptor.AichotelsHotelAdaptor;
import com.bingo.hotel.spa.intl.core.api.aichotels.adaptor.AichotelsProductAdaptor;
import com.bingo.hotel.spa.intl.core.api.aichotels.adaptor.AichotelsRoomAdaptor;
import com.bingo.hotel.spa.intl.core.api.huitravel.access.GetPriceAccess;
import com.bingo.hotel.spa.intl.core.api.huitravel.access.HotelDetailAccess;
import com.bingo.hotel.spa.intl.core.api.huitravel.adaptor.HuiTravelHotelAdaptor;
import com.bingo.hotel.spa.intl.core.api.huitravel.adaptor.HuiTravelProductAdaptor;
import com.bingo.hotel.spa.intl.core.api.huitravel.adaptor.HuiTravelRoomAdaptor;
import com.bingo.hotel.spa.intl.core.api.huitravel.bean.hotel.detail.HotelDetailRequest;
import com.bingo.hotel.spa.intl.core.api.huitravel.bean.hotel.detail.HotelDetailResponse;
import com.bingo.hotel.spa.intl.core.api.huitravel.bean.hotel.detail.HotelDetailResult;
import com.bingo.hotel.spa.intl.core.api.huitravel.bean.hotel.list.HotelListRequest;
import com.bingo.hotel.spa.intl.core.api.common.asynchttp.ResponseResult;
import com.bingo.hotel.spa.intl.core.api.huitravel.access.HotelIdListAccess;

import com.bingo.hotel.spa.intl.core.api.huitravel.bean.hotel.list.HotelListResponse;
import com.bingo.hotel.spa.intl.core.api.huitravel.bean.hotel.list.Hotels;
import com.bingo.hotel.spa.intl.core.api.huitravel.bean.price.availability.AvailabilityRequest;
import com.bingo.hotel.spa.intl.core.api.huitravel.bean.price.availability.AvailabilityResponse;
import com.bingo.hotel.spa.intl.core.api.huitravel.bean.price.availability.AvailabilityResult;
import com.bingo.hotel.spa.intl.core.api.huitravel.bean.price.check.CheckRequest;
import com.bingo.hotel.spa.intl.core.api.huitravel.bean.price.check.CheckResponse;
import com.bingo.hotel.spa.intl.core.api.huitravel.service.HuiTravelService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class HuiTravelServiceImpl implements HuiTravelService {
    @Value("${huitravel.url.hotellist}")
    String host;

    @Value("${huitravel.appkey}")
    String appKey;

    @Value("${huitravel.secretKey}")
    String secretKey;
    @Autowired
    private HotelInfoIntlClient hotelInfoIntlClient;

    @Override
    public void getHotelCodeListByCity(String countryCode, String cityCode) {
        HotelListRequest data = HotelListRequest.builder()
                .countrycode(countryCode)
                .citycode(cityCode)
                .build();
        ResponseResult<HotelListResponse> hotelListResponse = new HotelIdListAccess("http://cithotelsellapi.test.huizhi-intl.com/hotelsell/cithotelsell/static/hotellist", "boqiaotest", "d1e205262c06132733faa748dc3e29ab").access(data);
        for (Hotels hotelId : hotelListResponse.getData().getResult().getHotels()) {
            HotelDetailRequest request = HotelDetailRequest.builder().hids(hotelId.getHid() + "").build();
            ResponseResult<HotelDetailResponse> hotelDetailResponse = new HotelDetailAccess("http://cithotelsellapi.test.huizhi-intl.com/hotelsell/cithotelsell/static/hoteldetails", "boqiaotest", "d1e205262c06132733faa748dc3e29ab").access(request);
            System.out.println(hotelDetailResponse.getData().getResult().getHoteldetail().get(0).getEn_name());
            AvailabilityRequest availabilityRequest = AvailabilityRequest.builder().
                    hid(hotelId.getHid())
                    .checkin("2024-08-15")
                    .checkout("2024-8-16")
                    .roomnum(1)
                    .adultnum(2)
                    .nationality("CN")
                    .build();
            ResponseResult<AvailabilityResponse> availabilityResponse = new GetPriceAccess("http://cithotelsellapi.test.huizhi-intl.com/hotelsell/cithotelsell/static/hoteldetails", "boqiaotest", "d1e205262c06132733faa748dc3e29ab").access(availabilityRequest);
            InfoResult infoResult = hotelInfoIntlClient.saveHotelInfo(HuiTravelHotelAdaptor.transform(hotelDetailResponse.getData().getResult().getHoteldetail().get(0)));
            InfoResult roomResult = hotelInfoIntlClient.saveRoomInfo(HuiTravelRoomAdaptor.transform(hotelDetailResponse.getData().getResult().getHoteldetail().get(0).getRooms(), hotelId + ""));
            InfoResult productResult = hotelInfoIntlClient.saveProductInfo(HuiTravelProductAdaptor.transform(availabilityResponse.getData(), hotelId + ""));
        }
    }

    @Override
    public AvailabilityResponse getPrice(PriceReq priceReq, String sHotelId) {
        AvailabilityRequest availabilityRequest = AvailabilityRequest.builder().
                hid(Integer.parseInt(sHotelId))
                .checkin(priceReq.getCheckIn())
                .checkout(priceReq.getCheckout())
                .roomnum(priceReq.getRoomNum())
                .adultnum(priceReq.getAdultNum())
                .nationality("CN")
                .build();
        ResponseResult<AvailabilityResponse> availabilityResponse = new GetPriceAccess("http://cithotelsellapi.test.huizhi-intl.com/hotelsell/cithotelsell/static/hoteldetails", "boqiaotest", "d1e205262c06132733faa748dc3e29ab").access(availabilityRequest);
        return availabilityResponse.getData();
    }

    @Override
    public CheckResponse checkPrice(CheckPriceReq priceReq) {
        AvailabilityRequest availabilityRequest = AvailabilityRequest.builder().
                hid(Integer.parseInt(priceReq.getSHotelId()))
                .checkin(priceReq.getCheckIn())
                .checkout(priceReq.getCheckOut())
                .roomnum(priceReq.getRoomNum())
                .adultnum(priceReq.getAdultCount())
                .nationality("CN")
                .build();
        ResponseResult<AvailabilityResponse> availabilityResponse = new GetPriceAccess("http://cithotelsellapi.test.huizhi-intl.com/hotelsell/cithotelsell/static/hoteldetails", "boqiaotest", "d1e205262c06132733faa748dc3e29ab").access(availabilityRequest);

        availabilityResponse.getData().getResult().getPrices().get(0).getNightlyrate();
        CheckRequest checkRequest = CheckRequest.builder().
                checkin(priceReq.getCheckIn())
                .checkout(priceReq.getCheckOut())
                .adultnum(priceReq.getAdultCount())
                .hid(Integer.parseInt(priceReq.getSHotelId()))
                .rid(availabilityResponse.getData().getResult().getPrices().get(0).getRid())
                .rpid(availabilityResponse.getData().getResult().getPrices().get(0).getRpid())
                .build();
        return null;
    }
}
