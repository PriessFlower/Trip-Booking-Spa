package com.trip.booking.spa.core.api.meituan.bean.request;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Builder
@Getter
public class ProductInfoReqBody {

    private String checkoutDate;
    private Integer numberOfChildren;
    private List<Long> hotelIds;
    private Integer numberOfAdults;
    private String clientNationality;
    private String checkinDate;
    private Integer salesChannel;
    private String currencyCode;
    private String childrenAges;

}
