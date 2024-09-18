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
    private List<String> hotelIds;

    public List<String> getHotelIds() {
        return hotelIds;
    }

    public void setHotelIds(List<String> hotelIds) {
        this.hotelIds = hotelIds;
    }

    @Override
    public boolean isSucc() {
        return false;
    }

    @Override
    public boolean isEmptyResult() {
        return false;
    }
}
