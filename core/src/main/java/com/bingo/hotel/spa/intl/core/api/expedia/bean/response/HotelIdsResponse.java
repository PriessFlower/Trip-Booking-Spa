package com.bingo.hotel.spa.intl.core.api.expedia.bean.response;

import com.bingo.hotel.spa.intl.core.api.common.asynchttp.BaseResponse;

import java.util.List;
import java.util.Map;

/**
 * expedia酒店基础静态信息.
 *
 * @author : hanJH
 * @version : 1.0 2024/09/12
 * @since : 1.0
 **/
public class HotelIdsResponse implements BaseResponse {
    private List<Map<String, String>> hotelIds;

    public List<Map<String, String>> getHotelIds() {
        return hotelIds;
    }

    public void setHotelIds(List<Map<String, String>> hotelIds) {
        this.hotelIds = hotelIds;
    }

    @Override
    public boolean isSucc() {
        return true;
    }

    @Override
    public boolean isEmptyResult() {
        return false;
    }
}
