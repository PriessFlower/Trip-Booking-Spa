package com.trip.booking.spa.gateway.adapter.inbound.rest.bean;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.List;

@Getter
@Setter
public class PageBean<T> implements Serializable {
    // 当前页数
    private int currPage;
    // 总记录数
    private int totalCount;
    // 总页数
    private int totalPage;
    // 每页记录数
    private int pageSize;
    // 每页的数据的集合
    private List<T> list;

}
