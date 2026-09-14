/*
 * Copyright (c) 2017 Qunar.com. All Rights Reserved.
 */
package com.trip.booking.spa.platform.observability;

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
    SPA_SUPPLIER_BED_TYPE("查询供应商床型类型"),
    SPA_SUPPLIER_FILE_DOWNLOAD("供应商大文件下载"),
    /**
     * 供应商鉴权令牌接口（如 clwy 的 Wid+ApiKey 换 JWT）。
     * 它也是对供应商的 HTTP 调用，同样要吃通道层那道唯一闸门（§3.3）——绕过去就等于
     * 一个不限流、不埋点、失败不可见的调用路径，而令牌接口恰恰是"频繁重复请求"被官方点名的那个。
     */
    SPA_SUPPLIER_API_AUTH_TOKEN("供应商鉴权令牌接口"),
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
