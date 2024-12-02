package com.bingo.hotel.spa.intl.core.push.fliggy.service.impl;

import com.bingo.hotel.base.intl.cli.client.HotelBaseIntlClient;
import com.bingo.hotel.base.intl.cli.request.QueryHotelRequest;
import com.bingo.hotel.base.intl.cli.request.QueryRoomRequest;
import com.bingo.hotel.base.intl.cli.response.HotelBaseResponse;
import com.bingo.hotel.base.intl.cli.response.RoomBaseResponse;
import com.bingo.hotel.base.intl.cli.result.BaseResult;
import com.bingo.hotel.spa.intl.core.api.common.enums.SupplierSourceEnum;
import com.bingo.hotel.spa.intl.core.push.fliggy.access.PushHotelInfoAccess;
import com.bingo.hotel.spa.intl.core.push.fliggy.access.PushRoomInfoAccess;
import com.bingo.hotel.spa.intl.core.push.fliggy.service.FliggyPushService;
import com.bingo.hotel.spa.intl.core.push.service.UpHotelService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class FliggyPushServiceImpl implements FliggyPushService {
    @Value("${fliggy.distrib.url}")
    private String url;

    @Value("${fliggy.distrib.sessionKey}")
    private String sessionKey;

    @Value("${fliggy.distrib.appKey}")
    private String appKey;

    @Value("${fliggy.distrib.appSecret}")
    private String appSecret;
    @Autowired
    private HotelBaseIntlClient hotelBaseClient;
    @Autowired
    private UpHotelService upHotelService;

    @Override
    public void pushFliggyHotel(String hotelId) {
        List<String> hotelIds = upHotelService.getHotelListByDistributeId(SupplierSourceEnum.FLIGGY.getCode());
//        List<String> hotelIds = List.of(hotelId);
        List<List<String>> hotelIdList = com.google.common.collect.Lists.partition(hotelIds, 100);
        for (List<String> list : hotelIdList) {
            try {
                QueryHotelRequest hotelBaseListRequest = new QueryHotelRequest();
                hotelBaseListRequest.setHotelIds(list);
                BaseResult<List<HotelBaseResponse>> hotelListResult = hotelBaseClient.queryHotelBaseList(hotelBaseListRequest);
                if (hotelListResult.getData() == null || hotelListResult.getData().size() == 0) {
                    break;
                }
                sendFliggyHotelInfo(hotelListResult.getData());
            } catch (Exception e) {
                log.error("fliggy get push hotel info error", e);
            }
        }
    }

    private void sendFliggyHotelInfo(List<HotelBaseResponse> hotelBaseResponses) {
        for (HotelBaseResponse hotelBaseResponse : hotelBaseResponses) {
            new PushHotelInfoAccess(url, sessionKey, appKey, appSecret)
                    .access(hotelBaseResponse);
        }
    }

    @Override
    public void pushFliggyRoom(String hotelId) {
        List<String> hotelIds = upHotelService.getHotelListByDistributeId(SupplierSourceEnum.FLIGGY.getCode());
//        List<String> hotelIds = List.of(hotelId);
        List<List<String>> hotelIdList = com.google.common.collect.Lists.partition(hotelIds, 100);
        for (List<String> list : hotelIdList) {
            QueryRoomRequest queryRoomRequest = new QueryRoomRequest();
            queryRoomRequest.setHotelIds(list);
            BaseResult<List<RoomBaseResponse>> baseResult = hotelBaseClient.queryRoomBaseList(queryRoomRequest);
            for (RoomBaseResponse roomBaseResponse : baseResult.getData()) {
                new PushRoomInfoAccess(url, sessionKey, appKey, appSecret)
                        .access(roomBaseResponse);
            }
        }
    }
}
