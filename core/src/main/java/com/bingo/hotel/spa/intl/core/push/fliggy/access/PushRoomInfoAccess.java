package com.bingo.hotel.spa.intl.core.push.fliggy.access;

import com.bingo.hotel.base.intl.cli.dto.BedInfoDTO;
import com.bingo.hotel.base.intl.cli.enums.BedTypeExpediaEnum;
import com.bingo.hotel.base.intl.cli.response.RoomBaseResponse;
import com.bingo.hotel.spa.intl.core.api.aichotels.bean.price.availability.AvailabilityRequest;
import com.bingo.hotel.spa.intl.core.api.aichotels.bean.price.availability.AvailabilityResponse;
import com.bingo.hotel.spa.intl.core.api.common.access.BaseHttpAccess;
import com.bingo.hotel.spa.intl.core.api.common.asynchttp.IParser;
import com.bingo.hotel.spa.intl.core.api.common.asynchttp.ResponseResult;
import com.bingo.hotel.spa.intl.core.api.common.enums.MonitorNameEnum;
import com.bingo.hotel.spa.intl.core.api.common.enums.SupplierDataTypeEnum;
import com.bingo.hotel.spa.intl.core.api.common.enums.SupplierSourceEnum;
import com.bingo.hotel.spa.intl.core.push.fliggy.bean.FliggyPushResponse;
import com.bingo.hotel.spa.intl.core.util.JsonUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.taobao.api.DefaultTaobaoClient;
import com.taobao.api.TaobaoClient;
import com.taobao.api.request.XhotelRoomtypeUpdateRequest;
import com.taobao.api.request.XhotelUpdateRequest;
import com.taobao.api.response.XhotelRoomtypeUpdateResponse;
import com.taobao.api.response.XhotelUpdateResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.utils.Lists;
import org.apache.commons.lang3.StringUtils;

import java.util.List;

@Slf4j
public class PushRoomInfoAccess extends BaseHttpAccess<RoomBaseResponse, FliggyPushResponse> {
    private String url;

    private String sessionKey;

    private String appKey;

    private String appSecret;

    public PushRoomInfoAccess(String url, String sessionKey, String appKey, String appSecret) {
        super(SupplierSourceEnum.FLIGGY, SupplierDataTypeEnum.STATIC_DATA,
                MonitorNameEnum.SPA_SUPPLIER_PUSH_ROOM);
        this.url = url;
        this.sessionKey = sessionKey;
        this.appKey = appKey;
        this.appSecret = appSecret;
    }

    @Override
    protected ResponseResult<FliggyPushResponse> request(String url, RoomBaseResponse request, IParser<FliggyPushResponse> parser) throws Exception {
        TaobaoClient tc = new DefaultTaobaoClient(url, appKey, appSecret);
        XhotelRoomtypeUpdateRequest req = convertHotelInfoRequest(request);
        XhotelRoomtypeUpdateResponse resp = tc.execute(req, sessionKey);
        log.info(JsonUtils.writeObject2Json(resp));
        return (ResponseResult<FliggyPushResponse>) new ResponseResult(resp.getBody(), FliggyPushResponse.builder().success(true).build());
    }

    private XhotelRoomtypeUpdateRequest convertHotelInfoRequest(RoomBaseResponse request) {

        XhotelRoomtypeUpdateRequest req = new XhotelRoomtypeUpdateRequest();
        // required
        if (StringUtils.isNotBlank(request.getRoomNameCN())) {
            req.setName(request.getRoomNameCN());
        } else {
            req.setName(request.getRoomName());
        }
        req.setNameE(request.getRoomName());
        req.setHotelCode(request.getHotelId());
        req.setOuterId(request.getRoomId());
        req.setBedType(toAliBedRelationType(request.getBedDesc()));

        // optional
        req.setMaxOccupancy(request.getCapacity().longValue()); // 0？
        req.setArea(request.getArea());
        req.setFloor(request.getFloor());
        req.setWindowType(request.getHasWindows().longValue());
        req.setVendor("intl_Bingotravel");
        return req;
    }

    private static String toAliBedRelationType(String roomDesc) {
        List<List<BedInfoDTO>> baseBed = JsonUtils.decodeJson(roomDesc, new TypeReference<List<List<BedInfoDTO>>>() {
        });
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < baseBed.size(); i++) {
            List<BedInfoDTO> bedInfoDTOS = baseBed.get(i);
            for (BedInfoDTO bedInfoDTO : bedInfoDTOS) {
                sb.append(handlerBedType(bedInfoDTO.getBedDesc(), bedInfoDTO.getBedNumber()));
                sb.append(",");
            }
            sb.deleteCharAt(sb.length() - 1);
            sb.append("/");
        }
        sb.deleteCharAt(sb.length() - 1);
        return sb.toString();
    }

    private static String handlerBedType(String bedCode, Integer bedNumber) {
        if ("Other".equals(bedCode)) {
            return "其他";
        } else if (BedTypeExpediaEnum.TWIN_BED.getValue().equals(bedCode) && bedNumber == 2) {
            return "双床";
        } else if (bedNumber > 1) {
            return toAliBedNumber(bedCode, bedNumber);
        } else {
            return toAliBedType(bedCode);
        }
    }

    private static String toAliBedNumber(String bedCode, Integer bedNumber) {
        return bedNumber + "张" + toAliBedType(bedCode);
    }

    private static String toAliBedType(String bedCode) {
        if (bedCode != null) {
            switch (bedCode) {
                case "TwinXLBed":
                case "TwinBed":
                    return "单人床";
                case "FullBed":
                case "QueenBed":
                case "KingBed":
                    return "大床";
                case "Futon":
                    return "榻榻米";
                case "BunkBed":
                    return "上下铺";
                case "DORM_BED":
                    return "通铺";
                case "WaterBed":
                    return "水床";
            }
        }
        return "其他";
    }

    @Override
    protected void beforeAccess(RoomBaseResponse request) {

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
