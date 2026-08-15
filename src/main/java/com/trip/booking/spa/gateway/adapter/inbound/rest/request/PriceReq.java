package com.trip.booking.spa.gateway.adapter.inbound.rest.request;

import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
public class PriceReq {

    @NonNull
    private String checkIn;//入住日期
    @NonNull
    private String checkout;//离店日期
    @NonNull
    private Integer roomNum; //房间数量
    @NonNull
    private Integer adultNum; //成人数
    @NonNull
    private Integer childNum; //儿童数
    @NonNull
    private List<Integer> childAges; //儿童年龄
    @NonNull
    private Integer guestType;//宾客类型

    private String salesType;//售卖类型 expedia专用

    private String currency;//币种

    private String language;//语言

    private List<Supplier> suppliers;

    private String bedId;//床型id

    private String priceFlag;//hotel_package-打包价 hotel_only-零售价

    private List<String> occupancies;
}
