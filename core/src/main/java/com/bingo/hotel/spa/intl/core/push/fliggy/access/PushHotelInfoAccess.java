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
import com.taobao.api.response.XhotelUpdateResponse;

public class PushHotelInfoAccess extends BaseHttpAccess<AvailabilityRequest, AvailabilityResponse> {
    public PushHotelInfoAccess(SupplierSourceEnum supplier, SupplierDataTypeEnum dataType, MonitorNameEnum monitorKey) {
        super(supplier, dataType, monitorKey);
    }

    public PushHotelInfoAccess(SupplierSourceEnum supplier, SupplierDataTypeEnum dataType, MonitorNameEnum monitorKey, int retries) {
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

        // required只推英文名
//        req.setName(zyxHotel.getNameEn());
//        req.setOuterId(zyxHotel.getCode());
//        req.setCity(getAliCityCode(cityCodeMap, zyxHotel.getCityCode()));
//
//        // optional
//        req.setDomestic(1L); // 海外酒店
//        req.setProvince(0L);
//        req.setCountry(AliCodeMapper.getAliCountryCode(zyxHotel.getCountryCode()));
//        req.setNameE(zyxHotel.getNameEn());
//        req.setAddress(zyxHotel.getAddrEn());
//        req.setLongitude(truncate(zyxHotel.getLongitude(), 10));
//        req.setLatitude(truncate(zyxHotel.getLatitude(), 10));
//        req.setPositionType("G"); // G: Google, B:Baidu, A:Amap
//        if (!Strings.isNullOrEmpty(zyxHotel.getTelephone())) {
//            req.setTel(zyxHotel.getTelephone());
//        } else {
//            req.setTel("0");
//        }
//
//        req.setStar(zyxHotel.getStar());
//        req.setVendor("zyxkr");
//        req.setSupplier("zyxkr");
        //req.setUsedName();
        //req.setOpeningTime();
        //req.setDecorateTime();
        //req.setFloors();
        //req.setRooms();
        //req.setDescription();
        //req.setHotelFacilities();
        //req.setRoomFacilities();
        //req.setService();
        //req.setPics();
        //req.setBrand();
        //req.setPostalCode();

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
