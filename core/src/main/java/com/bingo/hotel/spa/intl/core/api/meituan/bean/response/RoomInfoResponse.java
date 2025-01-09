package com.bingo.hotel.spa.intl.core.api.meituan.bean.response;

import com.bingo.hotel.spa.intl.core.api.common.asynchttp.BaseResponse;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

/**
 * 酒店房型反参结构.
 *
 * @author : hanJH
 * @version : 1.0 2025/01/07
 * @since : 1.0
 **/

@Getter
@Setter
public class RoomInfoResponse implements BaseResponse {

    private Result result;
    private Integer code;
    private Integer partnerId;
    private String message;

    @Override
    public boolean isSucc() {
        return code == 0;
    }

    @Override
    public boolean isEmptyResult() {
        return result == null || result.realRoomInfos == null;
    }

    @Getter
    @Setter
    public static class Result {
        private Map<Integer, List<RealRoomInfos>> realRoomInfos;
    }

    @Getter
    @Setter
    public static class RealRoomInfos {
        RealRoomBaseInfo realRoomBaseInfo;
        private List<List<BedInfoList>> bedInfoList;
    }

    @Getter
    @Setter
    public static class RealRoomBaseInfo {
        private Long realRoomId;
        private String roomNameEn;
        private List<String> images;
        private Map<Integer, String> roomFacilities;
        private Integer extraBed;
        private Integer window;
        private Integer internetWay;
        private String floor;
        private String roomName;
        private String useableArea;
    }

    @Getter
    @Setter
    public static class BedInfoList {
        private Long realRoomId;
        private String bedType;
        private String bedDesc;
        private Integer bedCount;
    }
}
