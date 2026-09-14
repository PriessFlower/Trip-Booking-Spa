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
    SPA_SUPPLIER_API_PRODUCT_PRICES("批量代理商产品报价接口"),
    SPA_SUPPLIER_API_ORDER_PRICE("代理商订单报价接口"),
    SPA_SUPPLIER_API_CREATE_ORDER("供应商创建订单接口"),
    SPA_SUPPLIER_API_CANCEL_ORDER("供应商取消订单接口"),
    SPA_SUPPLIER_API_QUERY_ORDER("供应商查询订单接口"),
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
