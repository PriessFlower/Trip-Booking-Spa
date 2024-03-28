/*
 * Copyright (c) 2017 Qunar.com. All Rights Reserved.
 */
package com.bingo.hotel.spa.intl.core.api.common.enums;

/**
 * 业务名称_功能名称_类型 监控名称
 *
 * @author zhe.hao
 */
public enum MonitorNameEnum {
    // 一级分类，二级分类。。。功能
    SPA_SUPPLIER_API_COUNTRY("供应商国家接口"),
    SPA_SUPPLIER_API_CITY("供应商city接口"),
    SPA_SUPPLIER_API_HOTEL_LIST("供应商酒店列表接口"),
    SPA_SUPPLIER_API_HOTEL_INFO("供应商酒店详情接口"),
    SPA_SUPPLIER_API_HOTEL_INCR("供应商酒店增量接口"),
    SPA_SUPPLIER_API_ROOM_INFO("供应商房型信息接口"),
    SPA_SUPPLIER_API_PRODUCT_PRICE("代理商产品报价接口"),
    SPA_SUPPLIER_API_PRODUCT_PRICES("批量代理商产品报价接口"),
    SPA_SUPPLIER_API_ORDER_PRICE("代理商订单报价接口"),
    SPA_SUPPLIER_API_CREATE_ORDER("供应商创建订单接口"),
    SPA_SUPPLIER_API_CANCEL_ORDER("供应商取消订单接口"),
    SPA_SUPPLIER_API_QUERY_ORDER("供应商查询订单接口"),
    SPA_SUPPLIER_PUSH_RATE_PLAN("推送产品给分销商接口"),
    SPA_SUPPLIER_PUSH_RATE_PLAN_NOTIFY("推送产品通知给分销商接口"),
    SPA_SUPPLIER_PUSH_RATE_PLAN_STATUS("推送产品状态给分销商接口"),
    SPA_SUPPLIER_PUSH_RATES("推送价格库存给分销商接口"),
    SPA_SUPPLIER_PUSH_HOTEL("推送酒店给分销商接口"),
    SPA_SUPPLIER_PUSH_ROOM("推送房型给分销商接口"),
    ;

    MonitorNameEnum(String desc) {
        this.desc = desc;
    }

    // 监控描述
    private String desc;

    public String getDesc() {
        return desc;
    }

}
