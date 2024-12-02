package com.bingo.hotel.spa.intl.core.push.fliggy.access;

import com.bingo.hotel.base.intl.cli.response.HotelBaseResponse;
import com.bingo.hotel.spa.intl.core.api.aichotels.bean.price.availability.AvailabilityRequest;
import com.bingo.hotel.spa.intl.core.api.common.access.BaseHttpAccess;
import com.bingo.hotel.spa.intl.core.api.common.asynchttp.IParser;
import com.bingo.hotel.spa.intl.core.api.common.asynchttp.ResponseResult;
import com.bingo.hotel.spa.intl.core.api.common.enums.MonitorNameEnum;
import com.bingo.hotel.spa.intl.core.api.common.enums.SupplierDataTypeEnum;
import com.bingo.hotel.spa.intl.core.api.common.enums.SupplierSourceEnum;
import com.bingo.hotel.spa.intl.core.push.fliggy.bean.FliggyPushResponse;
import com.bingo.hotel.spa.intl.core.push.fliggy.bean.hotel.PushHotelVo;
import com.bingo.hotel.spa.intl.core.util.JsonUtils;
import com.google.common.collect.BiMap;
import com.google.common.collect.ImmutableBiMap;
import com.taobao.api.ApiException;
import com.taobao.api.DefaultTaobaoClient;
import com.taobao.api.TaobaoClient;
import com.taobao.api.request.XhotelCityCoordinatesBatchDownloadRequest;
import com.taobao.api.request.XhotelUpdateRequest;
import com.taobao.api.response.XhotelCityCoordinatesBatchDownloadResponse;
import com.taobao.api.response.XhotelUpdateResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.multimap.ArrayListValuedHashMap;

@Slf4j
public class PushHotelInfoAccess extends BaseHttpAccess<HotelBaseResponse, FliggyPushResponse> {
    private String url;

    private String sessionKey;

    private String appKey;

    private String appSecret;
    private static MultiValuedMap<String, Long> cityCodeMap = new ArrayListValuedHashMap<>() {{
        put("Chek Lap Kok", 810100L);
        put("Cheung Chau", 810100L);
        put("Discovery Bay", 810100L);
        put("Hong Kong", 810100L);
        put("Kowloon", 810100L);
        put("Kwai Chung", 810100L);
        put("Lamma Island", 810100L);
        put("Lantau", 810100L);
        put("Mui Wo", 810100L);
        put("Ngong Ping", 810100L);
        put("Sai Kung", 810100L);
        put("Sea Ranch", 810100L);
        put("Sha Tin", 810100L);
        put("Tai O Village", 810100L);
        put("Tai Po", 810100L);
        put("Tsing Yi", 810100L);
        put("Tsuen Wan", 810100L);
        put("Tuen Mun", 810100L);
        put("Tung Chung", 810100L);
        put("Yuen Long", 810100L);
        put("Coloane", 820100L);
        put("Cotai", 820100L);
        put("Taipa", 820100L);
        put("Macau", 820100L);
        put("Bangkok", 904976L);// 曼谷
        put("Hagersten", 956110L);
        put("Vantaa", 901908L);
    }};

    public PushHotelInfoAccess(String url, String sessionKey, String appKey, String appSecret) {
        super(SupplierSourceEnum.FLIGGY, SupplierDataTypeEnum.STATIC_DATA,
                MonitorNameEnum.SPA_SUPPLIER_PUSH_HOTEL);
        this.url = url;
        this.sessionKey = sessionKey;
        this.appKey = appKey;
        this.appSecret = appSecret;
    }


    @Override
    protected ResponseResult<FliggyPushResponse> request(String url, HotelBaseResponse request, IParser<FliggyPushResponse> parser) throws Exception {
        TaobaoClient tc = new DefaultTaobaoClient(url, appKey, appSecret);
        XhotelUpdateRequest req = convertHotelInfoRequest(request);
        XhotelUpdateResponse resp = tc.execute(req, sessionKey);
        log.info(JsonUtils.writeObject2Json(resp));
        return (ResponseResult<FliggyPushResponse>) new ResponseResult(resp.getBody(), FliggyPushResponse.builder().success(true).build());
    }

    private XhotelUpdateRequest convertHotelInfoRequest(HotelBaseResponse request) {
        XhotelUpdateRequest req = new XhotelUpdateRequest();

        req.setName(request.getHotelNameCN());
        req.setOuterId(request.getHotelId());
        req.setCity(cityCodeMap.get(request.getCityName()).stream().findFirst().orElse(null));
//        req.setCity(901892L);
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
    protected void beforeAccess(HotelBaseResponse request) {

    }

    @Override
    protected String buildRequestUrl() {
        return url;
    }

    @Override
    protected FliggyPushResponse parseResponse(String data) {
        return null;
    }
}
