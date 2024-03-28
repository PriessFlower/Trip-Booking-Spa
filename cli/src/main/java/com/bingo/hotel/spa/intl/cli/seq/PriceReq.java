package com.bingo.hotel.spa.intl.cli.seq;

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

    private List<Supplier> suppliers;
}
