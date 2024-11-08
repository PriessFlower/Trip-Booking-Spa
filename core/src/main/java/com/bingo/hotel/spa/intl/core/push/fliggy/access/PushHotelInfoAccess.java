package com.bingo.hotel.spa.intl.core.push.fliggy.access;

import com.bingo.hotel.spa.intl.core.api.aichotels.bean.price.availability.AvailabilityRequest;
import com.bingo.hotel.spa.intl.core.api.common.access.BaseHttpAccess;
import com.bingo.hotel.spa.intl.core.api.common.asynchttp.IParser;
import com.bingo.hotel.spa.intl.core.api.common.asynchttp.ResponseResult;
import com.bingo.hotel.spa.intl.core.api.common.enums.MonitorNameEnum;
import com.bingo.hotel.spa.intl.core.api.common.enums.SupplierDataTypeEnum;
import com.bingo.hotel.spa.intl.core.api.common.enums.SupplierSourceEnum;
import com.bingo.hotel.spa.intl.core.push.fliggy.bean.FliggyPushResponse;
import com.bingo.hotel.spa.intl.core.push.fliggy.bean.hotel.PushHotelVo;
import com.taobao.api.ApiException;
import com.taobao.api.DefaultTaobaoClient;
import com.taobao.api.TaobaoClient;
import com.taobao.api.request.XhotelCityCoordinatesBatchDownloadRequest;
import com.taobao.api.request.XhotelUpdateRequest;
import com.taobao.api.response.XhotelCityCoordinatesBatchDownloadResponse;
import com.taobao.api.response.XhotelUpdateResponse;

public class PushHotelInfoAccess extends BaseHttpAccess<PushHotelVo, FliggyPushResponse> {
    public PushHotelInfoAccess(SupplierSourceEnum supplier, SupplierDataTypeEnum dataType, MonitorNameEnum monitorKey) {
        super(supplier, dataType, monitorKey);
    }

    public PushHotelInfoAccess(SupplierSourceEnum supplier, SupplierDataTypeEnum dataType, MonitorNameEnum monitorKey, int retries) {
        super(supplier, dataType, monitorKey, retries);
    }

    @Override
    protected ResponseResult<FliggyPushResponse> request(String url, PushHotelVo request, IParser<FliggyPushResponse> parser) throws Exception {
        TaobaoClient tc = new DefaultTaobaoClient("", "", "");
        XhotelUpdateRequest req = convertHotelInfoRequest(request);
        XhotelUpdateResponse resp = tc.execute(req, "");
        return null;
    }

    private XhotelUpdateRequest convertHotelInfoRequest(PushHotelVo request) {
        XhotelUpdateRequest req = new XhotelUpdateRequest();

        req.setName(request.getHotelName());
        req.setOuterId(request.getHotelId());
//        req.setCity(getAliCityCode(cityCodeMap, zyxHotel.getCityCode()));

        // optional
        req.setDomestic(1L); // 海外酒店
        req.setProvince(0L);
//        req.setCountry(AliCodeMapper.getAliCountryCode(zyxHotel.getCountryCode()));
        req.setNameE(request.getHotelName());
        req.setAddress(request.getAddress());
        req.setLongitude(request.getLongitude());
        req.setLatitude(request.getLatitude());
        req.setPositionType("G"); // G: Google, B:Baidu, A:Amap
        req.setTel(request.getTelephone());
//        req.setStar(request.get);
        req.setVendor("intl_Bingotravel");
        req.setSupplier("intl_Bingotravel");

        return req;
    }

    @Override
    protected void beforeAccess(PushHotelVo request) {

    }

    @Override
    protected String buildRequestUrl() {
        return null;
    }

    @Override
    protected FliggyPushResponse parseResponse(String data) {
        return null;
    }
}
