package com.bingo.hotel.spa.intl.core.push.fliggy.bean.hotel;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;

@Getter
@Setter
@Builder
public class PushHotelVo{
    private String hotelId;
    private String supplierHotelId;
    private String hotelName;
    private String hotelNameCN;
    private String telephone;
    private String address;
    private String addressCN;
    private String postCode;
    private String cityId;
    private String cityName;
    private String cityNameCN;
    private String stateName;
    private String countryId;
    private String fax;
    private String longitude;
    private String latitude;
    private Boolean status;
    private Date createTime;
    private Date updateTime;
    private Boolean del;
    private String operator;
}
