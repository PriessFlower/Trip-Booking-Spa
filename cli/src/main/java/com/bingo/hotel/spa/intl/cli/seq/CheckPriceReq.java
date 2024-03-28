package com.bingo.hotel.spa.intl.cli.seq;

import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

import java.util.Date;

@Getter
@Setter
@Builder
public class CheckPriceReq {
    @NonNull
    private Integer supplierId;//供应商ID

    private String sHotelId;//供应商酒店ID

    private String sProductId;//供应商产品Id
    @NonNull
    private String checkIn;//入住日期
    @NonNull
    private String checkOut;//离店日期
    @NonNull
    private Integer roomNum;//房间数量
    @NonNull
    private Integer totalPrice;//总价
}
