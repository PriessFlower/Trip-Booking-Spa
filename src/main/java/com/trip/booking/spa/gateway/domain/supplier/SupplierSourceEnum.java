package com.trip.booking.spa.gateway.domain.supplier;

import com.google.common.collect.Maps;

import java.util.Map;

public enum SupplierSourceEnum {
    TRAVELCONNECT(10001, "travelConnect"),
    AICHOTELS(10002, "aicHotels"),
    DIDATRAVEL(10003, "didatravel"),
    HUITRAVEL(10004, "huitravel"),
    EXPEDIA(10005, "expedia"),
    FASTPAYHOTELS(10006, "FastpayHotels"),
    RATEHAWK(10007, "ratehawk"),
    MEITUAN(10009, "meituan"),
    ELONG(10010, "elong"),
    ;

    SupplierSourceEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    private int code;

    public int getCode() {
        return code;
    }

    private String desc;

    public String getDesc() {
        return desc;
    }

    private static final Map<String, SupplierSourceEnum> enumMap = Maps.newHashMap();

    static {
        for (SupplierSourceEnum e : values()) {
            enumMap.put(e.name(), e);
        }
    }

    public static SupplierSourceEnum valueOfName(String name) {
        return enumMap.get(name);
    }

    public static SupplierSourceEnum getEnum(int code) {
        for (SupplierSourceEnum sourceEnum : SupplierSourceEnum.values()) {
            if (sourceEnum.code == code) {
                return sourceEnum;
            }
        }
        return null;
    }
}
