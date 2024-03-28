package com.bingo.hotel.spa.intl.core.api.common.enums;

public enum OrderStatusEnum {
    CREATE(10, "创建订单"),
    BOOKING(20, "预定中"),
    BOOK_SUCCESS(21, "预定成功"),
    BOOK_FAIL(22, "预定失败"),
    CANCELING(30, "取消中"),
    CANCEL_SUCCESS(31, "取消成功"),
    CANCEL_FAIL(32, "取消失败");

    private final int code;
    private final String desc;

    OrderStatusEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public int getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }
}
