package com.bingo.hotel.spa.intl.core.push.fliggy.access;

import com.bingo.hotel.spa.intl.core.api.aichotels.bean.price.availability.AvailabilityRequest;
import com.bingo.hotel.spa.intl.core.api.aichotels.bean.price.availability.AvailabilityResponse;
import com.bingo.hotel.spa.intl.core.api.common.access.BaseHttpAccess;
import com.bingo.hotel.spa.intl.core.api.common.asynchttp.IParser;
import com.bingo.hotel.spa.intl.core.api.common.asynchttp.ResponseResult;
import com.bingo.hotel.spa.intl.core.api.common.enums.MonitorNameEnum;
import com.bingo.hotel.spa.intl.core.api.common.enums.SupplierDataTypeEnum;
import com.bingo.hotel.spa.intl.core.api.common.enums.SupplierSourceEnum;
import com.taobao.api.DefaultTaobaoClient;
import com.taobao.api.TaobaoClient;
import com.taobao.api.request.XhotelUpdateRequest;

public class PushRoomInfoAccess extends BaseHttpAccess<AvailabilityRequest, AvailabilityResponse> {
    public PushRoomInfoAccess(SupplierSourceEnum supplier, SupplierDataTypeEnum dataType, MonitorNameEnum monitorKey) {
        super(supplier, dataType, monitorKey);
    }

    public PushRoomInfoAccess(SupplierSourceEnum supplier, SupplierDataTypeEnum dataType, MonitorNameEnum monitorKey, int retries) {
        super(supplier, dataType, monitorKey, retries);
    }

    @Override
    protected ResponseResult<AvailabilityResponse> request(String url, AvailabilityRequest request, IParser<AvailabilityResponse> parser) throws Exception {
        TaobaoClient tc = new DefaultTaobaoClient("", "", "");
//        XhotelUpdateRequest req = convertHotelInfoRequest(zyxHotel, cityCodeMap, sessionKey);
//        XhotelUpdateResponse resp = tc.execute(req, "");
        return null;
    }

    private XhotelUpdateRequest convertHotelInfoRequest() {
        XhotelUpdateRequest req = new XhotelUpdateRequest();

//        XhotelRoomtypeUpdateRequest req = new XhotelRoomtypeUpdateRequest();
//
//        // required
//        if (!Strings.isNullOrEmpty(zyxRoom.getNameCn())) {
//            req.setName(zyxRoom.getNameCn());
//        } else {
//            req.setName(zyxRoom.getNameEn());
//        }
//        req.setHotelCode(zyxRoom.getHotelCode());
//        req.setOuterId(zyxRoom.getRoomCode());
//        req.setBedType(toAliBedRelationType(bedRelations,zyxRoom.getBedTypeRelation()));
//
//        // optional
//        req.setMaxOccupancy(zyxRoom.getMaxCount().longValue()); // 0？
//        req.setArea(zyxRoom.getArea());
//        req.setFloor(zyxRoom.getFloorNumber());
//        req.setBedSize(zyxRoom.getBedSize());
//        req.setWindowType(WindowType.AVAILABLE.getCode()
//                .equals(zyxRoom.getRoomWindowType()) ? 1L : 0L);
//        if(sessionKey.equals(FzApp.ZYX2.sessionKey)){
//            req.setVendor("ZYXJapan");
//        }else if(sessionKey.equals(FzApp.ZYX3.sessionKey)){
//            req.setVendor("hongtaiapi");
//        }
//        else if(sessionKey.equals(FzApp.ZYX5.sessionKey)){
//            req.setVendor("itc");
//        }else if(sessionKey.equals(FzApp.ZYX4.sessionKey)){
//            req.setVendor("uotapi");
//        }else if(sessionKey.equals(FzApp.ZYX6.sessionKey)){
//            req.setVendor("cholidayzl");
//        }else if(sessionKey.equals(FzApp.ZYX7.sessionKey)){
//            req.setVendor("zyxkr");
//        }

        return req;
    }

    @Override
    protected void beforeAccess(AvailabilityRequest request) {

    }

    @Override
    protected String buildRequestUrl() {
        return null;
    }

    @Override
    protected AvailabilityResponse parseResponse(String data) {
        return null;
    }
}
