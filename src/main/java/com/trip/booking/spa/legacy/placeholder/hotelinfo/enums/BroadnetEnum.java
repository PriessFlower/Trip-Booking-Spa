/*
 * Decompiled with CFR 0.152.
 */
package com.trip.booking.spa.legacy.placeholder.hotelinfo.enums;

public enum BroadnetEnum {
    NOT(0, "\u65e0"),
    FREE_BROADBAND(1, "\u514d\u8d39\u5bbd\u5e26"),
    PAID_BROADBAND(2, "\u6536\u8d39\u5bbd\u5e26"),
    FREE_WIFI(3, "\u514d\u8d39WIFI"),
    PAID_WIFI(4, "\u6536\u8d39WIFI");

    private Integer value;
    private String desc;

    private BroadnetEnum(Integer value, String desc) {
        this.value = value;
        this.desc = desc;
    }

    public Integer getValue() {
        return this.value;
    }

    public void setValue(String Integer2) {
        this.value = this.value;
    }

    public String getDesc() {
        return this.desc;
    }

    public void setDesc(String desc) {
        this.desc = desc;
    }
}
