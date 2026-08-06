package com.trip.booking.spa.core.api.meituan.bean.response;

import com.trip.booking.spa.core.api.common.asynchttp.BaseResponse;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class HotelIdsResponse implements BaseResponse {

    private int code;
    private String message;
    private HotelIdsResult result;

    @Override
    public boolean isSucc() {
        return code == 0;
    }

    @Override
    public boolean isEmptyResult() {
        return result == null;
    }
}
