package com.trip.booking.spa.gateway.application.routing;

/**
 * 网关对外的五个业务能力。路由与能力发现共用这一份枚举——
 * bean 名后缀是历史约定（@Service("<供应商desc><后缀>")），收敛在此处，
 * 不再散落在各控制器方法里手拼字符串。
 */
public enum Capability {

    PRICING("ProductSyncService"),
    CHECK_PRICE("CheckPriceSyncService"),
    BOOKING("BookingSyncService"),
    ORDER_QUERY("OrderQuerySyncService"),
    CANCELLATION("CancelSyncService");

    private final String beanNameSuffix;

    Capability(String beanNameSuffix) {
        this.beanNameSuffix = beanNameSuffix;
    }

    public String beanNameSuffix() {
        return beanNameSuffix;
    }
}
