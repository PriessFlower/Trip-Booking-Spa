package com.trip.booking.spa.gateway.adapter.outbound.supplier.expedia.shared.model.response;

import com.trip.booking.spa.platform.http.asynchttp.BaseResponse;

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
    private List<Property_id> hotelIds;

    public List<Property_id> getHotelIds() {
        return hotelIds;
    }

    public void setHotelIds(List<Property_id> hotelIds) {
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

    public static class Property_id {
        String property_id;

        public String getProperty_id() {
            return property_id;
        }

        public void setProperty_id(String property_id) {
            this.property_id = property_id;
        }
    }
}
