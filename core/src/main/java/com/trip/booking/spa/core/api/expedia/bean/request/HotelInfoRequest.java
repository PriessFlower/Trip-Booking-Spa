package com.trip.booking.spa.core.api.expedia.bean.request;

import lombok.Builder;

/**
 * 查询酒店相关信息请求.
 *
 * @author : hanJH
 * @version : 1.0 2024/09/03
 * @since : 1.0
 **/

@Builder
public class HotelInfoRequest {

    private String supply_source;

    private String property_id;

    public String getSupply_source() {
        return supply_source;
    }

    public void setSupply_source(String supply_source) {
        this.supply_source = supply_source;
    }

    public String getProperty_id() {
        return property_id;
    }

    public void setProperty_id(String property_id) {
        this.property_id = property_id;
    }
}
