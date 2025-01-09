package com.bingo.hotel.spa.intl.core.api.meituan.bean.request;

import lombok.Getter;
import lombok.Setter;

/**
 * 验价入参.
 *
 * @author : hanJH
 * @version : 1.0 2025/01/09
 * @since : 1.0
 **/

@Getter
@Setter
public class CheckReqBody {

    private String checkoutDate;
    private Integer numberOfChildren;
    private Integer roomNum;
    private Integer numberOfAdults;
    private Long goodsId;
    private String clientNationality;
    private String checkinDate;
    private Long hotelId;
    private String currencyCode;
    private String childrenAges;
}
