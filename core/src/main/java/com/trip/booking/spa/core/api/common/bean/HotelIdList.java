package com.trip.booking.spa.core.api.common.bean;

import lombok.Getter;
import lombok.Setter;

import java.util.Date;

@Getter
@Setter
public class HotelIdList {

    private Long id;
    //代理商ID
    private Integer supplierId;
    //代理商酒店ID
    private String sHotelId;
    //在线情况
    private Boolean online;
    //最后更新时间
    private Date lastTime;
    //创建时间
    private Date createTime;
    //修改时间
    private Date updateTime;
    //操作人
    private String operator;

}
